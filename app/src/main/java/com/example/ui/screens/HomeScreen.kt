package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.signOutUser
import com.example.model.SampleCaption
import com.example.model.Trip
import com.example.ui.components.CategoryPill
import com.example.ui.components.GlassCard
import com.example.ui.components.HowItWorksSheet
import com.example.ui.components.NavyGradientScaffoldBackground
import com.example.ui.components.ReelInputLadder
import com.example.ui.components.SampleCaptionsSheet
import com.example.ui.components.UppercaseLabel
import com.example.ui.theme.AmberBright
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyMid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.TripsListUiState

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    incomingSharedText: String?,
    onConsumedIncomingText: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenSharedPreview: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val ladderState by viewModel.ladderState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showHowItWorks by remember { mutableStateOf(false) }
    var showSamples by remember { mutableStateOf(false) }
    var showOpenShareDialog by remember { mutableStateOf(false) }
    var pendingSample by remember { mutableStateOf<SampleCaption?>(null) }
    var prefillLink by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(incomingSharedText) {
        if (!incomingSharedText.isNullOrBlank()) {
            prefillLink = incomingSharedText
            onConsumedIncomingText()
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    NavyGradientScaffoldBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            UppercaseLabel("WayReel")
                            Text(
                                text = "Reel to Itinerary",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextPrimary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showOpenShareDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("open_shared_link_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Open shared trip link",
                                    tint = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { showHowItWorks = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("how_it_works_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "How this works and its limits",
                                    tint = AmberBright
                                )
                            }
                            IconButton(
                                onClick = {
                                    signOutUser(credentialManager, scope)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Sign out",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }

                item {
                    ReelInputLadder(
                        ladderState = ladderState,
                        initialLinkText = prefillLink,
                        onSubmitLink = { link ->
                            viewModel.submitReelLink(
                                rawLink = link,
                                targetExistingTrip = false,
                                onTripReady = onOpenTrip
                            )
                        },
                        onSubmitCaption = { caption, attachedUrl, label ->
                            viewModel.submitCaptionText(
                                captionText = caption,
                                attachedUrl = attachedUrl,
                                customLabel = label,
                                targetExistingTrip = false,
                                onTripReady = onOpenTrip
                            )
                        },
                        onSubmitMedia = { uri: Uri, mimeType: String, name: String, attachedUrl: String ->
                            viewModel.submitMediaFile(
                                contentResolver = context.contentResolver,
                                uri = uri,
                                mimeType = mimeType,
                                displayName = name,
                                attachedUrl = attachedUrl,
                                targetExistingTrip = false,
                                onTripReady = onOpenTrip
                            )
                        },
                        onOpenSamples = { showSamples = true },
                        selectedSample = pendingSample,
                        onConsumedSample = { pendingSample = null }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UppercaseLabel("Saved Trips · Offline Cached")
                        Text(
                            text = "Up to 10 Reels / trip",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                when (val state = tripsState) {
                    TripsListUiState.Loading -> {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = AmberBright,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Loading saved trips...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    is TripsListUiState.Error -> {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorCoral
                                )
                            }
                        }
                    }
                    is TripsListUiState.Success -> {
                        if (state.trips.isEmpty()) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Icon(
                                        imageVector = Icons.Outlined.Map,
                                        contentDescription = null,
                                        tint = AmberBright,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "No trips yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Paste an Instagram Reel link, caption, or tap Samples above to generate your first mapped trip.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            items(state.trips, key = { it.id }) { trip ->
                                SavedTripCard(
                                    trip = trip,
                                    onClick = {
                                        viewModel.openTrip(trip.id)
                                        onOpenTrip(trip.id)
                                    },
                                    onDelete = { viewModel.deleteTrip(trip.id) }
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showHowItWorks) {
        HowItWorksSheet(onDismiss = { showHowItWorks = false })
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

    if (showOpenShareDialog) {
        var shareLinkInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOpenShareDialog = false },
            containerColor = NavyMid,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Open shared trip link",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a wayreel://trip?d=... link shared by a friend.")
                    OutlinedTextField(
                        value = shareLinkInput,
                        onValueChange = { shareLinkInput = it },
                        placeholder = { Text("wayreel://trip?d=...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.openSharedLink(shareLinkInput)) {
                            showOpenShareDialog = false
                            onOpenSharedPreview()
                        }
                    }
                ) {
                    Text("Open", color = AmberBright)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenShareDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SavedTripCard(
    trip: Trip,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val resolvedCount = trip.places.count { it.isResolved }
    val needsFixCount = trip.places.count { !it.isResolved }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("saved_trip_card_${trip.id}"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryPill("${trip.daysCount} days")
                    Text(
                        text = "${trip.reelSources.size} Reel${if (trip.reelSources.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("$resolvedCount mapped")
                        if (needsFixCount > 0) append(" · $needsFixCount needs fix")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete trip",
                        tint = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Open trip",
                    tint = AmberBright
                )
            }
        }
    }
}
