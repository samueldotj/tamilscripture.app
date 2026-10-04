package com.tamilscripture.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tamilscripture.core.data.AppGraph
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.stats.StatsContext
import com.tamilscripture.core.data.stats.StatsRecorder
import com.tamilscripture.core.media.AudioController
import com.tamilscripture.core.services.AppServices
import kotlinx.coroutines.launch

class TsApplication : Application(), GraphHost {
    lateinit var services: AppServices
        private set

    override val graph: AppGraph get() = services.graph

    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph(this, packsBaseOverride = BuildConfig.DEV_PACKS_BASE.ifBlank { null })
        val audio = AudioController(this, graph.content, graph.origins, graph.stats, graph.appScope)
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

        // ST-2: send what is queued when the app goes to the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = StatsRecorder.syncSoon(this@TsApplication)
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
