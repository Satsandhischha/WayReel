package com.example

import com.example.data.ItineraryPlanner
import com.example.model.GeocodeStatus
import com.example.model.PlaceCategory
import com.example.model.TripPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItineraryPlannerTest {

    private fun resolvedPlace(
        id: String,
        name: String,
        lat: Double,
        lng: Double,
        category: PlaceCategory = PlaceCategory.CAFE
    ) = TripPlace(
        id = id,
        name = name,
        city = "Tokyo",
        country = "Japan",
        category = category.wireName,
        whyMentioned = "Test stop",
        confidence = "high",
        lat = lat,
        lng = lng,
        geocodeStatus = GeocodeStatus.RESOLVED.wireName,
        durationMinutes = category.defaultDurationMinutes
    )

    @Test
    fun `autoClusterAndOrder groups stops across days and orders by proximity`() {
        val places = listOf(
            resolvedPlace("p1", "Omotesando Coffee", 35.6655, 139.7107, PlaceCategory.CAFE),
            resolvedPlace("p2", "Meiji Shrine", 35.6764, 139.6993, PlaceCategory.LANDMARK),
            resolvedPlace("p3", "Shibuya Sky", 35.6585, 139.7022, PlaceCategory.VIEWPOINT),
            resolvedPlace("p4", "Senso-ji", 35.7148, 139.7967, PlaceCategory.LANDMARK),
            resolvedPlace("p5", "Ueno Park", 35.7140, 139.7741, PlaceCategory.PARK)
        )

        val clustered = ItineraryPlanner.autoClusterAndOrder(places, daysCount = 2)
        val schedule = ItineraryPlanner.buildSchedule(clustered, daysCount = 2, startTimeMinutes = 540)

        assertEquals(2, schedule.size)
        assertTrue(schedule.all { it.stops.isNotEmpty() })
        assertEquals(5, schedule.sumOf { it.stops.size })

        val day1First = schedule.first().stops.first()
        assertEquals(540, day1First.arrivalMinutes)
        assertEquals(540 + day1First.place.durationMinutes, day1First.departureMinutes)
    }

    @Test
    fun `moveStopInDay and moveStopToDay update order cleanly`() {
        val places = listOf(
            resolvedPlace("p1", "Stop 1", 35.6655, 139.7107).copy(dayIndex = 0, orderInDay = 0),
            resolvedPlace("p2", "Stop 2", 35.6764, 139.6993).copy(dayIndex = 0, orderInDay = 1),
            resolvedPlace("p3", "Stop 3", 35.7148, 139.7967).copy(dayIndex = 1, orderInDay = 0)
        )

        val swapped = ItineraryPlanner.moveStopInDay(places, "p2", -1)
        assertEquals(0, swapped.first { it.id == "p2" }.orderInDay)
        assertEquals(1, swapped.first { it.id == "p1" }.orderInDay)

        val moved = ItineraryPlanner.moveStopToDay(swapped, "p1", targetDayIndex = 1)
        assertEquals(1, moved.first { it.id == "p1" }.dayIndex)
        assertEquals(1, moved.first { it.id == "p1" }.orderInDay)
    }
}
