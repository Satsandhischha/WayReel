package com.example.data

import android.util.Base64
import com.example.model.ScheduledDay
import com.example.model.Trip
import com.example.model.TripPlace
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class SharedTripSnapshot(
    val title: String,
    val daysCount: Int,
    val startTimeMinutes: Int,
    val places: List<TripPlace>
)

object TripShareCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeShareUrl(trip: Trip): String {
        val snapshot = SharedTripSnapshot(
            title = trip.title,
            daysCount = trip.daysCount,
            startTimeMinutes = trip.startTimeMinutes,
            places = trip.places
        )
        val rawBytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(rawBytes) }
            baos.toByteArray()
        }
        val token = Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "wayreel://trip?d=$token"
    }

    fun decodeShareUrl(rawLinkOrToken: String): SharedTripSnapshot? {
        return try {
            val trimmed = rawLinkOrToken.trim()
            val token = when {
                trimmed.contains("d=") -> trimmed.substringAfter("d=").substringBefore("&")
                else -> trimmed
            }
            val compressed = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val jsonString = GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                gzip.readBytes().toString(Charsets.UTF_8)
            }
            json.decodeFromString<SharedTripSnapshot>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    fun buildShareText(trip: Trip, schedule: List<ScheduledDay>): String {
        val sb = StringBuilder()
        sb.appendLine(trip.title)
        sb.appendLine("${trip.daysCount}-day WayReel itinerary · ${trip.places.count { it.isResolved }} mapped stops")
        sb.appendLine()
        for (day in schedule) {
            if (day.stops.isEmpty()) continue
            sb.appendLine("DAY ${day.dayIndex + 1}")
            for (stop in day.stops) {
                val time = ItineraryPlanner.formatMinutes(stop.arrivalMinutes)
                val leg = stop.travelFromPrevMinutes?.let { mins ->
                    val mode = if (stop.isWalkingLeg) "walk" else "drive"
                    "  (${mins}m $mode)"
                }.orEmpty()
                if (leg.isNotEmpty()) sb.appendLine(leg)
                sb.appendLine("${stop.stopNumber}. $time — ${stop.place.name} (${stop.place.categoryEnum.label})")
                if (stop.place.whyMentioned.isNotBlank()) {
                    sb.appendLine("   Why: ${stop.place.whyMentioned}")
                }
                if (stop.place.lat != null && stop.place.lng != null) {
                    val encoded = URLEncoder.encode(stop.place.name, "UTF-8")
                    sb.appendLine("   Map: https://www.google.com/maps/search/?api=1&query=${stop.place.lat},${stop.place.lng}($encoded)")
                }
            }
            sb.appendLine()
        }
        sb.appendLine("Open read-only trip in WayReel:")
        sb.append(encodeShareUrl(trip))
        return sb.toString()
    }
}
