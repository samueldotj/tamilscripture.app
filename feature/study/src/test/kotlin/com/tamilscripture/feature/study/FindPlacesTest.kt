package com.tamilscripture.feature.study

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M8-5d: place search on the atlas, over the website's places. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FindPlacesTest {
    private val places = runBlocking { loadAtlas { path -> javaClass.getResource("/entities/$path")?.readText() } }.places

    @Test fun englishNamesThatStartWithTheQueryComeFirst() {
        val r = findPlaces(places, "jeru")
        assertEquals("jerusalem", r.first().id)
    }

    @Test fun tamilNamesMatchToo() {
        val r = findPlaces(places, "எருசலே")
        assertEquals("jerusalem", r.first().id)
    }

    @Test fun atMostEightAndNothingForBlank() {
        assertTrue(findPlaces(places, "a").size <= 8)
        assertTrue(findPlaces(places, "  ").isEmpty())
    }
}
