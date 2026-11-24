package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ReelSource
import com.example.model.ScheduledDay
import com.example.model.TripPlace
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.net.URLEncoder

@Composable
fun OsmRouteMap(
    places: List<TripPlace>,
    scheduledDays: List<ScheduledDay>,
    selectedDayFilter: Int?, // null = all days
    modifier: Modifier = Modifier,
    emptyHint: String = "Fix unresolved places in the Places tab to pin them on the map.",
    reelSources: List<ReelSource> = emptyList(),
    isBusyOnMap: Boolean = false,
    busyLabel: String? = null,
    onFixPlace: ((TripPlace) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tappedPlace by remember { mutableStateOf<TripPlace?>(null) }
    var tappedNumber by remember { mutableIntStateOf(1) }
    var showLabels by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableDoubleStateOf(12.0) }
    var lastCoordsSignature by remember { mutableStateOf("") }

    // Configure osmdroid cache strictly in app cacheDir (zero storage permissions needed)
    remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = "WayReelAndroid/1.0 (${context.packageName})"
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        true
    }

    val dynamicOverlayManager = remember(context) { OsmDynamicOverlayManager(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setUseDataConnection(true)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            // Subtle dark-navy cartographic filter so OpenStreetMap integrates into the theme
            val matrix = ColorMatrix(
                floatArrayOf(
                    -0.82f, 0f, 0f, 0f, 232f,
                    0f, -0.80f, 0f, 0f, 235f,
                    0f, 0f, -0.68f, 0f, 245f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val mapListener = DelayedMapListener(
            object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    val z = event?.zoomLevel ?: mapView.zoomLevelDouble
                    if (kotlin.math.abs(z - currentZoom) >= 0.25) {
                        currentZoom = z
                    }
                    return false
                }
            },
            100L
        )
        mapView.addMapListener(mapListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(mapListener)
            dynamicOverlayManager.clearAll(mapView)
            mapView.onDetach()
        }
    }

    val displayedStops: List<Pair<Int, TripPlace>> = remember(places, scheduledDays, selectedDayFilter) {
        if (selectedDayFilter != null) {
            val day = scheduledDays.firstOrNull { it.dayIndex == selectedDayFilter }
            day?.stops?.map { it.stopNumber to it.place }.orEmpty()
        } else {
            val ordered = scheduledDays.flatMap { it.stops.map { s -> s.place } }
            val placeIds = places.map { it.id }.toSet()
            val filteredOrdered = ordered.filter { it.id in placeIds && it.isResolved }
            val fallback = if (filteredOrdered.isNotEmpty()) filteredOrdered else places.filter { it.isResolved }
            fallback.mapIndexed { idx, p -> (idx + 1) to p }
        }
    }

    val resolvedGeoPoints = remember(displayedStops) {
        displayedStops.mapNotNull { (_, p) ->
            val lat = p.lat
            val lng = p.lng
            if (lat != null && lng != null) GeoPoint(lat, lng) else null
        }
    }

    LaunchedEffect(displayedStops) {
        val tappedId = tappedPlace?.id
        if (tappedId != null) {
            val updatedEntry = displayedStops.firstOrNull { it.second.id == tappedId }
            if (updatedEntry == null) {
                tappedPlace = null
            } else {
                tappedPlace = updatedEntry.second
                tappedNumber = updatedEntry.first
            }
        }
    }

    fun focusStop(number: Int, place: TripPlace) {
        val lat = place.lat ?: return
        val lng = place.lng ?: return
        val gp = GeoPoint(lat, lng)
        tappedPlace = place
        tappedNumber = number
        val targetZoom = maxOf(mapView.zoomLevelDouble, 14.5)
        mapView.controller.animateTo(gp, targetZoom, 450L)
        currentZoom = targetZoom
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                dynamicOverlayManager.syncOverlays(
                    mapView = mv,
                    scheduledDays = scheduledDays,
                    selectedDayFilter = selectedDayFilter,
                    displayedStops = displayedStops,
                    selectedPlaceId = tappedPlace?.id,
                    showLabels = showLabels,
                    zoomLevel = currentZoom,
                    onTapSingle = { place, num, gp ->
                        tappedPlace = place
                        tappedNumber = num
                        val nextZoom = maxOf(mv.zoomLevelDouble, 14.2)
                        mv.controller.animateTo(gp, nextZoom, 400L)
                        currentZoom = nextZoom
                    },
                    onTapCluster = { cluster ->
                        tappedPlace = null
                        val clusterPoints = cluster.items.map { it.point }
                        val allSame = clusterPoints.all {
                            kotlin.math.abs(it.latitude - clusterPoints.first().latitude) < 0.0002 &&
                                kotlin.math.abs(it.longitude - clusterPoints.first().longitude) < 0.0002
                        }
                        if (allSame) {
                            val nextZoom = minOf(18.0, mv.zoomLevelDouble + 2.5)
                            mv.controller.animateTo(cluster.center, nextZoom, 400L)
                            currentZoom = nextZoom
                        } else {
                            OsmDynamicOverlayManager.fitPoints(mv, clusterPoints, animate = true)
                        }
                    }
                )

                val coordsSignature = resolvedGeoPoints.joinToString(";") {
                    "%.5f,%.5f".format(it.latitude, it.longitude)
                }
                if (coordsSignature != lastCoordsSignature) {
                    lastCoordsSignature = coordsSignature
                    OsmDynamicOverlayManager.fitPoints(mv, resolvedGeoPoints, animate = false)
                }
                mv.invalidate()
            }
        )

        // Top floating status + dynamic map controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = NavyDeep.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBusyOnMap) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = AmberBright,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = AmberBright,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = if (isBusyOnMap && !busyLabel.isNullOrBlank()) {
                            busyLabel
                        } else {
                            "${displayedStops.size} dynamic pin${if (displayedStops.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }

            if (displayedStops.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (showLabels) AmberMuted else NavyDeep.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, if (showLabels) AmberBright else GlassBorder)
                    ) {
                        IconButton(
                            onClick = { showLabels = !showLabels },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_map_labels_button")
                        ) {
                            Icon(
                                imageVector = if (showLabels) Icons.Filled.Label else Icons.Outlined.Label,
                                contentDescription = if (showLabels) "Hide pin labels" else "Show pin labels",
                                tint = if (showLabels) AmberBright else TextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = NavyDeep.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        IconButton(
                            onClick = {
                                tappedPlace = null
                                OsmDynamicOverlayManager.fitPoints(mapView, resolvedGeoPoints, animate = true)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fit_map_pins_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CenterFocusStrong,
                                contentDescription = "Fit all pins",
                                tint = AmberBright
                            )
                        }
                    }
                }
            }
        }

        if (displayedStops.isEmpty()) {
            GlassCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                elevated = true
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = AmberBright,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No mapped pins yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Tapped dynamic pin detail card with Prev/Next tour + Reel source + Google Maps
        AnimatedVisibility(
            visible = tappedPlace != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
        ) {
            val place = tappedPlace
            if (place != null) {
                val activeIndex = displayedStops.indexOfFirst { it.second.id == place.id }
                val sourceLabel = remember(place.sourceReelId, reelSources) {
                    reelSources.firstOrNull { it.id == place.sourceReelId }?.label
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = true,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CategoryPill("#$tappedNumber · ${place.categoryEnum.label}")
                                ConfidenceBadge(place.confidenceEnum)
                            }
                            if (!sourceLabel.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "From $sourceLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            val locationLine = listOf(place.city, place.country)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                            if (locationLine.isNotBlank()) {
                                Text(
                                    text = locationLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { tappedPlace = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss pin card",
                                tint = TextSecondary
                            )
                        }
                    }

                    if (place.whyMentioned.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = place.whyMentioned,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (displayedStops.size > 1 && activeIndex >= 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val prevIdx = if (activeIndex > 0) activeIndex - 1 else displayedStops.lastIndex
                                    val (num, target) = displayedStops[prevIdx]
                                    focusStop(num, target)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("map_prev_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.NavigateBefore,
                                    contentDescription = "Previous location",
                                    tint = AmberBright
                                )
                            }

                            Text(
                                text = "Location ${activeIndex + 1} of ${displayedStops.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )

                            IconButton(
                                onClick = {
                                    val nextIdx = (activeIndex + 1) % displayedStops.size
                                    val (num, target) = displayedStops[nextIdx]
                                    focusStop(num, target)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("map_next_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = "Next location",
                                    tint = AmberBright
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onFixPlace != null) {
                            OutlinedButton(
                                onClick = { onFixPlace(place) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, GlassBorder),
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .testTag("map_fix_selected_pin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EditLocationAlt,
                                    contentDescription = null,
                                    tint = AmberBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust", color = TextPrimary)
                            }
                        }

                        AmberGradientButton(
                            text = "Open in Google Maps",
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            onClick = { openInGoogleMaps(context, place) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_google_maps_button")
                        )
                    }
                }
            }
        }
    }
}

fun openInGoogleMaps(context: Context, place: TripPlace) {
    val lat = place.lat
    val lng = place.lng
    val encodedName = URLEncoder.encode(
        listOf(place.name, place.city).filter { it.isNotBlank() }.joinToString(", "),
        "UTF-8"
    )
    val uri = if (lat != null && lng != null) {
        Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedName)")
    } else {
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedName")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackUri = if (lat != null && lng != null) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        } else {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedName")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}
