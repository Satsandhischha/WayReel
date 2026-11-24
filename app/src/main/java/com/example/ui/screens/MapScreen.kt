package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ItineraryPlanner
import com.example.model.PlaceCategory
import com.example.model.SampleCaption
import com.example.model.Trip
import com.example.model.TripPlace
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.FixGeocodeSheet
import com.example.ui.components.GlassCard
import com.example.ui.components.NavyGradientScaffoldBackground
import com.example.ui.components.OsmRouteMap
import com.example.ui.components.ReelInputLadder
import com.example.ui.components.SampleCaptionsSheet
import com.example.ui.components.UppercaseLabel
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyMid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InputLadderState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.TripsListUiState

@Composable
fun MapScreen(
    viewModel: MainViewModel,
    onGoToExtract: () -> Unit,
    onOpenTripWorkspace: (tripId: String, initialTab: Int) -> Unit
) {
    val context = LocalContext.current
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val ladderState by viewModel.ladderState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearchingGeocode by viewModel.isSearchingGeocode.collectAsStateWithLifecycle()

    val savedTrips: List<Trip> = remember(tripsState) {
        (tripsState as? TripsListUiState.Success)?.trips.orEmpty()
    }

    var showAllTrips by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedCategoryWire by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSourceReelId by rememberSaveable { mutableStateOf<String?>(null) }

    var showQuickExtractSheet by remember { mutableStateOf(false) }
    var fixingPlace by remember { mutableStateOf<TripPlace?>(null) }

    val focusedTrip: Trip? = remember(activeTrip, savedTrips, showAllTrips) {
        if (showAllTrips) null else (activeTrip ?: savedTrips.firstOrNull())
    }

    val basePlaces = remember(focusedTrip, savedTrips, showAllTrips) {
        if (showAllTrips) {
            savedTrips.flatMap { it.places }.filter { it.isResolved }
        } else {
            focusedTrip?.places.orEmpty()
        }
    }

    val allReelSources = remember(focusedTrip, savedTrips, showAllTrips) {
        if (showAllTrips) {
            savedTrips.flatMap { it.reelSources }.distinctBy { it.id }
        } else {
            focusedTrip?.reelSources.orEmpty()
        }
    }

    val availableCategories = remember(basePlaces) {
        basePlaces.filter { it.isResolved }
            .map { it.categoryEnum }
            .distinct()
            .sortedBy { it.label }
    }

    val filteredPlaces = remember(basePlaces, selectedCategoryWire, selectedSourceReelId) {
        basePlaces.filter { place ->
            val catMatch = selectedCategoryWire == null || place.categoryEnum.wireName == selectedCategoryWire
            val reelMatch = selectedSourceReelId == null || place.sourceReelId == selectedSourceReelId
            catMatch && reelMatch
        }
    }

    val schedule = remember(focusedTrip, showAllTrips) {
        if (showAllTrips || focusedTrip == null) {
            emptyList()
        } else {
            ItineraryPlanner.buildSchedule(
                places = focusedTrip.places,
                daysCount = focusedTrip.daysCount,
                startTimeMinutes = focusedTrip.startTimeMinutes
            )
        }
    }

    val unresolvedPlaces = remember(focusedTrip) {
        focusedTrip?.places?.filter { !it.isResolved }.orEmpty()
    }
    val unresolvedCount = if (showAllTrips) {
        savedTrips.sumOf { t -> t.places.count { !it.isResolved } }
    } else {
        unresolvedPlaces.size
    }

    val isBusyOnMap = ladderState is InputLadderState.CheckingLink ||
        ladderState is InputLadderState.TranscribingMedia ||
        ladderState is InputLadderState.ExtractingPlaces ||
        ladderState is InputLadderState.GeocodingPlaces

    val busyLabel = when (ladderState) {
        InputLadderState.CheckingLink -> "Checking Reel..."
        InputLadderState.TranscribingMedia -> "Transcribing clip..."
        InputLadderState.ExtractingPlaces -> "Extracting locations..."
        InputLadderState.GeocodingPlaces -> "Dropping map pins..."
        else -> null
    }

    NavyGradientScaffoldBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UppercaseLabel("OSMDroid Dynamic Pins")
                    Text(
                        text = when {
                            showAllTrips -> "All Extracted Spots"
                            focusedTrip != null -> focusedTrip.title
                            else -> "Extracted Places Map"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            viewModel.resetLadderState()
                            showQuickExtractSheet = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("map_quick_extract_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = AmberBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add Reel", color = AmberBright)
                    }

                    if (focusedTrip != null) {
                        IconButton(
                            onClick = { onOpenTripWorkspace(focusedTrip.id, 0) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("map_edit_stops_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Edit stops",
                                tint = AmberBright
                            )
                        }
                    }
                }
            }

            // Trip selector chips
            if (savedTrips.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = showAllTrips,
                            onClick = {
                                showAllTrips = true
                                selectedDay = null
                                selectedSourceReelId = null
                                selectedCategoryWire = null
                            },
                            label = {
                                val total = savedTrips.sumOf { t -> t.places.count { it.isResolved } }
                                Text("All Reels ($total)")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberMuted,
                                selectedLabelColor = AmberBright,
                                labelColor = TextSecondary
                            )
                        )
                    }
                    items(savedTrips, key = { it.id }) { trip ->
                        val isSelected = !showAllTrips && focusedTrip?.id == trip.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                showAllTrips = false
                                selectedDay = null
                                selectedSourceReelId = null
                                selectedCategoryWire = null
                                viewModel.openTrip(trip.id)
                            },
                            label = { Text(trip.title.take(22)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberMuted,
                                selectedLabelColor = AmberBright,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }

            // Day, Reel-source, and Category dynamic filter chips
            val showFilterRow = (!showAllTrips && schedule.isNotEmpty()) ||
                availableCategories.size > 1 ||
                (!showAllTrips && allReelSources.size > 1)

            if (showFilterRow) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!showAllTrips && schedule.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = selectedDay == null && selectedCategoryWire == null && selectedSourceReelId == null,
                                onClick = {
                                    selectedDay = null
                                    selectedCategoryWire = null
                                    selectedSourceReelId = null
                                },
                                label = { Text("All pins") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberMuted,
                                    selectedLabelColor = AmberBright,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                        items(schedule, key = { "day_${it.dayIndex}" }) { day ->
                            FilterChip(
                                selected = selectedDay == day.dayIndex,
                                onClick = {
                                    selectedDay = if (selectedDay == day.dayIndex) null else day.dayIndex
                                },
                                label = { Text("Day ${day.dayIndex + 1} (${day.stops.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberMuted,
                                    selectedLabelColor = AmberBright,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    if (!showAllTrips && allReelSources.size > 1) {
                        items(allReelSources, key = { "reel_${it.id}" }) { reel ->
                            val selected = selectedSourceReelId == reel.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedSourceReelId = if (selected) null else reel.id
                                },
                                label = { Text("Reel: ${reel.label.take(16)}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberMuted,
                                    selectedLabelColor = AmberBright,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    if (availableCategories.size > 1) {
                        items(availableCategories, key = { "cat_${it.wireName}" }) { category ->
                            val selected = selectedCategoryWire == category.wireName
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategoryWire = if (selected) null else category.wireName
                                },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberMuted,
                                    selectedLabelColor = AmberBright,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Unresolved warning banner with direct in-map fix shortcut
            if (unresolvedCount > 0 && focusedTrip != null) {
                GlassCard(
                    borderColor = ErrorCoral.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = ErrorCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$unresolvedCount extracted place${if (unresolvedCount == 1) "" else "s"} need pin coordinates",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        TextButton(
                            onClick = {
                                val firstUnresolved = unresolvedPlaces.firstOrNull()
                                if (firstUnresolved != null) {
                                    fixingPlace = firstUnresolved
                                } else {
                                    onOpenTripWorkspace(focusedTrip.id, 0)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditLocationAlt,
                                contentDescription = null,
                                tint = AmberBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Fix pin", color = AmberBright)
                        }
                    }
                }
            }

            // Interactive OSMDroid Map with dynamic markers
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                OsmRouteMap(
                    places = filteredPlaces,
                    scheduledDays = schedule,
                    selectedDayFilter = selectedDay,
                    reelSources = allReelSources,
                    isBusyOnMap = isBusyOnMap,
                    busyLabel = busyLabel,
                    onFixPlace = if (!showAllTrips && focusedTrip != null) {
                        { place -> fixingPlace = place }
                    } else null,
                    emptyHint = if (savedTrips.isEmpty()) {
                        "Paste a Reel link or caption to drop dynamic pins on OpenStreetMap."
                    } else {
                        "No resolved coordinates match the active filters. Clear filters or tap Fix pin."
                    }
                )

                if (savedTrips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        AmberGradientButton(
                            text = "Paste a Reel caption",
                            icon = Icons.Outlined.Add,
                            onClick = {
                                viewModel.resetLadderState()
                                showQuickExtractSheet = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_empty_extract_button")
                        )
                    }
                }
            }
        }
    }

    if (showQuickExtractSheet) {
        QuickExtractOnMapSheet(
            ladderState = ladderState,
            focusedTrip = focusedTrip,
            onSubmitLink = { link, addToTrip ->
                viewModel.submitReelLink(link, targetExistingTrip = addToTrip) { tripId ->
                    showAllTrips = false
                    selectedDay = null
                    selectedCategoryWire = null
                    selectedSourceReelId = null
                    viewModel.openTrip(tripId)
                    showQuickExtractSheet = false
                }
            },
            onSubmitCaption = { caption, url, label, addToTrip ->
                viewModel.submitCaptionText(caption, url, label, targetExistingTrip = addToTrip) { tripId ->
                    showAllTrips = false
                    selectedDay = null
                    selectedCategoryWire = null
                    selectedSourceReelId = null
                    viewModel.openTrip(tripId)
                    showQuickExtractSheet = false
                }
            },
            onSubmitMedia = { uri, mime, name, url, addToTrip ->
                viewModel.submitMediaFile(context.contentResolver, uri, mime, name, url, targetExistingTrip = addToTrip) { tripId ->
                    showAllTrips = false
                    selectedDay = null
                    selectedCategoryWire = null
                    selectedSourceReelId = null
                    viewModel.openTrip(tripId)
                    showQuickExtractSheet = false
                }
            },
            onDismiss = { showQuickExtractSheet = false }
        )
    }

    val placeToFix = fixingPlace
    if (placeToFix != null) {
        FixGeocodeSheet(
            place = placeToFix,
            results = searchResults,
            isSearching = isSearchingGeocode,
            onSearch = viewModel::searchGeocodeCandidates,
            onSelectCandidate = { cand ->
                viewModel.applyGeocodeCandidate(placeToFix.id, cand)
                fixingPlace = null
            },
            onDismiss = {
                viewModel.clearGeocodeSearch()
                fixingPlace = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickExtractOnMapSheet(
    ladderState: InputLadderState,
    focusedTrip: Trip?,
    onSubmitLink: (link: String, addToTrip: Boolean) -> Unit,
    onSubmitCaption: (caption: String, url: String, label: String, addToTrip: Boolean) -> Unit,
    onSubmitMedia: (uri: android.net.Uri, mime: String, name: String, url: String, addToTrip: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canAddToExisting = focusedTrip != null && focusedTrip.reelSources.size < MainViewModel.MAX_REELS_PER_TRIP
    var addToCurrentTrip by rememberSaveable(focusedTrip?.id) { mutableStateOf(canAddToExisting) }
    var showSamples by remember { mutableStateOf(false) }
    var pendingSample by remember { mutableStateOf<SampleCaption?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyMid,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UppercaseLabel("Extract Locations to Map")
            Text(
                text = "Drop dynamic pins from a Reel caption",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            if (focusedTrip != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canAddToExisting) {
                        FilterChip(
                            selected = addToCurrentTrip,
                            onClick = { addToCurrentTrip = true },
                            label = { Text("Add to ${focusedTrip.title.take(16)}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberMuted,
                                selectedLabelColor = AmberBright,
                                labelColor = TextSecondary
                            )
                        )
                    }
                    FilterChip(
                        selected = !addToCurrentTrip,
                        onClick = { addToCurrentTrip = false },
                        label = { Text("New mapped trip") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberMuted,
                            selectedLabelColor = AmberBright,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            ReelInputLadder(
                ladderState = ladderState,
                onSubmitLink = { link -> onSubmitLink(link, addToCurrentTrip && canAddToExisting) },
                onSubmitCaption = { caption, url, label ->
                    onSubmitCaption(caption, url, label, addToCurrentTrip && canAddToExisting)
                },
                onSubmitMedia = { uri, mime, name, url ->
                    onSubmitMedia(uri, mime, name, url, addToCurrentTrip && canAddToExisting)
                },
                onOpenSamples = { showSamples = true },
                selectedSample = pendingSample,
                onConsumedSample = { pendingSample = null }
            )
        }
    }

    if (showSamples) {
        SampleCaptionsSheet(
            onSelectSample = { sample ->
                pendingSample = sample
                showSamples = false
            },
            onDismiss = { showSamples = false }
        )
    }
}
