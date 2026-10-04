package com.tamilscripture.core.data.content

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatBucketTest {
    private val book = listOf(1, 2, 3, 4, 5, 6, 7, 8)

    @Test fun quartilesAsTheWebsite() {
        assertEquals(0, HeatRepository.bucket(null, book))
        assertEquals(1, HeatRepository.bucket(1, book))
        assertEquals(2, HeatRepository.bucket(3, book))
        assertEquals(3, HeatRepository.bucket(5, book))
        assertEquals(4, HeatRepository.bucket(8, book))
        assertEquals(0, HeatRepository.bucket(3, emptyList()))
    }
}
