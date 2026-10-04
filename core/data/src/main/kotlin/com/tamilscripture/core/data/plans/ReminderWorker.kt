package com.tamilscripture.core.data.plans

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.DailyVerse
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.Plans
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * The daily reading reminder (roadmap M7-5). One-time work scheduled for the chosen time,
 * which posts today's plan passages (or the verse of the day) and schedules the next day.
 * Inexact by design: WorkManager may run it a little late, which a reminder can afford.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? GraphHost)?.graph ?: return Result.failure()
        val settings = graph.settings.settings.first()
        val minutes = settings.reminderMinutes ?: return Result.success()
        notify(applicationContext, graph)
        schedule(applicationContext, minutes)
        return Result.success()
    }

    private suspend fun notify(context: Context, graph: com.tamilscripture.core.data.AppGraph) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val settings = graph.settings.settings.first()
        val lang = settings.uiLang
        val tamil = lang == UiLang.Tamil
        val manifest = graph.content.manifest.value
        val (title, body, passage) = todays(graph, manifest, settings.version, lang)
            ?: DailyVerse.forDate().let { v ->
                Triple(
                    if (tamil) "இன்றைய வசனம்" else "Verse of the day",
                    manifest?.book(v.book)?.label(lang, v.chapter, v.verse) ?: v.toString(),
                    Passage(settings.version, v.book, v.chapter, v.verse),
                )
            }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, if (tamil) "வாசிப்பு நினைவூட்டல்" else "Reading reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val slug = manifest?.book(passage.book)?.slug ?: passage.book.lowercase()
        val link = "https://www.tamilscripture.com/${passage.version.lowercase()}/$slug/${passage.chapter}" +
            (passage.verse?.let { ".$it" } ?: "")
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    /** Today's passages of the active plan, unless they are all read. */
    private suspend fun todays(
        graph: com.tamilscripture.core.data.AppGraph,
        manifest: ContentManifest?,
        version: String,
        lang: UiLang,
    ): Triple<String, String, Passage>? {
        val m = manifest ?: return null
        val progress = graph.plans.progress.firstOrNull().orEmpty()
        val planId = graph.plans.activePlan.firstOrNull() ?: progress.keys.firstOrNull() ?: return null
        val plan = Plans.builtIn(m.books).firstOrNull { it.id == planId } ?: return null
        val prog = progress[planId] ?: return null
        val day = ChronoUnit.DAYS.between(prog.start, LocalDate.now()).toInt().coerceIn(0, plan.days - 1)
        val passages = Plans.schedule(plan)[day]
        if (Plans.dayDone(passages, day, prog.done)) return null
        val first = passages.first().units.first()
        val title = (if (lang == UiLang.Tamil) "இன்றைய வாசிப்பு · நாள் " else "Today's reading · Day ") + (day + 1)
        val body = passages.joinToString(" · ") { Plans.passageLabel(it.units, lang) }
        return Triple(title, body, Passage(version, first.book.code, first.chapter, first.verses?.first))
    }

    companion object {
        private const val WORK = "daily-reminder"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 7001

        /** Schedules the next reminder at [minutes] past midnight, or cancels it when null. */
        fun schedule(context: Context, minutes: Int?) {
            val wm = WorkManager.getInstance(context)
            if (minutes == null) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = LocalDate.now().atTime(LocalTime.of(minutes / 60, minutes % 60))
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            wm.enqueueUniqueWork(
                WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
    }
}
