package com.tamilscripture.core.data.content

import androidx.work.testing.WorkManagerTestInitHelper
import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.testing.FixturePacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

/**
 * M1-27 (NF-6): with the Bible and cross-reference packs installed, reading, cross-references
 * and search never reach for the network. Every request fails as in airplane mode, and is counted.
 */
@RunWith(RobolectricTestRunner::class)
class AirplaneModeTest {
    private val context = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = mutableListOf<String>()
    private lateinit var http: Http
    private lateinit var packs: PackRepository
    private lateinit var content: ContentRepository

    @Before fun setUp() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        http = Http(OriginResolver()).apply { guard = { url -> synchronized(calls) { calls += url }; throw IOException("airplane mode: $url") } }
        packs = PackRepository(context, http, json, scope)
        val build = File(context.cacheDir, "fixtures").apply { mkdirs() }
        val chapters = listOf("JHN/3", "PSA/23", "GEN/1")
        packs.store.install("bible.IRVTAM", 1, FixturePacks.bible(build, "IRVTAM", chapters))
        packs.store.install("xref", 1, FixturePacks.xref(build, listOf("JHN/3")))
        content = ContentRepository(http, OnlineCache(File(context.filesDir, "online_cache")), packs, json, scope)
    }

    @After fun tearDown() { File(context.filesDir, "packs").deleteRecursively() }

    @Test fun readingADownloadedChapterIsOffline() = runBlocking {
        val loaded = content.chapter("IRVTAM", "JHN", 3).toList()
        assertEquals(1, loaded.size)
        assertEquals(ContentSource.Pack, loaded.single().source)
        assertTrue(loaded.single().value.blocks.isNotEmpty())
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun crossReferencesComeFromTheirPack() = runBlocking {
        val refs = content.crossRefs("JHN", 3)
        assertTrue(refs["JHN.3.16"].orEmpty().isNotEmpty())
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun searchRunsOnTheDevice() = runBlocking {
        val search = SearchRepository(http, packs, json)
        val r = search.search("தேவன்", "IRVTAM", tamil = true)
        assertTrue(r.offline)
        assertTrue("hits: ${r.response.total}", r.response.total >= 5)
        assertTrue(r.perBook.isNotEmpty())
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun aChapterNotDownloadedFailsWithoutWaiting() = runBlocking {
        val failed = runCatching { content.chapter("IRVTAM", "JHN", 4).toList() }
        assertTrue(failed.isFailure)
    }
}
