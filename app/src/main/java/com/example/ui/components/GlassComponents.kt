package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PlaceConfidence
import com.example.model.SampleCaption
import com.example.model.SampleCaptions
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberGradient
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.ConfidenceHigh
import com.example.ui.theme.ConfidenceLow
import com.example.ui.theme.ConfidenceMedium
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassFill
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyIndigoGradient
import com.example.ui.theme.NavyMid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NavyGradientScaffoldBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyIndigoGradient),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    borderColor: Color = GlassBorder,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .border(1.dp, borderColor, shape),
        shape = shape,
        color = if (elevated) GlassFillElevated else GlassFill,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun AmberGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 52.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = NavyDeep,
            disabledContainerColor = Color(0x33FFFFFF),
            disabledContentColor = TextSecondary
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(
                    brush = if (enabled) AmberGradient else androidx.compose.ui.graphics.SolidColor(Color(0x33FFFFFF)),
                    shape = shape
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (enabled) NavyDeep else TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) NavyDeep else TextSecondary
                )
            }
        }
    }
}

@Composable
fun UppercaseLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AmberBright
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
    )
}

@Composable
fun ConfidenceBadge(confidence: PlaceConfidence) {
    val (bg, fg) = when (confidence) {
        PlaceConfidence.HIGH -> Color(0x2434D399) to ConfidenceHigh
        PlaceConfidence.MEDIUM -> Color(0x24FFC15A) to ConfidenceMedium
        PlaceConfidence.LOW -> Color(0x24FB7185) to ConfidenceLow
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))
    ) {
        Text(
            text = "${confidence.label} conf",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CategoryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = AmberMuted,
        border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.4f))
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AmberBright,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UppercaseLabel("Transparency & Limits")
            Text(
                text = "How this works and its limits",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "WayReel turns travel Reels into editable, mapped day plans without scraping Instagram or asking for your Instagram login.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            LimitRow(
                icon = Icons.Outlined.Lock,
                title = "1. Instagram access ladder",
                body = "We try Instagram's official oEmbed endpoint first. Most Reel links now require a Meta app token, so when Instagram returns nothing, you can paste the caption or upload a screen recording/audio clip."
            )
            LimitRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "2. Place extraction accuracy",
                body = "Gemini reads captions or transcribes clips under 18 MB and flags High, Medium, or Low confidence. Fast voiceovers or text-free montages can still miss spots—you can edit, fix, or remove any stop."
            )
            LimitRow(
                icon = Icons.Outlined.Translate,
                title = "3. English first",
                body = "Best tested on English captions. Non-Latin scripts, heavy slang, or mixed-language nicknames may lower OpenStreetMap geocoding hit rates unless city and country names are clear."
            )
            LimitRow(
                icon = Icons.Outlined.Map,
                title = "4. Routing & travel gap estimates",
                body = "Days are clustered by geographic proximity and ordered by shortest loop. Walk (<1.8 km) and drive gaps use straight-line distance × 1.35—they are rough planning estimates, not live traffic."
            )
            LimitRow(
                icon = Icons.Outlined.CloudOff,
                title = "5. Saved & offline-ready",
                body = "Your trips, stops, and feedback sync via Firestore and cache locally on your phone, along with viewed OpenStreetMap tiles, so your itinerary works without signal."
            )
            Spacer(Modifier.height(4.dp))
            AmberGradientButton(
                text = "Got it",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("close_how_it_works_button")
            )
        }
    }
}

@Composable
private fun LimitRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AmberMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AmberBright,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleCaptionsSheet(
    onSelectSample: (SampleCaption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            UppercaseLabel("Test Without Instagram")
            Text(
                text = "Try a sample Reel caption",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Pick any realistic caption below to test extraction, geocoding, mapping, and day clustering.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            SampleCaptions.samples.forEach { sample ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSample(sample) }
                        .testTag("sample_caption_${sample.id}"),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sample.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = sample.region,
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberBright
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Use sample",
                            tint = AmberBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sample.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 3
                    )
                }
            }
        }
    }
}
