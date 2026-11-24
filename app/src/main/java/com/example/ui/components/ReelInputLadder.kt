package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.SampleCaption
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderActive
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.InputLadderState

@Composable
fun ReelInputLadder(
    ladderState: InputLadderState,
    initialLinkText: String = "",
    onSubmitLink: (String) -> Unit,
    onSubmitCaption: (caption: String, attachedUrl: String, label: String) -> Unit,
    onSubmitMedia: (uri: Uri, mimeType: String, displayName: String, attachedUrl: String) -> Unit,
    onOpenSamples: () -> Unit,
    selectedSample: SampleCaption? = null,
    onConsumedSample: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var linkText by rememberSaveable { mutableStateOf(initialLinkText) }
    var captionText by rememberSaveable { mutableStateOf("") }
    var showFallbackInputs by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialLinkText) {
        if (initialLinkText.isNotBlank() && initialLinkText != linkText) {
            linkText = initialLinkText
        }
    }

    LaunchedEffect(ladderState) {
        if (ladderState is InputLadderState.LinkFallbackPrompt) {
            showFallbackInputs = true
        }
    }

    LaunchedEffect(selectedSample) {
        if (selectedSample != null) {
            captionText = selectedSample.caption
            showFallbackInputs = true
            onConsumedSample()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            val name = queryFileName(context, uri)
            val attachedUrl = (ladderState as? InputLadderState.LinkFallbackPrompt)?.attachedUrl
                ?: linkText.trim()
            onSubmitMedia(uri, mimeType, name, attachedUrl)
        }
    }

    val isBusy = ladderState is InputLadderState.CheckingLink ||
        ladderState is InputLadderState.TranscribingMedia ||
        ladderState is InputLadderState.ExtractingPlaces ||
        ladderState is InputLadderState.GeocodingPlaces

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        elevated = true,
        borderColor = if (showFallbackInputs) GlassBorderActive else GlassBorder
    ) {
        UppercaseLabel("Step 1 · Paste a Reel link")
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = linkText,
            onValueChange = { linkText = it },
            placeholder = {
                Text(
                    "https://www.instagram.com/reel/...",
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = AmberBright
                )
            },
            singleLine = true,
            enabled = !isBusy,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reel_link_input")
        )

        Spacer(Modifier.height(12.dp))

        AmberGradientButton(
            text = when (ladderState) {
                InputLadderState.CheckingLink -> "Checking Reel link..."
                InputLadderState.TranscribingMedia -> "Transcribing recording..."
                InputLadderState.ExtractingPlaces -> "Extracting places..."
                InputLadderState.GeocodingPlaces -> "Mapping stops..."
                else -> "Turn Reel into itinerary"
            },
            icon = if (isBusy) null else Icons.Outlined.AutoAwesome,
            enabled = !isBusy && linkText.isNotBlank(),
            onClick = { onSubmitLink(linkText.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extract_link_button")
        )

        if (isBusy) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AmberBright,
                    strokeWidth = 2.dp
                )
                Text(
                    text = when (ladderState) {
                        InputLadderState.CheckingLink -> "Trying Instagram's official oEmbed endpoint..."
                        InputLadderState.TranscribingMedia -> "Listening to your clip & reading on-screen places..."
                        InputLadderState.ExtractingPlaces -> "Identifying cafes, landmarks & neighborhoods..."
                        InputLadderState.GeocodingPlaces -> "Resolving coordinates via OpenStreetMap..."
                        InputLadderState.Idle,
                        is InputLadderState.LinkFallbackPrompt,
                        is InputLadderState.Error -> "Working..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Plain-language error or fallback banner — never a dead end
        if (ladderState is InputLadderState.LinkFallbackPrompt) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AmberMuted,
                border = BorderStroke(1.dp, AmberBright.copy(alpha = 0.45f))
            ) {
                Text(
                    text = ladderState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else if (ladderState is InputLadderState.Error) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x24FB7185),
                border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.55f))
            ) {
                Text(
                    text = ladderState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showFallbackInputs = !showFallbackInputs },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("toggle_caption_fallback_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = AmberBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showFallbackInputs) "Hide caption & video" else "Paste caption or upload clip",
                    color = AmberBright,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            TextButton(
                onClick = onOpenSamples,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("try_sample_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Samples",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AnimatedVisibility(visible = showFallbackInputs) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UppercaseLabel("Step 2 · Paste the caption or description")
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = {
                        Text(
                            "Paste the Instagram Reel caption, pinned comment, or travel notes here...",
                            color = TextMuted
                        )
                    },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_input")
                )

                AmberGradientButton(
                    text = "Extract from caption",
                    icon = Icons.Outlined.AutoAwesome,
                    enabled = !isBusy && captionText.isNotBlank(),
                    onClick = {
                        val attachedUrl = (ladderState as? InputLadderState.LinkFallbackPrompt)?.attachedUrl
                            ?: linkText.trim()
                        onSubmitCaption(captionText, attachedUrl, "")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_caption_button")
                )

                UppercaseLabel("Step 3 · Or upload screen recording / audio")
                OutlinedButton(
                    onClick = { mediaPicker.launch(arrayOf("video/*", "audio/*")) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("upload_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoFile,
                        contentDescription = null,
                        tint = AmberBright
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Pick screen recording or audio (<18 MB)",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AmberBright,
    unfocusedBorderColor = GlassBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AmberBright,
    focusedContainerColor = NavyDeep.copy(alpha = 0.45f),
    unfocusedContainerColor = NavyDeep.copy(alpha = 0.35f)
)

private fun queryFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "Reel clip"
        } ?: "Reel clip"
    } catch (_: Exception) {
        "Reel clip"
    }
}
