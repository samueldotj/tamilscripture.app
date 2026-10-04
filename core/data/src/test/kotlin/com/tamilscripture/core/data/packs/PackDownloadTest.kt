package com.tamilscripture.core.data.packs

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.tamilscripture.core.data.AppGraph
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.testing.FakeCdn
import com.tamilscripture.core.data.testing.FixturePacks
import com.tamilscripture.core.data.testing.Zstd
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

class TestGraphApp : Application(), GraphHost {
    lateinit var testGraph: AppGraph
    override val graph: AppGraph get() = testGraph
}

/** M1-29: downloads that drop, files that do not match, an install cut short, and updates waiting for Wi-Fi. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestGraphApp::class)
class PackDownloadTest {
    private val app = RuntimeEnvironment.getApplication() as TestGraphApp
    private val cdn = FakeCdn()
    private val json = Json
    private val id = "bible.IRVTAM"
    private lateinit var fixture: File
    private lateinit var raw: ByteArray
    private lateinit var zst: ByteArray

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun catalogue(version: Int, sha256: String = sha(zst)) = Catalogue(
        packs = listOf(
            CatalogueEntry(
                id = id, type = "bible", version = version, path = "packs/$id-$version.zst", size = zst.size.toLong(),
                rawSize = raw.size.toLong(), sha256 = sha256, rawSha256 = sha(raw),
            ),
        ),
    )

    /** The app as it starts: the catalogue it last fetched on disk, a graph over the fake CDN. */
    private fun start(cat: Catalogue) {
        File(app.filesDir, "packs").mkdirs()
        File(app.filesDir, "packs/catalogue.json").writeText(json.encodeToString(Catalogue.serializer(), cat))
        app.testGraph = AppGraph(app, packsBaseOverride = cdn.base)
    }

    private fun work(attempt: Int = 0): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<PackDownloadWorker>(app)
            .setInputData(workDataOf(PackDownloadWorker.ID to id))
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        File(app.filesDir, "packs").deleteRecursively()
        val dir = File(app.cacheDir, "fixtures").apply { mkdirs() }
        fixture = FixturePacks.bible(dir, "IRVTAM", listOf("JHN/3", "PSA/23", "GEN/1"))
        raw = fixture.readBytes()
        zst = Zstd.stored(raw)
        cdn.start()
        cdn.put("packs/$id-1.zst", zst)
        cdn.put("packs/$id-2.zst", zst)
    }

    @After fun tearDown() {
        cdn.stop()
        File(app.filesDir, "packs").deleteRecursively()
    }

    @Test fun downloadsVerifiesAndInstalls() = runBlocking {
        start(catalogue(1))
        assertEquals(ListenableWorker.Result.success(), work())
        assertTrue(app.graph.packs.hasBible("IRVTAM"))
        assertNotNull(app.graph.packs.chapterBody("IRVTAM", "JHN", 3))
        assertTrue(app.graph.packs.store.stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun aDroppedConnectionResumesWhereItStopped() = runBlocking {
        start(catalogue(1))
        cdn.fault("packs/$id-1.zst", FakeCdn.Fault.DropAfter(zst.size / 2))
        assertEquals(ListenableWorker.Result.retry(), work())
        val partial = File(app.graph.packs.store.stagingDir, "$id-1.zst")
        assertTrue("kept ${partial.length()} bytes", partial.length() in 1 until zst.size)
        assertFalse(app.graph.packs.hasBible("IRVTAM"))

        cdn.fault("packs/$id-1.zst", null)
        assertEquals(ListenableWorker.Result.success(), work(attempt = 1))
        assertTrue(app.graph.packs.hasBible("IRVTAM"))
    }

    @Test fun aWrongChecksumRetriesThenGivesUpWithoutInstalling() = runBlocking {
        start(catalogue(1, sha256 = "0".repeat(64)))
        assertEquals(ListenableWorker.Result.retry(), work())
        assertEquals(ListenableWorker.Result.failure(), work(attempt = 3))
        assertFalse(app.graph.packs.hasBible("IRVTAM"))
        assertTrue(app.graph.packs.store.stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun anInstallCutShortLeavesTheOldVersionReadable() = runBlocking {
        start(catalogue(1))
        assertEquals(ListenableWorker.Result.success(), work())
        val v1 = app.graph.packs.store.installed.value.getValue(id)
        // The process dies after version 2 was moved into place, before the index was written.
        fixture.copyTo(File(app.filesDir, "packs/$id/2/pack.sqlite").apply { parentFile?.mkdirs() })
        start(catalogue(2))
        assertEquals(v1.version, app.graph.packs.store.installed.value.getValue(id).version)
        assertNotNull(app.graph.packs.chapterBody("IRVTAM", "PSA", 23))
    }

    @Test fun automaticUpdatesWaitForWifi() = runBlocking {
        start(catalogue(1))
        assertEquals(ListenableWorker.Result.success(), work())
        start(catalogue(2))
        assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<PackUpdateWorker>(app).build().doWork())
        val queued = WorkManager.getInstance(app).getWorkInfosForUniqueWork("pack:$id").get()
        assertEquals(1, queued.size)
        assertEquals(NetworkType.UNMETERED, queued.single().constraints.requiredNetworkType)
        assertTrue(queued.single().constraints.requiresStorageNotLow())
    }
}
