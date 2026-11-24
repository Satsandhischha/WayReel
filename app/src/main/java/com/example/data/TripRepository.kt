package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ReelSource
import com.example.model.Trip
import com.example.model.TripPlace
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TripRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google before accessing saved trips.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Signed-in account changed; refusing cross-user write." }
        }
        return uid
    }

    fun observeTrips(userId: String): Flow<List<Trip>> = flow {
        val path = "users/$userId/trips"
        emitAll(
            db.collection("users").document(userId).collection("trips")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> docToTrip(doc) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createTrip(userId: String, trip: Trip): Result<String> {
        var docPath = "users/$userId/trips"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("trips").document()
            docPath = docRef.path
            docRef.set(trip.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTrip(userId: String, trip: Trip): Result<Unit> {
        var docPath = "users/$userId/trips/${trip.id}"
        return try {
            val uid = requireUserId(userId)
            require(trip.id.isNotBlank()) { "Trip ID must not be blank for update." }
            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            docRef.update(trip.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(userId: String, tripId: String): Result<Unit> {
        var docPath = "users/$userId/trips/$tripId"
        return try {
            val uid = requireUserId(userId)
            require(tripId.isNotBlank()) { "Trip ID must not be blank for delete." }
            val docRef = db.collection("users").document(uid).collection("trips").document(tripId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(
        userId: String,
        tripId: String,
        rating: String,
        comment: String?,
        reelCount: Int,
        placeCount: Int
    ): Result<Unit> {
        var docPath = "users/$userId/feedback"
        return try {
            val uid = requireUserId(userId)
            require(rating in setOf("yes", "mostly", "no")) { "Invalid feedback rating." }
            val safeTripId = tripId.ifBlank { "unsaved_trip" }.take(128)
            val docRef = db.collection("users").document(uid).collection("feedback").document()
            docPath = docRef.path
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "tripId" to safeTripId,
                "rating" to rating,
                "reelCount" to reelCount.coerceIn(0, 10),
                "placeCount" to placeCount.coerceIn(0, 200),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val trimmedComment = comment?.trim().orEmpty()
            if (trimmedComment.isNotEmpty()) {
                payload["comment"] = trimmedComment.take(1000)
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToTrip(doc: DocumentSnapshot): Trip? {
        val data = doc.data ?: return null
        val rawReels = (data["reelSources"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
            ?.map { ReelSource.fromMap(it) }
            .orEmpty()
        val rawPlaces = (data["places"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
            ?.map { TripPlace.fromMap(it) }
            .orEmpty()
        return Trip(
            id = doc.id,
            userId = data["userId"] as? String ?: "",
            title = data["title"] as? String ?: "Untitled Trip",
            daysCount = (data["daysCount"] as? Number)?.toInt() ?: 3,
            startTimeMinutes = (data["startTimeMinutes"] as? Number)?.toInt() ?: 540,
            reelSources = rawReels,
            places = rawPlaces,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }
}
