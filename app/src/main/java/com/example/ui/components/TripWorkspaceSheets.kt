package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TripShareCodec
import com.example.model.GeocodeCandidate
import com.example.model.PlaceCategory
import com.example.model.SampleCaption
import com.example.model.ScheduledDay
import com.example.model.Trip
import com.example.model.TripPlace
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyMid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InputLadderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixGeocodeSheet(
    place: TripPlace,
    results: List<GeocodeCandidate>,
    isSearching: Boolean,
    onSearch: (query: String, city: String, country: String) -> Unit,
    onSelectCandidate: (GeocodeCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember(place.id) { mutableStateOf(place.name) }

    LaunchedEffect(place.id) {
        onSearch(place.name, place.city, place.country)
    }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UppercaseLabel("Fix Location Pin")
            Text(
                text = place.name,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            if (place.city.isNotBlank() || place.country.isNotBlank()) {
                Text(
                    text = "Context bias: ${listOf(place.city, place.country).filter { it.isNotBlank() }.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search OpenStreetMap") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberBright,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AmberBright
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fix_geocode_search_input")
            )

            AmberGradientButton(
                text = if (isSearching) "Searching..." else "Search places",
                icon = Icons.Outlined.Search,
                enabled = !isSearching && query.isNotBlank(),
                onClick = { onSearch(query, place.city, place.country) },
                modifier = Modifier.fillMaxWidth()
            )

            if (isSearching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AmberBright,
                        strokeWidth = 2.dp
                    )
                    Text("Checking Photon & Nominatim...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else if (results.isEmpty()) {
                Text(
                    text = "No matches yet. Try adding the neighborhood or city name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                results.forEach { candidate ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCandidate(candidate) },
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = AmberBright
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "%.4f, %.4f".format(candidate.lat, candidate.lng),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditPlaceDialog(
    initialPlace: TripPlace?,
    onSave: (name: String, city: String, country: String, category: PlaceCategory, why: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialPlace?.id) { mutableStateOf(initialPlace?.name.orEmpty()) }
    var city by remember(initialPlace?.id) { mutableStateOf(initialPlace?.city.orEmpty()) }
    var country by remember(initialPlace?.id) { mutableStateOf(initialPlace?.country.orEmpty()) }
    var why by remember(initialPlace?.id) { mutableStateOf(initialPlace?.whyMentioned.orEmpty()) }
    var category by remember(initialPlace?.id) {
        mutableStateOf(initialPlace?.categoryEnum ?: PlaceCategory.CAFE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyMid,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (initialPlace == null) "Add place" else "Edit place",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    label = { Text("Why mentioned") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                UppercaseLabel("Category")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlaceCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberMuted,
                                selectedLabelColor = AmberBright,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, city, country, category, why) }
            ) {
                Text("Save", color = AmberBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReelSheet(
    ladderState: InputLadderState,
    currentReelCount: Int,
    onSubmitLink: (String) -> Unit,
    onSubmitCaption: (String, String, String) -> Unit,
    onSubmitMedia: (Uri, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            UppercaseLabel("Add Another Reel ($currentReelCount / 10)")
            Text(
                text = "Combine multiple Reels into this trip",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            ReelInputLadder(
                ladderState = ladderState,
                onSubmitLink = onSubmitLink,
                onSubmitCaption = onSubmitCaption,
                onSubmitMedia = onSubmitMedia,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTripSheet(
    trip: Trip,
    schedule: List<ScheduledDay>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareUrl = remember(trip) { TripShareCodec.encodeShareUrl(trip) }
    val shareText = remember(trip, schedule) { TripShareCodec.buildShareText(trip, schedule) }
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyMid,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UppercaseLabel("Share Read-Only Trip")
            Text(
                text = trip.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Anyone with this WayReel link can view the mapped pins and day plan without editing your saved trip.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Text(
                    text = shareUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberBright,
                    maxLines = 2
                )
            }

            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("WayReel Link", shareUrl))
                    copied = true
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = AmberBright)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (copied) "Link copied" else "Copy read-only link",
                    color = TextPrimary
                )
            }

            AmberGradientButton(
                text = "Share itinerary & map links",
                icon = Icons.Outlined.Share,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, trip.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share trip"))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
