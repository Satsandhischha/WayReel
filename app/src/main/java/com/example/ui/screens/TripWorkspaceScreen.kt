package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ItineraryPlanner
import com.example.model.PlaceCategory
import com.example.model.ScheduledDay
import com.example.model.ScheduledStop
import com.example.model.Trip
import com.example.model.TripPlace
import com.example.ui.components.AddReelSheet
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.CategoryPill
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.EditPlaceDialog
import com.example.ui.components.FixGeocodeSheet
import com.example.ui.components.GlassCard
import com.example.ui.components.HowItWorksSheet
import com.example.ui.components.NavyGradientScaffoldBackground
import com.example.ui.components.OsmRouteMap
import com.example.ui.components.ShareTripSheet
import com.example.ui.components.UppercaseLabel
import com.example.ui.components.openInGoogleMaps
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.ConfidenceHigh
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassFill
import com.example.ui.theme.NavyMid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FeedbackUiState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun TripWorkspaceScreen(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    showBackButton: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val trip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val ladderState by viewModel.ladderState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearchingGeocode by viewModel.isSearchingGeocode.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) } // 0=Places, 1=Map, 2=Itinerary
    var selectedMapDay by rememberSaveable { mutableStateOf<Int?>(null) }

    var fixingPlace by remember { mutableStateOf<TripPlace?>(null) }
    var editingPlace by remember { mutableStateOf<TripPlace?>(null) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var showAddReelSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val currentTrip = trip
    NavyGradientScaffoldBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (currentTrip == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No trip selected yet", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Paste a Reel link or caption to build your first itinerary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    AmberGradientButton(text = "Extract a Reel", onClick = onBack)
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (showBackButton) 8.dp else 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("workspace_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back to trips",
                                    tint = TextPrimary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            UppercaseLabel("${currentTrip.reelSources.size}/10 Reels · ${currentTrip.places.size} stops")
                            Text(
                                text = currentTrip.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showHowItWorks = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = "How it works", tint = TextSecondary)
                        }
                        IconButton(
                            onClick = { showShareSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("share_trip_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share trip", tint = AmberBright)
                        }
                    }

                    // 3-tab bar: Places, Map, Itinerary
                    val tabs = listOf("Places", "Map", "Itinerary")
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = AmberBright,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = AmberBright
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, label ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("tab_${label.lowercase()}"),
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selectedTab == index) AmberBright else TextSecondary
                                    )
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> PlacesTabContent(
                            trip = currentTrip,
                            onAddReel = {
                                viewModel.resetLadderState()
                                showAddReelSheet = true
                            },
                            onRemoveReel = viewModel::removeReelSource,
                            onFixPlace = { fixingPlace = it },
                            onEditPlace = { editingPlace = it },
                            onDeletePlace = viewModel::removePlace,
                            onAddManualPlace = { showAddPlaceDialog = true },
                            onGoToMap = { selectedTab = 1 }
                        )
                        1 -> MapTabContent(
                            trip = currentTrip,
                            schedule = schedule,
                            selectedDay = selectedMapDay,
                            onSelectDay = { selectedMapDay = it },
                            onFixPlace = { fixingPlace = it }
                        )
                        2 -> ItineraryTabContent(
                            trip = currentTrip,
                            schedule = schedule,
                            feedbackState = feedbackState,
                            onChangeDays = viewModel::updateDaysCount,
                            onChangeStartTime = viewModel::updateStartTimeMinutes,
                            onRecluster = viewModel::reclusterItinerary,
                            onMoveStopInDay = viewModel::moveStopInDay,
                            onMoveStopToDay = viewModel::moveStopToDay,
                            onViewDayOnMap = { dayIdx ->
                                selectedMapDay = dayIdx
                                selectedTab = 1
                            },
                            onSubmitFeedback = viewModel::submitFeedback
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
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

    val placeToEdit = editingPlace
    if (placeToEdit != null) {
        EditPlaceDialog(
            initialPlace = placeToEdit,
            onSave = { name, city, country, cat, why ->
                viewModel.updatePlace(
                    placeToEdit.copy(
                        name = name.trim(),
                        city = city.trim(),
                        country = country.trim(),
                        category = cat.wireName,
                        durationMinutes = cat.defaultDurationMinutes,
                        whyMentioned = why.trim().ifBlank { placeToEdit.whyMentioned }
                    )
                )
                editingPlace = null
            },
            onDismiss = { editingPlace = null }
        )
    }

    if (showAddPlaceDialog) {
        EditPlaceDialog(
            initialPlace = null,
            onSave = { name, city, country, cat, why ->
                viewModel.addManualPlace(name, city, country, cat, why)
                showAddPlaceDialog = false
            },
            onDismiss = { showAddPlaceDialog = false }
        )
    }

    if (showAddReelSheet && currentTrip != null) {
        AddReelSheet(
            ladderState = ladderState,
            currentReelCount = currentTrip.reelSources.size,
            onSubmitLink = { link ->
                viewModel.submitReelLink(link, targetExistingTrip = true) {
                    showAddReelSheet = false
                }
            },
            onSubmitCaption = { caption, url, label ->
                viewModel.submitCaptionText(caption, url, label, targetExistingTrip = true) {
                    showAddReelSheet = false
                }
            },
            onSubmitMedia = { uri, mime, name, url ->
                viewModel.submitMediaFile(context.contentResolver, uri, mime, name, url, targetExistingTrip = true) {
                    showAddReelSheet = false
                }
            },
            onDismiss = { showAddReelSheet = false }
        )
    }

    if (showShareSheet && currentTrip != null) {
        ShareTripSheet(
            trip = currentTrip,
            schedule = schedule,
            onDismiss = { showShareSheet = false }
        )
    }

    if (showHowItWorks) {
        HowItWorksSheet(onDismiss = { showHowItWorks = false })
    }
}

@Composable
private fun PlacesTabContent(
    trip: Trip,
    onAddReel: () -> Unit,
    onRemoveReel: (String) -> Unit,
    onFixPlace: (TripPlace) -> Unit,
    onEditPlace: (TripPlace) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddManualPlace: () -> Unit,
    onGoToMap: () -> Unit
) {
    val needsFix = remember(trip.places) { trip.places.filter { !it.isResolved } }
    val resolved = remember(trip.places) { trip.places.filter { it.isResolved } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UppercaseLabel("Reels in this trip (${trip.reelSources.size}/10)")
                    TextButton(
                        onClick = onAddReel,
                        enabled = trip.reelSources.size < MainViewModel.MAX_REELS_PER_TRIP,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_reel_to_trip_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = AmberBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Reel", color = AmberBright)
                    }
                }
                if (trip.reelSources.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trip.reelSources, key = { it.id }) { reel ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = GlassFill,
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                ) {
                                    Text(
                                        text = reel.label.take(24),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                    IconButton(
                                        onClick = { onRemoveReel(reel.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Remove Reel",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (needsFix.isNotEmpty()) {
            item {
                UppercaseLabel("Needs a fix (${needsFix.size})", color = ErrorCoral)
            }
            items(needsFix, key = { "fix_${it.id}" }) { place ->
                GlassCard(
                    borderColor = ErrorCoral.copy(alpha = 0.5f),
                    contentPadding = PaddingValues(14.dp)
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
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ErrorCoral)
                            Column {
                                Text(place.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "Couldn't match coordinates automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        IconButton(onClick = { onDeletePlace(place.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove", tint = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AmberGradientButton(
                        text = "Search & fix pin",
                        onClick = { onFixPlace(place) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fix_place_button_${place.id}")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UppercaseLabel("Mapped Places (${resolved.size})")
                Row {
                    TextButton(
                        onClick = onAddManualPlace,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("+ Add stop", color = AmberBright)
                    }
                    if (resolved.isNotEmpty()) {
                        TextButton(
                            onClick = onGoToMap,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("View map", color = TextSecondary)
                        }
                    }
                }
            }
        }

        items(resolved, key = { it.id }) { place ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryPill(place.categoryEnum.label)
                        ConfidenceBadge(place.confidenceEnum)
                    }
                    Row {
                        IconButton(onClick = { onEditPlace(place) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit place", tint = TextSecondary)
                        }
                        IconButton(onClick = { onDeletePlace(place.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete place", tint = TextSecondary)
                        }
                    }
                }
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                val locationLine = listOf(place.city, place.country).filter { it.isNotBlank() }.joinToString(", ")
                if (locationLine.isNotBlank()) {
                    Text(locationLine, style = MaterialTheme.typography.labelSmall, color = AmberBright)
                }
                if (place.whyMentioned.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(place.whyMentioned, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MapTabContent(
    trip: Trip,
    schedule: List<ScheduledDay>,
    selectedDay: Int?,
    onSelectDay: (Int?) -> Unit,
    onFixPlace: (TripPlace) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedDay == null,
                    onClick = { onSelectDay(null) },
                    label = { Text("All days") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberMuted,
                        selectedLabelColor = AmberBright,
                        labelColor = TextSecondary
                    )
                )
            }
            items(schedule, key = { it.dayIndex }) { day ->
                FilterChip(
                    selected = selectedDay == day.dayIndex,
                    onClick = { onSelectDay(day.dayIndex) },
                    label = { Text("Day ${day.dayIndex + 1} (${day.stops.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberMuted,
                        selectedLabelColor = AmberBright,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            OsmRouteMap(
                places = trip.places,
                scheduledDays = schedule,
                selectedDayFilter = selectedDay,
                reelSources = trip.reelSources,
                onFixPlace = onFixPlace
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItineraryTabContent(
    trip: Trip,
    schedule: List<ScheduledDay>,
    feedbackState: FeedbackUiState,
    onChangeDays: (Int) -> Unit,
    onChangeStartTime: (Int) -> Unit,
    onRecluster: () -> Unit,
    onMoveStopInDay: (String, Int) -> Unit,
    onMoveStopToDay: (String, Int) -> Unit,
    onViewDayOnMap: (Int) -> Unit,
    onSubmitFeedback: (rating: String, comment: String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Controls card
        item {
            GlassCard(elevated = true, contentPadding = PaddingValues(16.dp)) {
                UppercaseLabel("Schedule Settings")
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trip length", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (trip.daysCount > 1) onChangeDays(trip.daysCount - 1) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Fewer days", tint = AmberBright)
                        }
                        Text(
                            text = "${trip.daysCount} ${if (trip.daysCount == 1) "day" else "days"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { if (trip.daysCount < 10) onChangeDays(trip.daysCount + 1) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "More days", tint = AmberBright)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Start each day at", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                val startOptions = listOf(480 to "8:00 AM", 540 to "9:00 AM", 600 to "10:00 AM", 660 to "11:00 AM")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    startOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = trip.startTimeMinutes == mins,
                            onClick = { onChangeStartTime(mins) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberMuted,
                                selectedLabelColor = AmberBright,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRecluster,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("recluster_button")
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = AmberBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-cluster stops by proximity", color = TextPrimary)
                }
            }
        }

        schedule.forEach { day ->
            item(key = "day_header_${day.dayIndex}") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        UppercaseLabel("Day ${day.dayIndex + 1}")
                        Text(
                            text = if (day.stops.isEmpty()) "Open day"
                            else "${day.stops.size} stops · ~%.1f km".format(day.totalDistanceKm),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (day.stops.isNotEmpty()) {
                        TextButton(
                            onClick = { onViewDayOnMap(day.dayIndex) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("See Day ${day.dayIndex + 1} route", color = AmberBright)
                        }
                    }
                }
            }

            if (day.stops.isEmpty()) {
                item(key = "empty_day_${day.dayIndex}") {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Text(
                            text = "No stops assigned to Day ${day.dayIndex + 1}. Move a stop from another day or tap Auto-cluster.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(day.stops, key = { "stop_${day.dayIndex}_${it.place.id}" }) { stop ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stop.travelFromPrevMinutes != null && stop.distanceFromPrevKm != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 14.dp)
                            ) {
                                Icon(
                                    imageVector = if (stop.isWalkingLeg) Icons.AutoMirrored.Outlined.DirectionsWalk
                                    else Icons.Outlined.DirectionsCar,
                                    contentDescription = null,
                                    tint = AmberBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "~${stop.travelFromPrevMinutes} min ${if (stop.isWalkingLeg) "walk" else "drive"} (%.1f km)".format(stop.distanceFromPrevKm),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        ItineraryStopCard(
                            stop = stop,
                            totalDays = trip.daysCount,
                            canMoveUp = stop.stopNumber > 1,
                            canMoveDown = stop.stopNumber < day.stops.size,
                            onMoveUp = { onMoveStopInDay(stop.place.id, -1) },
                            onMoveDown = { onMoveStopInDay(stop.place.id, +1) },
                            onMoveToDay = { targetDay -> onMoveStopToDay(stop.place.id, targetDay) },
                            onOpenMaps = { openInGoogleMaps(context, stop.place) }
                        )
                    }
                }
            }
        }

        // Feedback card at the bottom of the itinerary
        item {
            FeedbackCard(
                feedbackState = feedbackState,
                onSubmit = onSubmitFeedback
            )
        }
    }
}

@Composable
private fun ItineraryStopCard(
    stop: ScheduledStop,
    totalDays: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onOpenMaps: () -> Unit
) {
    var showMoveDayMenu by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryPill(
                "#${stop.stopNumber} · ${ItineraryPlanner.formatMinutes(stop.arrivalMinutes)}–${ItineraryPlanner.formatMinutes(stop.departureMinutes)}"
            )
            Text(
                text = "${stop.place.durationMinutes} min · ${stop.place.categoryEnum.label}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stop.place.name,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (stop.place.whyMentioned.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stop.place.whyMentioned,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = "Move stop earlier",
                        tint = if (canMoveUp) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = "Move stop later",
                        tint = if (canMoveDown) TextPrimary else TextSecondary.copy(alpha = 0.3f)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMoveDayMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                            contentDescription = "Move stop to another day",
                            tint = AmberBright
                        )
                    }
                    DropdownMenu(
                        expanded = showMoveDayMenu,
                        onDismissRequest = { showMoveDayMenu = false }
                    ) {
                        (0 until totalDays).forEach { targetDay ->
                            if (targetDay != stop.place.dayIndex) {
                                DropdownMenuItem(
                                    text = { Text("Move to Day ${targetDay + 1}") },
                                    onClick = {
                                        showMoveDayMenu = false
                                        onMoveToDay(targetDay)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onOpenMaps,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = AmberBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Maps", color = AmberBright)
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    feedbackState: FeedbackUiState,
    onSubmit: (rating: String, comment: String) -> Unit
) {
    var selectedRating by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }

    GlassCard(
        elevated = true,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        UppercaseLabel("Quick Accuracy Check")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Did this match what the Reel showed?",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))

        if (feedbackState is FeedbackUiState.Submitted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = ConfidenceHigh)
                Text(
                    text = "Logged. Thanks for helping improve WayReel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConfidenceHigh
                )
            }
        } else {
            val options = listOf("yes" to "Yes", "mostly" to "Mostly", "no" to "No")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedRating == value,
                        onClick = { selectedRating = value },
                        label = { Text(label) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("feedback_$value"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberMuted,
                            selectedLabelColor = AmberBright,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Optional: missed a spot, wrong city, etc.") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberBright,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("feedback_comment_input")
            )

            if (feedbackState is FeedbackUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(feedbackState.message, style = MaterialTheme.typography.bodySmall, color = ErrorCoral)
            }

            Spacer(Modifier.height(12.dp))
            AmberGradientButton(
                text = if (feedbackState is FeedbackUiState.Submitting) "Saving..." else "Submit feedback",
                enabled = selectedRating != null && feedbackState !is FeedbackUiState.Submitting,
                onClick = { selectedRating?.let { onSubmit(it, comment) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_feedback_button")
            )
        }
    }
}
