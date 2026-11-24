package com.example.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.example.model.GeocodeStatus
import com.example.model.PlaceCategory
import com.example.model.PlaceConfidence
import com.example.model.TripPlace
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

data class ExtractionOutput(
    val suggestedTripTitle: String,
    val transcriptSummary: String,
    val places: List<TripPlace>
)

class PlaceExtractorAiService {

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "suggested_trip_title" to Schema.string(
                description = "A short 2-5 word trip title based on the city/region, e.g., 'Tokyo Coffee & Views'."
            ),
            "transcript_summary" to Schema.string(
                description = "Concise summary or clean transcript of spoken/on-screen places if media was provided, or empty string for plain captions."
            ),
            "places" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Specific place, venue, landmark, or neighborhood name."),
                        "city" to Schema.string(description = "City or town name if mentioned or clearly implied, else empty string."),
                        "country" to Schema.string(description = "Country name if mentioned or clearly implied, else empty string."),
                        "category" to Schema.enumeration(
                            values = listOf(
                                "cafe", "restaurant", "bar", "viewpoint", "museum",
                                "landmark", "beach", "market", "neighborhood", "park", "hotel", "other"
                            ),
                            description = "Best matching place category."
                        ),
                        "why_mentioned" to Schema.string(
                            description = "Short plain-language reason the Reel recommended this stop (under 15 words)."
                        ),
                        "confidence" to Schema.enumeration(
                            values = listOf("high", "medium", "low"),
                            description = "high = explicitly named specific place; medium = partial/ambiguous name; low = uncertain or inferred."
                        )
                    )
                ),
                description = "List of distinct real-world travel places mentioned."
            )
        ),
        optionalProperties = listOf("transcript_summary")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    suspend fun extractFromText(
        captionText: String,
        sourceReelId: String
    ): ExtractionOutput {
        val cleanText = captionText.trim()
        require(cleanText.isNotEmpty()) { "Caption or description text is empty." }

        val prompt = """
            Extract every real-world travel place mentioned in this Instagram Reel caption, description, or transcript.
            Rules:
            - Include specific cafes, restaurants, bars, viewpoints, beaches, museums, markets, hotels, parks, landmarks, or distinct neighborhoods.
            - Do NOT invent places that are not in the text.
            - Infer city and country whenever strongly implied by the other stops or hashtags (e.g., Omotesando -> Tokyo, Japan).
            - Keep why_mentioned short, plain, and faithful to the text.
            - Set confidence to 'high' for clear named venues, 'medium' for short/ambiguous names, 'low' if uncertain.

            INPUT TEXT:
            $cleanText
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty response.")
            parseExtractionJson(jsonText, sourceReelId)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Text place extraction timed out", e)
            throw IllegalStateException("Extraction timed out after 60 seconds. Try a shorter snippet or retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text place extraction failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Couldn't extract places from this text.")
        }
    }

    suspend fun transcribeAndExtractFromMedia(
        contentResolver: ContentResolver,
        uri: Uri,
        mimeType: String,
        sourceReelId: String
    ): ExtractionOutput {
        val bytes = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { stream ->
                val data = stream.readBytes()
                if (data.size > MAX_MEDIA_BYTES) {
                    throw IllegalArgumentException(
                        "File is too large (${data.size / (1024 * 1024)} MB). Keep screen recordings or audio clips under 18 MB."
                    )
                }
                data
            } ?: throw IllegalStateException("Couldn't read the selected file.")
        }

        val resolvedMime = mimeType.ifBlank { "video/mp4" }
        val promptText = """
            Transcribe the spoken audio and read any on-screen place captions/labels in this Instagram Reel screen recording or audio clip.
            Put a concise transcript/summary in transcript_summary, and extract every real-world travel place mentioned into places.
            Do not invent places not spoken or shown. Infer city/country from context when clear.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(bytes, resolvedMime)
                        text(promptText)
                    }
                )
            }
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty response for this recording.")
            parseExtractionJson(jsonText, sourceReelId)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Media transcription timed out", e)
            throw IllegalStateException("Transcription timed out. Try a shorter clip under 18 MB or paste the caption.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Media transcription failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Couldn't transcribe this recording.")
        }
    }

    private fun parseExtractionJson(jsonText: String, sourceReelId: String): ExtractionOutput {
        val root = JSONObject(jsonText)
        val title = root.optString("suggested_trip_title", "").trim().ifBlank { "Reel Trip" }
        val transcript = root.optString("transcript_summary", "").trim()
        val array = root.optJSONArray("places")
        val places = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val city = obj.optString("city", "").trim()
                    val country = obj.optString("country", "").trim()
                    val category = PlaceCategory.fromRaw(obj.optString("category", "other"))
                    val why = obj.optString("why_mentioned", "").trim()
                        .ifBlank { "Mentioned in Reel" }
                    val confidence = PlaceConfidence.fromRaw(obj.optString("confidence", "medium"))

                    add(
                        TripPlace(
                            id = "pl_${UUID.randomUUID().toString().take(8)}",
                            name = name,
                            city = city,
                            country = country,
                            category = category.wireName,
                            whyMentioned = why,
                            confidence = confidence.wireName,
                            geocodeStatus = GeocodeStatus.NEEDS_FIX.wireName,
                            durationMinutes = category.defaultDurationMinutes,
                            sourceReelId = sourceReelId
                        )
                    )
                }
            }
        }
        return ExtractionOutput(
            suggestedTripTitle = title,
            transcriptSummary = transcript,
            places = places
        )
    }

    companion object {
        private const val TAG = "PlaceExtractorAi"
        private const val MAX_MEDIA_BYTES = 18 * 1024 * 1024
    }
}
