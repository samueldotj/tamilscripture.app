package com.tamilscripture.feature.study

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2B: the atlas's journey chips and the sheet's distance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JourneyGroupsTest {
    private val data = runBlocking { loadAtlas { path -> javaClass.getResource("/entities/$path")?.readText() } }

    @Test fun paulHasHisFourJourneysInOrder() {
        val paul = journeyGroups(data.journeys).first { it.id == "paul" }
        assertEquals(listOf("paul-1", "paul-2", "paul-3", "paul-rome"), paul.journeys.map { it.id })
    }

    @Test fun everyJourneyIsInExactlyOneGroup() {
        val ids = journeyGroups(data.journeys).flatMap { g -> g.journeys.map { it.id } }
        assertEquals(data.journeys.map { it.id }.sorted(), ids.sorted())
    }

    @Test fun paulsSecondJourneyIsThousandsOfKilometres() {
        val j = data.journeys.first { it.id == "paul-2" }
        val km = journeyKm(data.routes[j.id], j.stops)
        assertTrue("$km", km in 2500.0..7000.0)
    }

    @Test fun kilometresRound() {
        assertEquals("≈ 4,500 கி.மீ", kmLabel(4512.0, tamil = true))
        assertEquals("≈ 380 km", kmLabel(377.0, tamil = false))
    }
}
