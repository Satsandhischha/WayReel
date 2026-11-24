package com.example.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeocodingService
import com.example.data.InstagramOEmbedClient
import com.example.data.ItineraryPlanner
import com.example.data.OEmbedResult
import com.example.data.PlaceExtractorAiService
import com.example.data.SharedTripSnapshot
import com.example.data.TripRepository
import com.example.data.TripShareCodec
import com.example.model.GeocodeCandidate
import com.example.model.GeocodeStatus
import com.example.model.PlaceCategory
import com.example.model.PlaceConfidence
import com.example.model.ReelInputType
import com.example.model.ReelSource
import com.example.model.ScheduledDay
import com.example.model.Trip
import com.example.model.TripPlace
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface TripsListUiState {
    data object Loading : TripsListUiState
    data class Success(val trips: List<Trip>) : TripsListUiState
    data class Error(val message: String) : TripsListUiState
}

sealed interface InputLadderState {
    data object Idle : InputLadderState
    data object CheckingLink : InputLadderState
    data class LinkFallbackPrompt(
        val attachedUrl: String,
        val message: String
    ) : InputLadderState
    data object TranscribingMedia : InputLadderState
    data object ExtractingPlaces : InputLadderState
    data object GeocodingPlaces : InputLadderState
    data class Error(val message: String) : InputLadderState
}

sealed interface FeedbackUiState {
    data object Idle : FeedbackUiState
    data object Submitting : FeedbackUiState
    data object Submitted : FeedbackUiState
    data class Error(val message: String) : FeedbackUiState
}

class MainViewModel(
    private val repository: TripRepository,
    private val currentUserId: String,
    private val oEmbedClient: InstagramOEmbedClient = InstagramOEmbedClient(),
    private val aiService: PlaceExtractorAiService = PlaceExtractorAiService(),
    private val geocodingService: GeocodingService = GeocodingService()
) : ViewModel() {

    val tripsState: StateFlow<TripsListUiState> = repository.observeTrips(currentUserId)
        .map<List<Trip>, TripsListUiState> { TripsListUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing trips", rootError)
            emit(TripsListUiState.Error(rootError.localizedMessage ?: "Failed to load saved trips."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TripsListUiState.Loading
        )

    private val _activeTripId = MutableStateFlow<String?>(null)
    private val _draftTrip = MutableStateFlow<Trip?>(null)

    val activeTrip: StateFlow<Trip?> = combine(
        tripsState,
        _activeTripId,
        _draftTrip
    ) { listState, selectedId, draft ->
        if (selectedId != null && listState is TripsListUiState.Success) {
            listState.trips.firstOrNull { it.id == selectedId } ?: draft
        } else {
            draft
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    val schedule: StateFlow<List<ScheduledDay>> = activeTrip
        .map { trip ->
            if (trip == null) emptyList()
            else ItineraryPlanner.buildSchedule(
                places = trip.places,
                daysCount = trip.daysCount,
                startTimeMinutes = trip.startTimeMinutes
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _ladderState = MutableStateFlow<InputLadderState>(InputLadderState.Idle)
    val ladderState: StateFlow<InputLadderState> = _ladderState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodeCandidate>>(emptyList())
    val searchResults: StateFlow<List<GeocodeCandidate>> = _searchResults.asStateFlow()

    private val _isSearchingGeocode = MutableStateFlow(false)
    val isSearchingGeocode: StateFlow<Boolean> = _isSearchingGeocode.asStateFlow()

    private val _feedbackState = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val feedbackState: StateFlow<FeedbackUiState> = _feedbackState.asStateFlow()

    private val _sharedPreview = MutableStateFlow<SharedTripSnapshot?>(null)
    val sharedPreview: StateFlow<SharedTripSnapshot?> = _sharedPreview.asStateFlow()

    private var geocodeSearchJob: Job? = null

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun resetLadderState() {
        _ladderState.value = InputLadderState.Idle
    }

    fun openTrip(tripId: String) {
        _activeTripId.value = tripId
        _draftTrip.value = null
        _feedbackState.value = FeedbackUiState.Idle
        _ladderState.value = InputLadderState.Idle
    }

    fun closeActiveTrip() {
        _activeTripId.value = null
        _draftTrip.value = null
        _ladderState.value = InputLadderState.Idle
    }

    fun openSharedLink(rawLinkOrToken: String): Boolean {
        val snapshot = TripShareCodec.decodeShareUrl(rawLinkOrToken)
        if (snapshot != null) {
            _sharedPreview.value = snapshot
            return true
        }
        _statusMessage.value = "That shared trip link couldn't be decoded."
        return false
    }

    fun closeSharedPreview() {
        _sharedPreview.value = null
    }

    fun saveSharedSnapshotToMyTrips(snapshot: SharedTripSnapshot, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val newTrip = Trip(
                userId = currentUserId,
                title = "${snapshot.title} (Copy)",
                daysCount = snapshot.daysCount,
                startTimeMinutes = snapshot.startTimeMinutes,
                reelSources = emptyList(),
                places = snapshot.places
            )
            repository.createTrip(currentUserId, newTrip)
                .onSuccess { id ->
                    _sharedPreview.value = null
                    openTrip(id)
                    onSaved(id)
                }
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "Couldn't save trip copy."
                }
        }
    }

    fun submitReelLink(
        rawLink: String,
        targetExistingTrip: Boolean,
        onTripReady: (String) -> Unit
    ) {
        val currentCount = if (targetExistingTrip) activeTrip.value?.reelSources?.size ?: 0 else 0
        if (currentCount >= MAX_REELS_PER_TRIP) {
            _ladderState.value = InputLadderState.Error("You've reached the 10-Reel limit for one trip.")
            return
        }

        viewModelScope.launch {
            _ladderState.value = InputLadderState.CheckingLink
            when (val result = oEmbedClient.tryFetchMetadata(rawLink)) {
                is OEmbedResult.Success -> {
                    val sourceId = "reel_${UUID.randomUUID().toString().take(8)}"
                    val source = ReelSource(
                        id = sourceId,
                        inputType = ReelInputType.LINK.wireName,
                        label = if (result.authorName.isNotBlank()) "Reel by @${result.authorName}" else "Instagram Reel",
                        rawText = result.captionOrTitle,
                        reelUrl = result.sourceUrl
                    )
                    runExtractionAndMerge(
                        source = source,
                        textToExtract = result.captionOrTitle,
                        targetExistingTrip = targetExistingTrip,
                        onTripReady = onTripReady
                    )
                }
                is OEmbedResult.FallbackNeeded -> {
                    _ladderState.value = InputLadderState.LinkFallbackPrompt(
                        attachedUrl = result.normalizedUrl,
                        message = result.reason
                    )
                }
                is OEmbedResult.InvalidInput -> {
                    _ladderState.value = InputLadderState.LinkFallbackPrompt(
                        attachedUrl = "",
                        message = result.reason
                    )
                }
            }
        }
    }

    fun submitCaptionText(
        captionText: String,
        attachedUrl: String = "",
        customLabel: String = "",
        targetExistingTrip: Boolean,
        onTripReady: (String) -> Unit
    ) {
        val clean = captionText.trim()
        if (clean.isBlank()) {
            _ladderState.value = InputLadderState.Error("Paste the Reel caption or description first.")
            return
        }
        val currentCount = if (targetExistingTrip) activeTrip.value?.reelSources?.size ?: 0 else 0
        if (currentCount >= MAX_REELS_PER_TRIP) {
            _ladderState.value = InputLadderState.Error("You've reached the 10-Reel limit for one trip.")
            return
        }

        val sourceId = "reel_${UUID.randomUUID().toString().take(8)}"
        val label = customLabel.ifBlank {
            clean.lineSequence().firstOrNull { it.isNotBlank() }?.take(42) ?: "Pasted Caption"
        }
        val source = ReelSource(
            id = sourceId,
            inputType = ReelInputType.CAPTION.wireName,
            label = label,
            rawText = clean,
            reelUrl = attachedUrl
        )

        viewModelScope.launch {
            runExtractionAndMerge(
                source = source,
                textToExtract = clean,
                targetExistingTrip = targetExistingTrip,
                onTripReady = onTripReady
            )
        }
    }

    fun submitMediaFile(
        contentResolver: ContentResolver,
        uri: Uri,
        mimeType: String,
        displayName: String,
        attachedUrl: String = "",
        targetExistingTrip: Boolean,
        onTripReady: (String) -> Unit
    ) {
        val currentCount = if (targetExistingTrip) activeTrip.value?.reelSources?.size ?: 0 else 0
        if (currentCount >= MAX_REELS_PER_TRIP) {
            _ladderState.value = InputLadderState.Error("You've reached the 10-Reel limit for one trip.")
            return
        }

        viewModelScope.launch {
            _ladderState.value = InputLadderState.TranscribingMedia
            val sourceId = "reel_${UUID.randomUUID().toString().take(8)}"
            try {
                val output = aiService.transcribeAndExtractFromMedia(
                    contentResolver = contentResolver,
                    uri = uri,
                    mimeType = mimeType,
                    sourceReelId = sourceId
                )
                if (output.places.isEmpty()) {
                    _ladderState.value = InputLadderState.Error(
                        "No specific places were heard or spotted in that clip. Try pasting the caption instead."
                    )
                    return@launch
                }

                _ladderState.value = InputLadderState.GeocodingPlaces
                val geocoded = geocodingService.geocodePlaces(output.places)
                val source = ReelSource(
                    id = sourceId,
                    inputType = ReelInputType.MEDIA.wireName,
                    label = displayName.ifBlank { "Screen recording" },
                    rawText = output.transcriptSummary,
                    reelUrl = attachedUrl
                )
                mergeAndPersistTrip(
                    newSource = source,
                    newPlaces = geocoded,
                    suggestedTitle = output.suggestedTripTitle,
                    targetExistingTrip = targetExistingTrip,
                    onTripReady = onTripReady
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ladderState.value = InputLadderState.Error(
                    e.localizedMessage ?: "Couldn't transcribe that recording."
                )
            }
        }
    }

    private suspend fun runExtractionAndMerge(
        source: ReelSource,
        textToExtract: String,
        targetExistingTrip: Boolean,
        onTripReady: (String) -> Unit
    ) {
        _ladderState.value = InputLadderState.ExtractingPlaces
        try {
            val output = aiService.extractFromText(textToExtract, source.id)
            if (output.places.isEmpty()) {
                _ladderState.value = InputLadderState.Error(
                    "No places found in that text. Check that venue or city names are included, or add places manually."
                )
                return
            }

            _ladderState.value = InputLadderState.GeocodingPlaces
            val geocoded = geocodingService.geocodePlaces(output.places)
            mergeAndPersistTrip(
                newSource = source,
                newPlaces = geocoded,
                suggestedTitle = output.suggestedTripTitle,
                targetExistingTrip = targetExistingTrip,
                onTripReady = onTripReady
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ladderState.value = InputLadderState.Error(
                e.localizedMessage ?: "Extraction failed. Please try again."
            )
        }
    }

    private suspend fun mergeAndPersistTrip(
        newSource: ReelSource,
        newPlaces: List<TripPlace>,
        suggestedTitle: String,
        targetExistingTrip: Boolean,
        onTripReady: (String) -> Unit
    ) {
        val existing = if (targetExistingTrip) activeTrip.value else null
        if (existing == null) {
            val clustered = ItineraryPlanner.autoClusterAndOrder(newPlaces, daysCount = 3)
            val draft = Trip(
                userId = currentUserId,
                title = suggestedTitle,
                daysCount = 3,
                startTimeMinutes = 540,
                reelSources = listOf(newSource),
                places = clustered
            )
            repository.createTrip(currentUserId, draft)
                .onSuccess { newId ->
                    _activeTripId.value = newId
                    _draftTrip.value = draft.copy(id = newId)
                    _feedbackState.value = FeedbackUiState.Idle
                    _ladderState.value = InputLadderState.Idle
                    onTripReady(newId)
                }
                .onFailure { err ->
                    _ladderState.value = InputLadderState.Error(
                        err.localizedMessage ?: "Couldn't save trip to Firestore."
                    )
                }
        } else {
            val combinedReels = (existing.reelSources + newSource).take(MAX_REELS_PER_TRIP)
            val existingNames = existing.places.map { it.name.lowercase().trim() }.toSet()
            val dedupedNew = newPlaces.filter { it.name.lowercase().trim() !in existingNames }
            val combinedPlaces = (existing.places + dedupedNew).take(80)
            val reordered = ItineraryPlanner.autoClusterAndOrder(combinedPlaces, existing.daysCount)
            val updated = existing.copy(
                reelSources = combinedReels,
                places = reordered
            )
            persistUpdatedTrip(updated)
            _ladderState.value = InputLadderState.Idle
            if (existing.id.isNotBlank()) onTripReady(existing.id)
        }
    }

    fun updateTripTitle(newTitle: String) {
        val trip = activeTrip.value ?: return
        val clean = newTitle.trim().ifBlank { return }.take(120)
        persistTripAsync(trip.copy(title = clean))
    }

    fun removeReelSource(reelId: String) {
        val trip = activeTrip.value ?: return
        val remainingReels = trip.reelSources.filterNot { it.id == reelId }
        val remainingPlaces = trip.places.filterNot { it.sourceReelId == reelId }
        val reordered = ItineraryPlanner.autoClusterAndOrder(remainingPlaces, trip.daysCount)
        persistTripAsync(trip.copy(reelSources = remainingReels, places = reordered))
    }

    fun updatePlace(updatedPlace: TripPlace) {
        val trip = activeTrip.value ?: return
        viewModelScope.launch {
            val old = trip.places.firstOrNull { it.id == updatedPlace.id }
            val needsRegeocode = old != null &&
                (old.name != updatedPlace.name || old.city != updatedPlace.city || old.country != updatedPlace.country) &&
                updatedPlace.lat == old.lat

            val finalPlace = if (needsRegeocode) {
                geocodingService.resolveSinglePlace(
                    updatedPlace.copy(
                        lat = null,
                        lng = null,
                        geocodeStatus = GeocodeStatus.NEEDS_FIX.wireName
                    )
                )
            } else {
                updatedPlace
            }

            val updatedPlaces = trip.places.map { if (it.id == finalPlace.id) finalPlace else it }
            persistUpdatedTrip(trip.copy(places = updatedPlaces))
        }
    }

    fun addManualPlace(
        name: String,
        city: String,
        country: String,
        category: PlaceCategory,
        whyMentioned: String
    ) {
        val trip = activeTrip.value ?: return
        if (name.isBlank() || trip.places.size >= 80) return
        viewModelScope.launch {
            val raw = TripPlace(
                id = "pl_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                city = city.trim(),
                country = country.trim(),
                category = category.wireName,
                whyMentioned = whyMentioned.trim().ifBlank { "Added manually" },
                confidence = PlaceConfidence.HIGH.wireName,
                durationMinutes = category.defaultDurationMinutes,
                dayIndex = 0,
                orderInDay = trip.places.size
            )
            val resolved = geocodingService.resolveSinglePlace(raw)
            val updatedPlaces = ItineraryPlanner.autoClusterAndOrder(
                trip.places + resolved,
                trip.daysCount
            )
            persistUpdatedTrip(trip.copy(places = updatedPlaces))
        }
    }

    fun removePlace(placeId: String) {
        val trip = activeTrip.value ?: return
        val remaining = trip.places.filterNot { it.id == placeId }
        persistTripAsync(trip.copy(places = remaining))
    }

    fun searchGeocodeCandidates(query: String, cityBias: String = "", countryBias: String = "") {
        geocodeSearchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearchingGeocode.value = false
            return
        }
        geocodeSearchJob = viewModelScope.launch {
            _isSearchingGeocode.value = true
            try {
                _searchResults.value = geocodingService.searchCandidates(query, cityBias, countryBias)
            } finally {
                _isSearchingGeocode.value = false
            }
        }
    }

    fun clearGeocodeSearch() {
        geocodeSearchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearchingGeocode.value = false
    }

    fun applyGeocodeCandidate(placeId: String, candidate: GeocodeCandidate) {
        val trip = activeTrip.value ?: return
        val updatedPlaces = trip.places.map { place ->
            if (place.id == placeId) {
                place.copy(
                    lat = candidate.lat,
                    lng = candidate.lng,
                    city = place.city.ifBlank { candidate.city },
                    country = place.country.ifBlank { candidate.country },
                    displayAddress = candidate.displayName,
                    geocodeStatus = GeocodeStatus.RESOLVED.wireName
                )
            } else {
                place
            }
        }
        val clustered = ItineraryPlanner.autoClusterAndOrder(updatedPlaces, trip.daysCount)
        persistTripAsync(trip.copy(places = clustered))
        clearGeocodeSearch()
    }

    fun updateDaysCount(daysCount: Int) {
        val trip = activeTrip.value ?: return
        val safeDays = daysCount.coerceIn(1, 10)
        val reordered = ItineraryPlanner.autoClusterAndOrder(trip.places, safeDays)
        persistTripAsync(trip.copy(daysCount = safeDays, places = reordered))
    }

    fun updateStartTimeMinutes(minutes: Int) {
        val trip = activeTrip.value ?: return
        persistTripAsync(trip.copy(startTimeMinutes = minutes.coerceIn(0, 1439)))
    }

    fun reclusterItinerary() {
        val trip = activeTrip.value ?: return
        val reordered = ItineraryPlanner.autoClusterAndOrder(trip.places, trip.daysCount)
        persistTripAsync(trip.copy(places = reordered))
        _statusMessage.value = "Stops re-clustered by proximity."
    }

    fun moveStopInDay(placeId: String, delta: Int) {
        val trip = activeTrip.value ?: return
        val updatedPlaces = ItineraryPlanner.moveStopInDay(trip.places, placeId, delta)
        persistTripAsync(trip.copy(places = updatedPlaces))
    }

    fun moveStopToDay(placeId: String, targetDayIndex: Int) {
        val trip = activeTrip.value ?: return
        val updatedPlaces = ItineraryPlanner.moveStopToDay(trip.places, placeId, targetDayIndex)
        persistTripAsync(trip.copy(places = updatedPlaces))
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            repository.deleteTrip(currentUserId, tripId)
                .onSuccess {
                    if (_activeTripId.value == tripId) {
                        closeActiveTrip()
                    }
                }
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "Couldn't delete trip."
                }
        }
    }

    fun submitFeedback(rating: String, comment: String) {
        val trip = activeTrip.value ?: return
        viewModelScope.launch {
            _feedbackState.value = FeedbackUiState.Submitting
            repository.submitFeedback(
                userId = currentUserId,
                tripId = trip.id,
                rating = rating,
                comment = comment,
                reelCount = trip.reelSources.size,
                placeCount = trip.places.size
            ).onSuccess {
                _feedbackState.value = FeedbackUiState.Submitted
            }.onFailure { e ->
                _feedbackState.value = FeedbackUiState.Error(
                    e.localizedMessage ?: "Couldn't send feedback."
                )
            }
        }
    }

    private fun persistTripAsync(updated: Trip) {
        _draftTrip.value = updated
        viewModelScope.launch {
            persistUpdatedTrip(updated)
        }
    }

    private suspend fun persistUpdatedTrip(updated: Trip) {
        _draftTrip.value = updated
        if (updated.id.isBlank()) {
            repository.createTrip(currentUserId, updated)
                .onSuccess { id ->
                    _activeTripId.value = id
                    _draftTrip.value = updated.copy(id = id)
                }
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "Offline or sync error saving trip."
                }
        } else {
            repository.updateTrip(currentUserId, updated)
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "Couldn't sync changes right now."
                }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        const val MAX_REELS_PER_TRIP = 10
    }
}
