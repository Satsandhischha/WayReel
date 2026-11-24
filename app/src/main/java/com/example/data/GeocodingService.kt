package com.example.data

import com.example.model.GeocodeCandidate
import com.example.model.GeocodeStatus
import com.example.model.TripPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeocodingService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun geocodePlaces(places: List<TripPlace>): List<TripPlace> = withContext(Dispatchers.IO) {
        places.mapIndexed { index, place ->
            if (place.isResolved) {
                place
            } else {
                if (index > 0) delay(250L)
                resolveSinglePlace(place)
            }
        }
    }

    suspend fun resolveSinglePlace(place: TripPlace): TripPlace = withContext(Dispatchers.IO) {
        val queries = buildList {
            val parts = listOf(place.name, place.city, place.country).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) add(parts.joinToString(", "))
            if (place.city.isNotBlank() && place.name.isNotBlank()) {
                add("${place.name}, ${place.city}")
            }
        }.distinct()

        for (query in queries) {
            val photonMatch = searchPhoton(query, limit = 1).firstOrNull()
            if (photonMatch != null) {
                return@withContext place.copy(
                    lat = photonMatch.lat,
                    lng = photonMatch.lng,
                    city = place.city.ifBlank { photonMatch.city },
                    country = place.country.ifBlank { photonMatch.country },
                    displayAddress = photonMatch.displayName,
                    geocodeStatus = GeocodeStatus.RESOLVED.wireName
                )
            }

            val nominatimMatch = searchNominatim(query, limit = 1).firstOrNull()
            if (nominatimMatch != null) {
                return@withContext place.copy(
                    lat = nominatimMatch.lat,
                    lng = nominatimMatch.lng,
                    city = place.city.ifBlank { nominatimMatch.city },
                    country = place.country.ifBlank { nominatimMatch.country },
                    displayAddress = nominatimMatch.displayName,
                    geocodeStatus = GeocodeStatus.RESOLVED.wireName
                )
            }
        }

        place.copy(geocodeStatus = GeocodeStatus.NEEDS_FIX.wireName)
    }

    suspend fun searchCandidates(query: String, cityBias: String = "", countryBias: String = ""): List<GeocodeCandidate> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            val biasedQuery = listOf(clean, cityBias.trim(), countryBias.trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")

            val photon = searchPhoton(biasedQuery, limit = 5)
            if (photon.isNotEmpty()) return@withContext photon

            val nominatim = searchNominatim(biasedQuery, limit = 5)
            if (nominatim.isNotEmpty()) return@withContext nominatim

            if (biasedQuery != clean) {
                return@withContext searchPhoton(clean, limit = 5).ifEmpty {
                    searchNominatim(clean, limit = 5)
                }
            }
            emptyList()
        }

    private fun searchPhoton(query: String, limit: Int): List<GeocodeCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://photon.komoot.io/api/?q=$encoded&limit=$limit&lang=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WayReelAndroid/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val features = root.optJSONArray("features") ?: return emptyList()
                buildList {
                    for (i in 0 until features.length()) {
                        val feat = features.optJSONObject(i) ?: continue
                        val geom = feat.optJSONObject("geometry") ?: continue
                        val coords = geom.optJSONArray("coordinates") ?: continue
                        if (coords.length() < 2) continue
                        val lng = coords.optDouble(0, Double.NaN)
                        val lat = coords.optDouble(1, Double.NaN)
                        if (lat.isNaN() || lng.isNaN()) continue
                        val props = feat.optJSONObject("properties") ?: JSONObject()
                        val name = props.optString("name", "")
                        val street = props.optString("street", "")
                        val city = props.optString("city", "")
                            .ifBlank { props.optString("town", "") }
                            .ifBlank { props.optString("state", "") }
                        val country = props.optString("country", "")
                        val label = listOf(name, street, city, country)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { query }
                        add(GeocodeCandidate(label, lat, lng, city, country))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchNominatim(query: String, limit: Int): List<GeocodeCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&q=$encoded&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WayReelAndroid/1.0 (travel-itinerary-planner)")
                .header("Accept-Language", "en")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val lat = obj.optString("lat", "").toDoubleOrNull() ?: continue
                        val lng = obj.optString("lon", "").toDoubleOrNull() ?: continue
                        val displayName = obj.optString("display_name", query)
                        val addr = obj.optJSONObject("address")
                        val city = addr?.optString("city", "")
                            ?.ifBlank { addr.optString("town", "") }
                            ?.ifBlank { addr.optString("village", "") }
                            .orEmpty()
                        val country = addr?.optString("country", "").orEmpty()
                        add(GeocodeCandidate(displayName, lat, lng, city, country))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
