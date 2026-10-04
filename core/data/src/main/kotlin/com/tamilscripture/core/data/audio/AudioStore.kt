package com.tamilscripture.core.data.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.net.HttpException
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where a chapter's recording lives, on the `audio` origin and on the device alike. */
object AudioPaths {
    fun chapter(version: String, recording: String, book: String, chapter: Int) =
        "$version/$recording/$book/${book}_${chapter.toString().padStart(3, '0')}.mp3"
}

/**
 * Downloaded audio (A-6.6, roadmap M5-8): each chapter's MP3 at the same path it has on
 * the audio origin, with its verse timings beside it, so playback and verse highlighting
 * work in airplane mode. Downloads run a book at a time in [AudioDownloadWorker].
 */
class AudioStore(private val context: Context, private val http: Http) {
    val root = File(context.filesDir, "audio")
    private val counts = MutableStateFlow(scan())

    /** "VERSION/BOOK" → chapters of that book on the device. */
    val downloaded: StateFlow<Map<String, Int>> = counts

    /** Books downloading or queued, as "VERSION/BOOK", with progress 0..1. */
    val working: Flow<Map<String, Float>> =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(TAG).map { infos ->
            infos.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                .mapNotNull { info ->
                    info.tags.firstOrNull { it.startsWith("audio:") }?.removePrefix("audio:")
                        ?.let { it to info.progress.getFloat(PROGRESS, 0f) }
                }.toMap()
        }

    fun mp3(version: String, recording: String, book: String, chapter: Int) =
        File(root, AudioPaths.chapter(version, recording, book, chapter))

    fun timings(version: String, book: String, chapter: Int) = File(root, "$version/timings/$book/$chapter.audio.json")

    /** The local file for a chapter, when it is downloaded. */
    fun local(version: String, recording: String, book: String, chapter: Int): File? =
        mp3(version, recording, book, chapter).takeIf { it.isFile }

    fun bytes(version: String? = null): Long =
        (if (version == null) root else File(root, version)).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun refresh() {
        counts.value = scan()
    }

    /** Removes a book's recordings and timings, or every book of [version] when [book] is null. */
    suspend fun delete(version: String, book: String? = null) = withContext(Dispatchers.IO) {
        val dir = File(root, version)
        if (book == null) {
            dir.deleteRecursively()
        } else {
            dir.listFiles()?.forEach { rec -> File(rec, book).deleteRecursively() }
            File(dir, "timings/$book").deleteRecursively()
        }
        refresh()
    }

    fun download(version: String, book: String, wifiOnly: Boolean) {
        val key = "$version/$book"
        WorkManager.getInstance(context).enqueueUniqueWork(
            "audio:$key", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf(VERSION to version, BOOK to book))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .addTag(TAG)
                .addTag("audio:$key")
                .build(),
        )
    }

    fun cancel(version: String, book: String) {
        WorkManager.getInstance(context).cancelUniqueWork("audio:$version/$book")
    }

    /** Counts the MP3s under audio/{version}/{recording}/{book}/. */
    private fun scan(): Map<String, Int> {
        val out = HashMap<String, Int>()
        root.listFiles()?.forEach { version ->
            version.listFiles()?.filter { it.name != "timings" }?.forEach { rec ->
                rec.listFiles()?.forEach { book ->
                    val n = book.listFiles()?.count { it.name.endsWith(".mp3") } ?: 0
                    if (n > 0) out["${version.name}/${book.name}"] = (out["${version.name}/${book.name}"] ?: 0) + n
                }
            }
        }
        return out
    }

    companion object {
        const val TAG = "audio-download"
        const val VERSION = "version"
        const val BOOK = "book"
        const val PROGRESS = "progress"
    }

    /** One chapter's recording, unless already here, then its verse timings. */
    internal suspend fun fetch(version: String, recording: String, book: String, chapter: Int, build: String?) {
        val dest = mp3(version, recording, book, chapter)
        if (!dest.isFile) {
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            http.download(Origin.Audio, AudioPaths.chapter(version, recording, book, chapter), part) { }
            if (!part.renameTo(dest)) throw IOException("could not move ${part.name}")
        }
        val t = timings(version, book, chapter)
        if (build != null && !t.isFile) {
            try {
                val bytes = http.get(Origin.Content, "content/$build/$version/$book/$chapter.audio.json")
                t.parentFile?.mkdirs()
                File(t.path + ".part").apply { writeBytes(bytes) }.renameTo(t)
            } catch (_: HttpException) {
                // Not every recording has verse timings; the audio still plays.
            }
        }
    }
}

/** Downloads one book of one version's recording as a foreground dataSync job. */
class AudioDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? GraphHost)?.graph ?: return Result.failure()
        val version = inputData.getString(AudioStore.VERSION) ?: return Result.failure()
        val code = inputData.getString(AudioStore.BOOK) ?: return Result.failure()
        val manifest = graph.content.manifest.value ?: graph.content.refreshManifest() ?: return Result.retry()
        val recording = manifest.version(version)?.audio?.recording ?: return Result.failure()
        val book = manifest.book(code) ?: return Result.failure()
        val title = "${book.nameTa} · $version"
        runCatching { setForeground(foreground(title, 0f)) }
        return try {
            for (ch in 1..book.chapters) {
                graph.audio.fetch(version, recording, code, ch, manifest.build)
                val f = ch.toFloat() / book.chapters
                setProgress(workDataOf(AudioStore.PROGRESS to f))
                runCatching { setForeground(foreground(title, f)) }
                graph.audio.refresh()
            }
            graph.stats.record("download", action = "audio", source = "$version/$code", amount = graph.audio.bytes(version))
            Result.success()
        } catch (e: HttpException) {
            if (e.code in 400..499) Result.failure() else Result.retry()
        } catch (e: IOException) {
            Result.retry()
        } finally {
            graph.audio.refresh()
        }
    }

    private fun foreground(title: String, fraction: Float): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val id = title.hashCode()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    private companion object {
        const val CHANNEL = "downloads"
    }
}
