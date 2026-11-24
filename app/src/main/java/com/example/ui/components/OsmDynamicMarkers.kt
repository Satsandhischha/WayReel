package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import com.example.model.PlaceCategory
import com.example.model.PlaceConfidence
import com.example.model.ScheduledDay
import com.example.model.TripPlace
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.hypot

sealed interface OsmMarkerNode {
    data class SinglePin(
        val number: Int,
        val place: TripPlace,
        val point: GeoPoint
    ) : OsmMarkerNode

    data class ClusterPin(
        val id: String,
        val center: GeoPoint,
        val items: List<SinglePin>
    ) : OsmMarkerNode
}

class OsmDynamicOverlayManager(private val context: Context) {

    private val activeMarkers = mutableMapOf<String, Marker>()
    private val activePolylines = mutableListOf<Polyline>()
    private val bitmapCache = object : LinkedHashMap<String, BitmapDrawable>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitmapDrawable>?): Boolean {
            return size > 120
        }
    }
    private val scratchPointA = Point()
    private val scratchPointB = Point()

    fun syncOverlays(
        mapView: MapView,
        scheduledDays: List<ScheduledDay>,
        selectedDayFilter: Int?,
        displayedStops: List<Pair<Int, TripPlace>>,
        selectedPlaceId: String?,
        showLabels: Boolean,
        zoomLevel: Double,
        onTapSingle: (TripPlace, Int, GeoPoint) -> Unit,
        onTapCluster: (OsmMarkerNode.ClusterPin) -> Unit
    ) {
        // 1. Update route polylines
        activePolylines.forEach { mapView.overlays.remove(it) }
        activePolylines.clear()

        val daysToDraw = if (selectedDayFilter != null) {
            scheduledDays.filter { it.dayIndex == selectedDayFilter }
        } else {
            scheduledDays
        }

        for (day in daysToDraw) {
            val pts = day.stops.mapNotNull { stop ->
                val lat = stop.place.lat
                val lng = stop.place.lng
                if (lat != null && lng != null) GeoPoint(lat, lng) else null
            }
            if (pts.size >= 2) {
                val segments = pts.size - 1
                for (i in 0 until segments) {
                    val ratio = if (segments <= 1) 0.5f else i.toFloat() / (segments - 1).toFloat()
                    val color = interpolateAmberColor(ratio)
                    val line = Polyline(mapView).apply {
                        setPoints(listOf(pts[i], pts[i + 1]))
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }
                    mapView.overlays.add(line)
                    activePolylines.add(line)
                }
            }
        }

        // 2. Compute dynamic marker nodes (single vs zoom-aware cluster)
        val nodes = computeDynamicNodes(
            mapView = mapView,
            stops = displayedStops,
            zoomLevel = zoomLevel,
            selectedPlaceId = selectedPlaceId
        )

        val desiredKeys = mutableSetOf<String>()
        val allowAutoLabels = showLabels || zoomLevel >= 14.5
        val labelEligibleCount = nodes.count { it is OsmMarkerNode.SinglePin } <= 14

        // Add non-selected markers first, selected last so selected draws on top
        val sortedNodes = nodes.sortedBy { node ->
            when (node) {
                is OsmMarkerNode.ClusterPin -> 0
                is OsmMarkerNode.SinglePin -> if (node.place.id == selectedPlaceId) 2 else 1
            }
        }

        sortedNodes.forEach { node ->
            when (node) {
                is OsmMarkerNode.SinglePin -> {
                    val key = "pin_${node.place.id}"
                    desiredKeys.add(key)
                    val isSelected = node.place.id == selectedPlaceId
                    val labelText = if (isSelected || (allowAutoLabels && labelEligibleCount)) {
                        node.place.name.trim().take(18).let {
                            if (node.place.name.trim().length > 18) "$it…" else it
                        }
                    } else {
                        null
                    }
                    val (drawable, anchorV) = getOrCreateSinglePinDrawable(
                        number = node.number,
                        category = node.place.categoryEnum,
                        confidence = node.place.confidenceEnum,
                        isSelected = isSelected,
                        labelText = labelText
                    )
                    val marker = activeMarkers.getOrPut(key) {
                        Marker(mapView).also { mapView.overlays.add(it) }
                    }
                    marker.position = node.point
                    marker.setAnchor(Marker.ANCHOR_CENTER, anchorV)
                    marker.icon = drawable
                    marker.title = node.place.name
                    marker.setOnMarkerClickListener { _, _ ->
                        onTapSingle(node.place, node.number, node.point)
                        true
                    }
                    if (isSelected) {
                        // Re-append so selected marker sits above neighbors
                        mapView.overlays.remove(marker)
                        mapView.overlays.add(marker)
                    }
                }
                is OsmMarkerNode.ClusterPin -> {
                    val key = node.id
                    desiredKeys.add(key)
                    val drawable = getOrCreateClusterDrawable(node.items.size)
                    val marker = activeMarkers.getOrPut(key) {
                        Marker(mapView).also { mapView.overlays.add(it) }
                    }
                    marker.position = node.center
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.icon = drawable
                    marker.title = "${node.items.size} places"
                    marker.setOnMarkerClickListener { _, _ ->
                        onTapCluster(node)
                        true
                    }
                }
            }
        }

        // Remove stale markers no longer present after filtering or deletion
        val toRemove = activeMarkers.keys.filterNot { it in desiredKeys }
        toRemove.forEach { key ->
            activeMarkers.remove(key)?.let { mapView.overlays.remove(it) }
        }
    }

    fun clearAll(mapView: MapView) {
        activePolylines.forEach { mapView.overlays.remove(it) }
        activePolylines.clear()
        activeMarkers.values.forEach { mapView.overlays.remove(it) }
        activeMarkers.clear()
        bitmapCache.clear()
    }

    private fun computeDynamicNodes(
        mapView: MapView,
        stops: List<Pair<Int, TripPlace>>,
        zoomLevel: Double,
        selectedPlaceId: String?
    ): List<OsmMarkerNode> {
        val singles = stops.mapNotNull { (number, place) ->
            val lat = place.lat ?: return@mapNotNull null
            val lng = place.lng ?: return@mapNotNull null
            OsmMarkerNode.SinglePin(number, place, GeoPoint(lat, lng))
        }
        if (singles.size <= 1 || zoomLevel >= 15.2 || mapView.width <= 0 || mapView.height <= 0) {
            return singles
        }

        val density = context.resources.displayMetrics.density
        val clusterRadiusPx = 40f * density
        val used = BooleanArray(singles.size)
        val result = mutableListOf<OsmMarkerNode>()

        for (i in singles.indices) {
            if (used[i]) continue
            val anchor = singles[i]
            // Keep selected marker unclustered so user always sees the active stop
            if (anchor.place.id == selectedPlaceId) {
                used[i] = true
                result.add(anchor)
                continue
            }

            mapView.projection.toPixels(anchor.point, scratchPointA)
            val group = mutableListOf(anchor)
            used[i] = true

            for (j in (i + 1) until singles.size) {
                if (used[j]) continue
                val candidate = singles[j]
                if (candidate.place.id == selectedPlaceId) continue

                mapView.projection.toPixels(candidate.point, scratchPointB)
                val distPx = hypot(
                    (scratchPointA.x - scratchPointB.x).toDouble(),
                    (scratchPointA.y - scratchPointB.y).toDouble()
                )
                if (distPx <= clusterRadiusPx) {
                    used[j] = true
                    group.add(candidate)
                }
            }

            if (group.size == 1) {
                result.add(anchor)
            } else {
                val avgLat = group.map { it.point.latitude }.average()
                val avgLng = group.map { it.point.longitude }.average()
                val clusterId = "cluster_${group.joinToString("_") { it.place.id.take(6) }}"
                result.add(
                    OsmMarkerNode.ClusterPin(
                        id = clusterId,
                        center = GeoPoint(avgLat, avgLng),
                        items = group
                    )
                )
            }
        }
        return result
    }

    private fun getOrCreateSinglePinDrawable(
        number: Int,
        category: PlaceCategory,
        confidence: PlaceConfidence,
        isSelected: Boolean,
        labelText: String?
    ): Pair<BitmapDrawable, Float> {
        val density = context.resources.displayMetrics.density
        val circleDp = if (isSelected) 42f else 34f
        val circlePx = circleDp * density
        val badgeOverhang = 5f * density
        val hasLabel = !labelText.isNullOrBlank()
        val labelGap = if (hasLabel) 4f * density else 0f
        val labelHeight = if (hasLabel) 20f * density else 0f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 10.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val labelWidth = if (hasLabel) {
            (textPaint.measureText(labelText) + 16f * density).coerceAtLeast(circlePx + 12f * density)
        } else {
            0f
        }

        val widthPx = maxOf(circlePx + badgeOverhang * 2f, labelWidth).toInt().coerceAtLeast(1)
        val heightPx = (circlePx + badgeOverhang + labelGap + labelHeight).toInt().coerceAtLeast(1)
        val circleCenterY = badgeOverhang / 2f + circlePx / 2f
        val anchorV = (circleCenterY / heightPx.toFloat()).coerceIn(0.1f, 0.9f)

        val cacheKey = "single_${number}_${category.wireName}_${confidence.wireName}_${isSelected}_${labelText.orEmpty()}"
        bitmapCache[cacheKey]?.let { return it to anchorV }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = widthPx / 2f
        val radius = circlePx / 2f

        // Confidence / selection halo ring
        val ringColor = when {
            isSelected -> android.graphics.Color.WHITE
            confidence == PlaceConfidence.HIGH -> android.graphics.Color.parseColor("#34D399")
            confidence == PlaceConfidence.LOW -> android.graphics.Color.parseColor("#FB7185")
            else -> android.graphics.Color.parseColor("#0B1731")
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, circleCenterY, radius, ringPaint)

        // Amber gradient pin core
        val innerRadius = radius - (if (isSelected) 3f else 2.4f) * density
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                centerX - innerRadius,
                circleCenterY - innerRadius,
                centerX + innerRadius,
                circleCenterY + innerRadius,
                android.graphics.Color.parseColor("#FFC15A"),
                android.graphics.Color.parseColor("#F07A1F"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, circleCenterY, innerRadius, fillPaint)

        // Stop number
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#060B19")
            textSize = (if (number >= 10) 11.5f else 13.5f) * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val numY = circleCenterY - (numPaint.descent() + numPaint.ascent()) / 2f
        canvas.drawText(number.toString(), centerX, numY, numPaint)

        // Category mini-badge in top-right quadrant
        val badgeRadius = 6.5f * density
        val badgeX = centerX + radius * 0.72f
        val badgeY = circleCenterY - radius * 0.72f
        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0B1731")
        }
        canvas.drawCircle(badgeX, badgeY, badgeRadius + 1.2f * density, badgeBg)

        val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = categoryBadgeColor(category)
        }
        canvas.drawCircle(badgeX, badgeY, badgeRadius, badgeFill)

        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#060B19")
            textSize = 7.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val glyphY = badgeY - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText(categoryBadgeGlyph(category), badgeX, glyphY, glyphPaint)

        // Optional floating glass label pill below the marker
        if (hasLabel && labelText != null) {
            val pillTop = circleCenterY + radius + labelGap
            val pillRect = RectF(
                centerX - labelWidth / 2f + 1f * density,
                pillTop,
                centerX + labelWidth / 2f - 1f * density,
                pillTop + labelHeight - 1f * density
            )
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isSelected) {
                    android.graphics.Color.parseColor("#F0060B19")
                } else {
                    android.graphics.Color.parseColor("#D90B1731")
                }
            }
            val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isSelected) {
                    android.graphics.Color.parseColor("#FFC15A")
                } else {
                    android.graphics.Color.parseColor("#66FFC15A")
                }
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
            }
            val corner = 9f * density
            canvas.drawRoundRect(pillRect, corner, corner, pillBg)
            canvas.drawRoundRect(pillRect, corner, corner, pillBorder)

            val labelY = pillRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(labelText, centerX, labelY, textPaint)
        }

        val drawable = BitmapDrawable(context.resources, bitmap)
        bitmapCache[cacheKey] = drawable
        return drawable to anchorV
    }

    private fun getOrCreateClusterDrawable(count: Int): BitmapDrawable {
        val cacheKey = "cluster_$count"
        bitmapCache[cacheKey]?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (42f * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f

        val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFC15A")
        }
        canvas.drawCircle(radius, radius, radius, outerGlow)

        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0B1731")
        }
        canvas.drawCircle(radius, radius, radius - 3f * density, core)

        val innerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#44F07A1F")
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(radius, radius, radius - 6f * density, innerRing)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFC15A")
            textSize = 14f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(count.toString(), radius, textY, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        bitmapCache[cacheKey] = drawable
        return drawable
    }

    companion object {
        fun fitPoints(mapView: MapView, geoPoints: List<GeoPoint>, animate: Boolean) {
            if (geoPoints.isEmpty()) {
                mapView.controller.setZoom(2.5)
                mapView.controller.setCenter(GeoPoint(20.0, 0.0))
                return
            }
            if (geoPoints.size == 1) {
                if (animate) {
                    mapView.controller.animateTo(geoPoints.first(), 14.5, 450L)
                } else {
                    mapView.controller.setZoom(14.5)
                    mapView.controller.setCenter(geoPoints.first())
                }
                return
            }
            val bbox = BoundingBox.fromGeoPointsSafe(geoPoints)
            if (bbox.latNorth == bbox.latSouth && bbox.lonEast == bbox.lonWest) {
                if (animate) {
                    mapView.controller.animateTo(geoPoints.first(), 15.5, 450L)
                } else {
                    mapView.controller.setZoom(15.5)
                    mapView.controller.setCenter(geoPoints.first())
                }
                return
            }
            mapView.post {
                try {
                    mapView.zoomToBoundingBox(bbox.increaseByScale(1.35f), animate)
                } catch (_: Exception) {
                    mapView.controller.setCenter(geoPoints.first())
                    mapView.controller.setZoom(12.0)
                }
            }
        }
    }
}

private fun categoryBadgeColor(category: PlaceCategory): Int = when (category) {
    PlaceCategory.CAFE -> android.graphics.Color.parseColor("#FDE68A")
    PlaceCategory.RESTAURANT, PlaceCategory.BAR -> android.graphics.Color.parseColor("#FCA5A5")
    PlaceCategory.VIEWPOINT, PlaceCategory.PARK -> android.graphics.Color.parseColor("#6EE7B7")
    PlaceCategory.MUSEUM, PlaceCategory.LANDMARK -> android.graphics.Color.parseColor("#93C5FD")
    PlaceCategory.BEACH -> android.graphics.Color.parseColor("#67E8F9")
    PlaceCategory.MARKET, PlaceCategory.NEIGHBORHOOD -> android.graphics.Color.parseColor("#FDBA74")
    PlaceCategory.HOTEL -> android.graphics.Color.parseColor("#C4B5FD")
    PlaceCategory.OTHER -> android.graphics.Color.parseColor("#E2E8F0")
}

private fun categoryBadgeGlyph(category: PlaceCategory): String = when (category) {
    PlaceCategory.CAFE -> "C"
    PlaceCategory.RESTAURANT -> "R"
    PlaceCategory.BAR -> "B"
    PlaceCategory.VIEWPOINT -> "V"
    PlaceCategory.MUSEUM -> "M"
    PlaceCategory.LANDMARK -> "L"
    PlaceCategory.BEACH -> "W"
    PlaceCategory.MARKET -> "K"
    PlaceCategory.NEIGHBORHOOD -> "N"
    PlaceCategory.PARK -> "P"
    PlaceCategory.HOTEL -> "H"
    PlaceCategory.OTHER -> "•"
}

private fun interpolateAmberColor(t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val r = (255 + (240 - 255) * clamped).toInt()
    val g = (193 + (122 - 193) * clamped).toInt()
    val b = (90 + (31 - 90) * clamped).toInt()
    return android.graphics.Color.rgb(r, g, b)
}
