package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

enum class PlaceCategory(
    val wireName: String,
    val label: String,
    val defaultDurationMinutes: Int
) {
    CAFE("cafe", "Cafe", 45),
    RESTAURANT("restaurant", "Restaurant", 75),
    BAR("bar", "Bar", 60),
    VIEWPOINT("viewpoint", "Viewpoint", 30),
    MUSEUM("museum", "Museum", 90),
    LANDMARK("landmark", "Landmark", 45),
    BEACH("beach", "Beach", 90),
    MARKET("market", "Market", 60),
    NEIGHBORHOOD("neighborhood", "Neighborhood", 90),
    PARK("park", "Park", 60),
    HOTEL("hotel", "Stay", 30),
    OTHER("other", "Spot", 45);

    companion object {
        fun fromRaw(raw: String?): PlaceCategory {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized || it.name.lowercase() == normalized }
                ?: OTHER
        }
    }
}

enum class PlaceConfidence(val wireName: String, val label: String) {
    HIGH("high", "High"),
    MEDIUM("medium", "Medium"),
    LOW("low", "Low");

    companion object {
        fun fromRaw(raw: String?): PlaceConfidence {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized } ?: MEDIUM
        }
    }
}

enum class GeocodeStatus(val wireName: String) {
    RESOLVED("resolved"),
    NEEDS_FIX("needs_fix");

    companion object {
        fun fromRaw(raw: String?): GeocodeStatus {
            return if (raw == RESOLVED.wireName) RESOLVED else NEEDS_FIX
        }
    }
}

enum class ReelInputType(val wireName: String, val label: String) {
    LINK("link", "Reel link"),
    CAPTION("caption", "Caption"),
    MEDIA("media", "Screen recording / Audio");

    companion object {
        fun fromRaw(raw: String?): ReelInputType {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized } ?: CAPTION
        }
    }
}

@Keep
@IgnoreExtraProperties
@Serializable
data class ReelSource(
    val id: String = "",
    val inputType: String = ReelInputType.CAPTION.wireName,
    val label: String = "",
    val rawText: String = "",
    val reelUrl: String = "",
    val status: String = "extracted"
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "inputType" to inputType,
        "label" to label.take(120),
        "rawText" to rawText.take(4000),
        "reelUrl" to reelUrl.take(500),
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ReelSource {
            return ReelSource(
                id = map["id"] as? String ?: "",
                inputType = map["inputType"] as? String ?: ReelInputType.CAPTION.wireName,
                label = map["label"] as? String ?: "",
                rawText = map["rawText"] as? String ?: "",
                reelUrl = map["reelUrl"] as? String ?: "",
                status = map["status"] as? String ?: "extracted"
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
@Serializable
data class TripPlace(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val country: String = "",
    val category: String = PlaceCategory.OTHER.wireName,
    val whyMentioned: String = "",
    val confidence: String = PlaceConfidence.MEDIUM.wireName,
    val lat: Double? = null,
    val lng: Double? = null,
    val geocodeStatus: String = GeocodeStatus.NEEDS_FIX.wireName,
    val displayAddress: String = "",
    val dayIndex: Int = 0,
    val orderInDay: Int = 0,
    val durationMinutes: Int = 45,
    val sourceReelId: String = ""
) {
    val categoryEnum: PlaceCategory get() = PlaceCategory.fromRaw(category)
    val confidenceEnum: PlaceConfidence get() = PlaceConfidence.fromRaw(confidence)
    val isResolved: Boolean get() = geocodeStatus == GeocodeStatus.RESOLVED.wireName && lat != null && lng != null

    fun toFirestoreMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "id" to id,
            "name" to name.take(140),
            "city" to city.take(80),
            "country" to country.take(80),
            "category" to category,
            "whyMentioned" to whyMentioned.take(300),
            "confidence" to confidence,
            "geocodeStatus" to geocodeStatus,
            "displayAddress" to displayAddress.take(240),
            "dayIndex" to dayIndex,
            "orderInDay" to orderInDay,
            "durationMinutes" to durationMinutes,
            "sourceReelId" to sourceReelId
        )
        if (lat != null && lng != null) {
            base["lat"] = lat
            base["lng"] = lng
        }
        return base
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): TripPlace {
            return TripPlace(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                city = map["city"] as? String ?: "",
                country = map["country"] as? String ?: "",
                category = map["category"] as? String ?: PlaceCategory.OTHER.wireName,
                whyMentioned = map["whyMentioned"] as? String ?: "",
                confidence = map["confidence"] as? String ?: PlaceConfidence.MEDIUM.wireName,
                lat = (map["lat"] as? Number)?.toDouble(),
                lng = (map["lng"] as? Number)?.toDouble(),
                geocodeStatus = map["geocodeStatus"] as? String ?: GeocodeStatus.NEEDS_FIX.wireName,
                displayAddress = map["displayAddress"] as? String ?: "",
                dayIndex = (map["dayIndex"] as? Number)?.toInt() ?: 0,
                orderInDay = (map["orderInDay"] as? Number)?.toInt() ?: 0,
                durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 45,
                sourceReelId = map["sourceReelId"] as? String ?: ""
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Trip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled Trip",
    val daysCount: Int = 3,
    val startTimeMinutes: Int = 540,
    val reelSources: List<ReelSource> = emptyList(),
    val places: List<TripPlace> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.trim().ifBlank { "New Reel Trip" }.take(120),
        "daysCount" to daysCount.coerceIn(1, 10),
        "startTimeMinutes" to startTimeMinutes.coerceIn(0, 1439),
        "reelSources" to reelSources.take(10).map { it.toFirestoreMap() },
        "places" to places.take(80).map { it.toFirestoreMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().ifBlank { "New Reel Trip" }.take(120),
        "daysCount" to daysCount.coerceIn(1, 10),
        "startTimeMinutes" to startTimeMinutes.coerceIn(0, 1439),
        "reelSources" to reelSources.take(10).map { it.toFirestoreMap() },
        "places" to places.take(80).map { it.toFirestoreMap() },
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FeedbackSubmission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tripId: String = "",
    val rating: String = "yes",
    val comment: String? = null,
    val reelCount: Int = 0,
    val placeCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class GeocodeCandidate(
    val displayName: String,
    val lat: Double,
    val lng: Double,
    val city: String,
    val country: String
)

data class ScheduledStop(
    val place: TripPlace,
    val stopNumber: Int,
    val arrivalMinutes: Int,
    val departureMinutes: Int,
    val travelFromPrevMinutes: Int?,
    val distanceFromPrevKm: Double?,
    val isWalkingLeg: Boolean
)

data class ScheduledDay(
    val dayIndex: Int,
    val stops: List<ScheduledStop>,
    val totalDistanceKm: Double,
    val totalDurationMinutes: Int
)

data class SampleCaption(
    val id: String,
    val title: String,
    val region: String,
    val caption: String
)
