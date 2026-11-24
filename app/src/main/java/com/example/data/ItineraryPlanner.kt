package com.example.data

import com.example.model.ScheduledDay
import com.example.model.ScheduledStop
import com.example.model.TripPlace
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ItineraryPlanner {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val STREET_DETOUR_FACTOR = 1.35
    private const val WALK_THRESHOLD_KM = 1.8
    private const val WALK_SPEED_KMH = 4.5
    private const val DRIVE_SPEED_KMH = 28.0

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Clusters resolved places into [daysCount] geographic clusters and orders each day
     * by nearest-neighbor + 2-opt shortest route. Unresolved places keep their existing dayIndex.
     */
    fun autoClusterAndOrder(
        places: List<TripPlace>,
        daysCount: Int
    ): List<TripPlace> {
        val safeDays = daysCount.coerceIn(1, 10)
        val resolved = places.filter { it.isResolved }
        val unresolved = places.filter { !it.isResolved }
        if (resolved.isEmpty()) return places

        val actualClusters = min(safeDays, resolved.size)

        // 1. Farthest-first seeding for well-separated day anchors
        val centroids = mutableListOf<Pair<Double, Double>>()
        centroids.add(resolved.first().lat!! to resolved.first().lng!!)
        while (centroids.size < actualClusters) {
            val nextSeed = resolved.maxByOrNull { place ->
                centroids.minOf { (cLat, cLng) ->
                    haversineKm(place.lat!!, place.lng!!, cLat, cLng)
                }
            } ?: break
            centroids.add(nextSeed.lat!! to nextSeed.lng!!)
        }

        // 2. Iterative k-means assignment with gentle capacity balancing
        val maxPerDay = kotlin.math.ceil(resolved.size.toDouble() / actualClusters).toInt() + 1
        var assignments = IntArray(resolved.size) { 0 }

        repeat(6) {
            val counts = IntArray(actualClusters)
            val newAssignments = IntArray(resolved.size)
            // Sort by distance margin so unambiguous points claim their closest cluster first
            val orderedIndices = resolved.indices.sortedByDescending { idx ->
                val p = resolved[idx]
                val dists = centroids.map { (cLat, cLng) -> haversineKm(p.lat!!, p.lng!!, cLat, cLng) }.sorted()
                if (dists.size >= 2) dists[1] - dists[0] else 0.0
            }

            for (idx in orderedIndices) {
                val p = resolved[idx]
                val rankedClusters = centroids.indices.sortedBy { c ->
                    haversineKm(p.lat!!, p.lng!!, centroids[c].first, centroids[c].second)
                }
                val chosen = rankedClusters.firstOrNull { counts[it] < maxPerDay } ?: rankedClusters.first()
                newAssignments[idx] = chosen
                counts[chosen]++
            }
            assignments = newAssignments

            // Recompute centroids
            for (c in 0 until actualClusters) {
                val members = resolved.indices.filter { assignments[it] == c }.map { resolved[it] }
                if (members.isNotEmpty()) {
                    centroids[c] = members.map { it.lat!! }.average() to members.map { it.lng!! }.average()
                }
            }
        }

        // Sort clusters geographically west-to-east / north-to-south for predictable Day 1..N ordering
        val clusterOrder = (0 until actualClusters).sortedBy { centroids[it].second }
        val clusterRemap = clusterOrder.withIndex().associate { (newIdx, oldIdx) -> oldIdx to newIdx }

        val grouped = (0 until actualClusters).associateWith { mutableListOf<TripPlace>() }.toMutableMap()
        resolved.forEachIndexed { idx, place ->
            val remappedDay = clusterRemap[assignments[idx]] ?: 0
            grouped.getOrPut(remappedDay) { mutableListOf() }.add(place)
        }

        val orderedResolved = mutableListOf<TripPlace>()
        for (day in 0 until actualClusters) {
            val dayPlaces = grouped[day].orEmpty()
            val shortest = optimizeRouteOrder(dayPlaces)
            shortest.forEachIndexed { orderIdx, place ->
                orderedResolved.add(
                    place.copy(
                        dayIndex = day,
                        orderInDay = orderIdx
                    )
                )
            }
        }

        return orderedResolved + unresolved
    }

    fun optimizeRouteOrder(stops: List<TripPlace>): List<TripPlace> {
        if (stops.size <= 2) return stops
        val remaining = stops.toMutableList()
        // Start from northernmost/westernmost point
        val start = remaining.minByOrNull { (it.lat ?: 0.0) * -1.0 + (it.lng ?: 0.0) } ?: remaining.first()
        remaining.remove(start)
        val route = mutableListOf(start)

        while (remaining.isNotEmpty()) {
            val current = route.last()
            val next = remaining.minByOrNull { candidate ->
                haversineKm(
                    current.lat ?: 0.0,
                    current.lng ?: 0.0,
                    candidate.lat ?: 0.0,
                    candidate.lng ?: 0.0
                )
            } ?: remaining.first()
            route.add(next)
            remaining.remove(next)
        }

        // 2-opt local improvement
        var improved = true
        var guard = 0
        while (improved && guard < 20) {
            improved = false
            guard++
            for (i in 0 until route.size - 2) {
                for (j in i + 2 until route.size) {
                    if (i == 0 && j == route.size - 1) continue
                    val a = route[i]
                    val b = route[i + 1]
                    val c = route[j]
                    val d = if (j + 1 < route.size) route[j + 1] else null

                    val currentDist = dist(a, b) + (if (d != null) dist(c, d) else 0.0)
                    val swappedDist = dist(a, c) + (if (d != null) dist(b, d) else 0.0)
                    if (swappedDist + 0.01 < currentDist) {
                        route.subList(i + 1, j + 1).reverse()
                        improved = true
                    }
                }
            }
        }
        return route
    }

    private fun dist(a: TripPlace, b: TripPlace): Double {
        if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) return 0.0
        return haversineKm(a.lat, a.lng, b.lat, b.lng)
    }

    fun buildSchedule(
        places: List<TripPlace>,
        daysCount: Int,
        startTimeMinutes: Int
    ): List<ScheduledDay> {
        val safeDays = daysCount.coerceIn(1, 10)
        val resolved = places.filter { it.isResolved }
        val maxDayInPlaces = resolved.maxOfOrNull { it.dayIndex } ?: 0
        val totalDays = max(safeDays, maxDayInPlaces + 1).coerceAtMost(10)

        return (0 until totalDays).map { day ->
            val dayStops = resolved
                .filter { it.dayIndex == day }
                .sortedBy { it.orderInDay }

            var currentMinute = startTimeMinutes
            var totalDist = 0.0
            val scheduledStops = mutableListOf<ScheduledStop>()

            dayStops.forEachIndexed { index, stop ->
                var travelMinutes: Int? = null
                var legDistKm: Double? = null
                var isWalk = true

                if (index > 0) {
                    val prev = dayStops[index - 1]
                    val rawKm = dist(prev, stop)
                    val streetKm = rawKm * STREET_DETOUR_FACTOR
                    totalDist += streetKm
                    legDistKm = streetKm
                    isWalk = streetKm < WALK_THRESHOLD_KM
                    val speed = if (isWalk) WALK_SPEED_KMH else DRIVE_SPEED_KMH
                    val mins = max(5, ((streetKm / speed) * 60.0).roundToInt())
                    travelMinutes = mins
                    currentMinute += mins
                }

                val arrival = currentMinute
                val duration = stop.durationMinutes.coerceIn(15, 240)
                val departure = arrival + duration
                currentMinute = departure

                scheduledStops.add(
                    ScheduledStop(
                        place = stop,
                        stopNumber = index + 1,
                        arrivalMinutes = arrival,
                        departureMinutes = departure,
                        travelFromPrevMinutes = travelMinutes,
                        distanceFromPrevKm = legDistKm,
                        isWalkingLeg = isWalk
                    )
                )
            }

            val totalDayDuration = if (scheduledStops.isEmpty()) 0 else {
                scheduledStops.last().departureMinutes - startTimeMinutes
            }

            ScheduledDay(
                dayIndex = day,
                stops = scheduledStops,
                totalDistanceKm = totalDist,
                totalDurationMinutes = totalDayDuration
            )
        }
    }

    fun moveStopInDay(
        places: List<TripPlace>,
        placeId: String,
        delta: Int
    ): List<TripPlace> {
        val target = places.firstOrNull { it.id == placeId } ?: return places
        val sameDay = places
            .filter { it.isResolved && it.dayIndex == target.dayIndex }
            .sortedBy { it.orderInDay }
            .toMutableList()
        val currentIdx = sameDay.indexOfFirst { it.id == placeId }
        val newIdx = (currentIdx + delta).coerceIn(0, sameDay.lastIndex)
        if (currentIdx == -1 || currentIdx == newIdx) return places

        val item = sameDay.removeAt(currentIdx)
        sameDay.add(newIdx, item)
        val updatedOrder = sameDay.mapIndexed { idx, p -> p.id to p.copy(orderInDay = idx) }.toMap()

        return places.map { updatedOrder[it.id] ?: it }
    }

    fun moveStopToDay(
        places: List<TripPlace>,
        placeId: String,
        targetDayIndex: Int
    ): List<TripPlace> {
        val target = places.firstOrNull { it.id == placeId } ?: return places
        if (target.dayIndex == targetDayIndex) return places

        val oldDay = target.dayIndex
        val nextOrderInTarget = (places
            .filter { it.isResolved && it.dayIndex == targetDayIndex }
            .maxOfOrNull { it.orderInDay } ?: -1) + 1

        val updated = places.map { p ->
            if (p.id == placeId) p.copy(dayIndex = targetDayIndex, orderInDay = nextOrderInTarget)
            else p
        }

        // Compact orders in oldDay
        val compactedOld = updated
            .filter { it.isResolved && it.dayIndex == oldDay }
            .sortedBy { it.orderInDay }
            .mapIndexed { idx, p -> p.id to p.copy(orderInDay = idx) }
            .toMap()

        return updated.map { compactedOld[it.id] ?: it }
    }

    fun formatMinutes(minutesFromMidnight: Int): String {
        val wrapped = ((minutesFromMidnight % 1440) + 1440) % 1440
        val h24 = wrapped / 60
        val m = wrapped % 60
        val amPm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "%d:%02d %s".format(h12, m, amPm)
    }
}
