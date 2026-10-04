package com.tamilscripture.core.data.packs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.net.HttpException
import com.tamilscripture.core.data.net.Origin
import uniffi.ts_mobile.PackException
import uniffi.ts_mobile.decompressPack
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads, verifies and installs one pack (design §7.7):
 * download with Range resume → SHA-256 of the compressed file → zstd decompress
 * (in Rust) with SHA-256 of the result → quick_check and schema → atomic install.
 */
class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as? GraphHost)?.graph ?: return Result.failure()
        val packs = graph.packs
        val id = inputData.getString(ID) ?: return Result.failure()
        val entry = packs.catalogue.value?.entry(id) ?: packs.refreshCatalogue()?.entry(id) ?: return Result.failure()
        runCatching { setForeground(foreground(entry, 0f)) }

        val staging = packs.store.stagingDir
        val zst = File(staging, "$id-${entry.version}.zst")
        val raw = File(staging, "$id-${entry.version}.sqlite")
        if (staging.freeSpace < (entry.size - zst.length()) + entry.rawSize + entry.rawSize / 10) {
            packs.reportFailure(id, "space")
            return Result.failure()
        }
        try {
            graph.http.download(Origin.Packs, entry.path, zst) { have ->
                val f = (have.toFloat() / entry.size).coerceIn(0f, 0.99f)
                setProgress(workDataOf(PROGRESS to f))
                runCatching { setForeground(foreground(entry, f)) }
            }
            setProgress(workDataOf(PROGRESS to 1f))
            if (sha256(zst) != entry.sha256) {
                zst.delete()
                return retryOrFail(packs, id, "checksum")
            }
            val rawSha = decompressPack(zst.path, raw.path)
            zst.delete()
            if (rawSha != entry.rawSha256 || !packs.store.check(raw, entry.schema)) {
                raw.delete()
                return retryOrFail(packs, id, "checksum")
            }
            packs.store.install(id, entry.version, raw)
            graph.stats.record("download", action = "install", source = id, amount = entry.size)
            return Result.success()
        } catch (e: HttpException) {
            return if (e.code in 400..499) { packs.reportFailure(id, "http ${e.code}"); Result.failure() } else Result.retry()
        } catch (e: IOException) {
            return Result.retry()
        } catch (e: PackException) {
            raw.delete()
            return retryOrFail(packs, id, "decompress")
        }
    }

    private fun retryOrFail(packs: PackRepository, id: String, reason: String): Result =
        if (runAttemptCount < 3) Result.retry() else { packs.reportFailure(id, reason); Result.failure() }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun foreground(entry: CatalogueEntry, fraction: Float): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(entry.title.ta.ifBlank { entry.title.en })
            .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(entry.id.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(entry.id.hashCode(), n)
        }
    }

    companion object {
        const val ID = "id"
        const val PROGRESS = "progress"
        const val TAG = "pack-download"
        private const val CHANNEL = "downloads"
    }
}

/** Refreshes the catalogue and updates installed packs that have a newer version (DL-6). */
class PackUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? GraphHost)?.graph ?: return Result.failure()
        val cat = graph.packs.refreshCatalogue() ?: return Result.retry()
        for ((id, inst) in graph.packs.store.installed.value) {
            val latest = cat.entry(id) ?: continue
            if (latest.version > inst.version && latest.schema <= SUPPORTED_PACK_SCHEMA) graph.packs.download(id, wifiOnly = true)
        }
        return Result.success()
    }
}
