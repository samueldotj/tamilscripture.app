package com.tamilscripture.core.data.stats

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.HttpException
import com.tamilscripture.core.data.net.Origin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One stats event (requirements §5.1). Field names match the server's `track_app_batch` payload. */
@Serializable
data class StatsEvent(
    val id: String,
    val at: String,
    val kind: String,
    val verse: String? = null,
    val version: String? = null,
    val book: String? = null,
    val chapter: Int? = null,
    val action: String? = null,
    val amount: Long? = null,
    val source: String? = null,
    val offline: Boolean? = null,
    val query: String? = null,
)

@Serializable
private data class Batch(
    val install: String,
    val app: String,
    val device: String,
    val window: String,
    val os: String,
    val lang: String,
    val theme: String,
    val events: List<StatsEvent>,
)

@Serializable
private data class Accepted(val accepted: List<String> = emptyList())

/** Context the recorder adds to every batch (device class and window class change at runtime). */
data class StatsContext(
    val appVersion: String,
    val device: String = "phone",
    val window: String = "compact",
    val lang: String = "ta",
    val theme: String = "dark",
)

/**
 * Records events without blocking the caller (ST-1): [record] only offers to a channel.
 * A collector writes batches to a local queue file every 2 s; [StatsSyncWorker] sends them.
 */
class StatsRecorder(
    private val dir: File,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    @Volatile var enabled: Boolean = true
    @Volatile var context: StatsContext = StatsContext(appVersion = "dev")

    private val channel = Channel<StatsEvent>(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val fileLock = Mutex()
    private val queue = File(dir, "pending.jsonl")
    val installId: String by lazy {
        val f = File(dir, "install")
        if (f.exists()) f.readText() else UUID.randomUUID().toString().also { dir.mkdirs(); f.writeText(it) }
    }

    fun start() {
        dir.mkdirs()
        scope.launch(Dispatchers.IO) {
            val pending = ArrayList<StatsEvent>()
            while (true) {
                val first = channel.receive()
                pending += first
                delay(2_000)
                while (true) pending += channel.tryReceive().getOrNull() ?: break
                append(pending)
                pending.clear()
            }
        }
    }

    fun record(
        kind: String,
        verse: String? = null,
        version: String? = null,
        book: String? = null,
        chapter: Int? = null,
        action: String? = null,
        amount: Long? = null,
        source: String? = null,
        offline: Boolean? = null,
        query: String? = null,
    ) {
        if (!enabled) return
        channel.trySend(
            StatsEvent(UUID.randomUUID().toString(), Instant.now().toString(), kind, verse, version, book, chapter, action, amount, source, offline, query),
        )
    }

    private suspend fun append(events: List<StatsEvent>) = fileLock.withLock {
        if (!enabled) return@withLock
        queue.appendText(events.joinToString("") { json.encodeToString(StatsEvent.serializer(), it) + "\n" })
        trim()
    }

    /** ST-4: at most 10,000 pending events or 30 days, oldest dropped. */
    private fun trim() {
        val lines = queue.readLines()
        val cutoff = Instant.now().minusSeconds(30L * 24 * 3600).toString()
        val kept = lines.filter { l -> l.substringAfter("\"at\":\"").substringBefore('"') >= cutoff }.takeLast(10_000)
        if (kept.size != lines.size) queue.writeText(kept.joinToString("") { "$it\n" })
    }

    suspend fun pendingCount(): Int = fileLock.withLock { if (queue.exists()) queue.readLines().size else 0 }

    /** ST-8: switching stats off clears what has not been sent. */
    suspend fun clear() = fileLock.withLock { queue.delete(); Unit }

    /** Sends up to 500 events; deletes only those the server confirms (ST-3). Returns false to retry. */
    suspend fun upload(http: Http, bearer: String?): Boolean = withContext(Dispatchers.IO) {
        val batch = fileLock.withLock {
            if (!queue.exists()) return@withContext true
            queue.readLines().take(500).mapNotNull { runCatching { json.decodeFromString(StatsEvent.serializer(), it) }.getOrNull() }
        }
        if (batch.isEmpty()) return@withContext true
        val c = context
        val body = json.encodeToString(
            Batch.serializer(),
            Batch(installId, c.appVersion, c.device, c.window, "Android ${Build.VERSION.RELEASE}", c.lang, c.theme, batch),
        )
        val accepted = try {
            val res = http.postJson(Origin.Api, "api/t/app", body, bearer)
            runCatching { json.decodeFromString(Accepted.serializer(), res.decodeToString()).accepted.toSet() }
                .getOrDefault(batch.map { it.id }.toSet())
        } catch (e: HttpException) {
            // 404 until the collector is deployed (roadmap M1-21a): keep the queue.
            return@withContext false
        } catch (e: IOException) {
            return@withContext false
        }
        fileLock.withLock {
            val remaining = queue.readLines().filter { l -> accepted.none { l.contains("\"id\":\"$it\"") } }
            queue.writeText(remaining.joinToString("") { "$it\n" })
        }
        true
    }

    companion object {
        private const val PERIODIC = "stats-sync"
        private const val NOW = "stats-sync-now"

        /** ST-2: every 15 minutes with a network, plus an expedited run when the app goes to the background. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StatsSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build(),
            )
        }

        fun syncSoon(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StatsSyncWorker>()
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}

class StatsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? com.tamilscripture.core.data.GraphHost)?.graph ?: return Result.failure()
        return if (graph.stats.upload(graph.http, bearer = null)) Result.success() else Result.retry()
    }
}
