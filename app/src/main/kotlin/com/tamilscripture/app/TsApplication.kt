package com.tamilscripture.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tamilscripture.app.widget.TodayWidget
import com.tamilscripture.core.data.AppGraph
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.stats.StatsContext
import com.tamilscripture.core.data.stats.StatsRecorder
import com.tamilscripture.core.data.user.SyncWorker
import com.tamilscripture.core.media.AudioController
import com.tamilscripture.core.services.AppServices
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class TsApplication : Application(), GraphHost {
    lateinit var services: AppServices
        private set

    override val graph: AppGraph get() = services.graph

    override fun onCreate() {
        super.onCreate()
        // M0-17: debug builds log disk and network work on the main thread, and leaks.
        // Logged, not fatal: the one deliberate read before the first frame (MainActivity) stays.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
        }
        val graph = AppGraph(this, packsBaseOverride = BuildConfig.DEV_PACKS_BASE.ifBlank { null })
        val audio = AudioController(this, graph.content, graph.origins, graph.stats, graph.appScope, graph.audio)
        services = AppServices(graph, audio)

        graph.content.start()
        graph.stats.context = StatsContext(appVersion = BuildConfig.VERSION_NAME, device = deviceClass())
        graph.stats.start()
        StatsRecorder.schedule(this)
        PackRepository.scheduleUpdates(this)
        graph.appScope.launch { graph.packs.refreshCatalogue() }

        graph.appScope.launch {
            graph.settings.settings.collect { s ->
                graph.stats.enabled = s.shareStats
                if (!s.shareStats) graph.stats.clear()
                graph.stats.context = graph.stats.context.copy(lang = s.uiLang.code, theme = s.appearance.name.lowercase())
            }
        }

        // M6-6: the account follows the device's changes; signed out, nothing is sent.
        graph.appScope.launch {
            graph.account.session.distinctUntilChangedBy { it?.userId }.collect { s ->
                if (s != null) {
                    SyncWorker.schedule(this@TsApplication)
                    SyncWorker.syncSoon(this@TsApplication, delaySeconds = 0)
                } else SyncWorker.cancel(this@TsApplication)
            }
        }
        graph.appScope.launch {
            graph.userData.data.drop(1).collect { d ->
                val pending = d.dirtyHighlights.isNotEmpty() || d.dirtyNotes.isNotEmpty() || d.deletedHighlights.isNotEmpty() ||
                    d.deletedNotes.isNotEmpty() || d.pendingVisits.isNotEmpty() || d.clearHistory
                if (pending && graph.account.session.value != null) SyncWorker.syncSoon(this@TsApplication)
            }
        }

        graph.appScope.launch {
            // The settings the account keeps (M6-9): a change there is sent soon.
            launch {
                graph.settings.settings
                    .distinctUntilChangedBy { listOf(it.uiLang, it.version, it.typeface, it.headings, it.footnotes, it.crossRefs, it.heat, it.commentary, it.commentarySource) }
                    .drop(1)
                    .collect { if (graph.account.session.value != null) SyncWorker.syncSoon(this@TsApplication) }
            }
            graph.plans.pending.drop(1).collect { pending ->
                if (pending && graph.account.session.value != null) SyncWorker.syncSoon(this@TsApplication)
            }
        }

        // ST-2: send what is queued when the app goes to the background; and bring the
        // widget's "Continue reading" up to date for the home screen the reader returns to.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            // M6-9a: what changed on the website meanwhile comes down when the app is opened.
            override fun onStart(owner: LifecycleOwner) {
                if (graph.account.session.value != null) SyncWorker.syncSoon(this@TsApplication, delaySeconds = 0)
            }

            override fun onStop(owner: LifecycleOwner) {
                StatsRecorder.syncSoon(this@TsApplication)
                graph.appScope.launch { runCatching { TodayWidget.refresh(this@TsApplication) } }
            }
        })
    }

    /** phone, tablet or desktop (ChromeOS / ALOS) for stats (requirements §5.1). */
    private fun deviceClass(): String = when {
        Build.VERSION.SDK_INT >= 27 && packageManager.hasSystemFeature(PackageManager.FEATURE_PC) -> "desktop"
        packageManager.hasSystemFeature("org.chromium.arc") -> "desktop"
        resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
        else -> "phone"
    }
}
