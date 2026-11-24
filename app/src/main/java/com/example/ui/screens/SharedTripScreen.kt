package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ItineraryPlanner
import com.example.data.SharedTripSnapshot
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.CategoryPill
import com.example.ui.components.GlassCard
import com.example.ui.components.NavyGradientScaffoldBackground
import com.example.ui.components.OsmRouteMap
import com.example.ui.components.UppercaseLabel
import com.example.ui.components.openInGoogleMaps
import com.example.ui.theme.AmberBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SharedTripScreen(
    snapshot: SharedTripSnapshot,
    onBack: () -> Unit,
    onSaveCopy: () -> Unit
) {
    val context = LocalContext.current
    val schedule = remember(snapshot) {
        ItineraryPlanner.buildSchedule(
            places = snapshot.places,
            daysCount = snapshot.daysCount,
            startTimeMinutes = snapshot.startTimeMinutes
        )
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            UppercaseLabel("Shared Read-Only Trip")
                            Text(
                                text = snapshot.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                item {
                    AmberGradientButton(
                        text = "Save copy to my trips",
                        icon = Icons.Outlined.BookmarkAdd,
                        onClick = onSaveCopy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_shared_copy_button")
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        OsmRouteMap(
                            places = snapshot.places,
                            scheduledDays = schedule,
                            selectedDayFilter = null
                        )
                    }
                }

                schedule.forEach { day ->
                    if (day.stops.isNotEmpty()) {
                        item {
                            UppercaseLabel("Day ${day.dayIndex + 1} · ${day.stops.size} stops")
                        }
                        items(day.stops, key = { "${day.dayIndex}_${it.place.id}" }) { stop ->
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CategoryPill("#${stop.stopNumber} · ${ItineraryPlanner.formatMinutes(stop.arrivalMinutes)}")
                                    Text(
                                        text = "${stop.place.durationMinutes} min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stop.place.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                if (stop.place.whyMentioned.isNotBlank()) {
                                    Text(
                                        text = stop.place.whyMentioned,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.TextButton(
                                    onClick = { openInGoogleMaps(context, stop.place) }
                                ) {
                                    Text("Open in Google Maps", color = AmberBright)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
