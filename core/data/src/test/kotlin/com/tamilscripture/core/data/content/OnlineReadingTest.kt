package com.tamilscripture.core.data.content

import androidx.work.testing.WorkManagerTestInitHelper
import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.net.Bootstrap
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.testing.FakeCdn
import com.tamilscripture.core.data.testing.FixturePacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** M1-28 (design §15.4): reading with no packs, from the cache with the server gone, and after a new build. */
@RunWith(RobolectricTestRunner::class)
class OnlineReadingTest {
    private val context = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cdn = FakeCdn()
    private val cacheDir = File(context.filesDir, "online_cache_test")
    private val build1 = FixturePacks.manifest.build

    private fun manifest(build: String): String {
        val o = json.parseToJsonElement(FixturePacks.resource("manifest.json")).jsonObject
        return kotlinx.serialization.json.JsonObject(o + ("build" to JsonPrimitive(build))).toString()
    }

    /** A fresh app process: new repositories over the same cache directory. */
    private fun app(): ContentRepository {
        val origins = OriginResolver(Bootstrap(origins = mapOf("content" to listOf(cdn.base), "api" to listOf(cdn.base), "packs" to listOf(cdn.base))))
        val http = Http(origins)
        return ContentRepository(http, OnlineCache(cacheDir), PackRepository(context, http, json, scope), json, scope)
    }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        cacheDir.deleteRecursively()
        cdn.start()
        cdn.put("content/manifest.json", manifest(build1))
        cdn.put("content/$build1/IRVTAM/JHN/3.json", FixturePacks.resource("IRVTAM/JHN/3.json"))
    }

    @After fun tearDown() { cdn.stop(); cacheDir.deleteRecursively() }

    @Test fun noPacksReadsOnlineThenFromTheCacheWithTheServerGone() = runBlocking {
        val first = app()
        first.refreshManifest()
        val online = first.chapter("IRVTAM", "JHN", 3).toList()
        assertEquals(ContentSource.Online, online.last().source)

        cdn.stop()
        val again = app()
        val cached = again.chapter("IRVTAM", "JHN", 3).toList()
        assertEquals(listOf(ContentSource.Cache), cached.map { it.source })
        assertEquals(online.last().value, cached.single().value)
    }

    @Test fun aNewBuildRevalidatesTheCachedChapter() = runBlocking {
        val first = app()
        first.refreshManifest()
        first.chapter("IRVTAM", "JHN", 3).toList()

        // The website publishes a new build; the chapter's text changed in it.
        val build2 = "next$build1"
        val changed = FixturePacks.resource("IRVTAM/JHN/3.json").replace("\"build\":\"$build1\"", "\"build\":\"$build2\"")
        cdn.put("content/manifest.json", manifest(build2))
        cdn.put("content/$build2/IRVTAM/JHN/3.json", changed)

        val later = app()
        later.refreshManifest()
        val emitted = later.chapter("IRVTAM", "JHN", 3).toList()
        // The cached copy paints at once (marked stale), then the new build's copy follows.
        assertEquals(listOf(ContentSource.Cache, ContentSource.Online), emitted.map { it.source })
        assertTrue(emitted.first().stale)
        assertTrue(cdn.requests.contains("content/$build2/IRVTAM/JHN/3.json"))
    }
}
