package com.tamilscripture.core.data

import android.content.Context
import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.content.CommentaryRepository
import com.tamilscripture.core.data.content.ContentRepository
import com.tamilscripture.core.data.content.SearchRepository
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.data.plans.PlanRepository
import com.tamilscripture.core.data.settings.SettingsRepository
import com.tamilscripture.core.data.stats.StatsRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The app's object graph, built once in the Application. Plain constructor injection keeps
 * start-up free of reflection and code generation (NF-2).
 */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val origins = OriginResolver()
    val http = Http(origins)
    val onlineCache = OnlineCache(File(context.filesDir, "online_cache"))
    val content = ContentRepository(http, onlineCache, appScope)
    val commentary = CommentaryRepository(http, onlineCache, content.json)
    val search = SearchRepository(http, content.json)
    val settings = SettingsRepository(context)
    val plans = PlanRepository(context)
    val stats = StatsRecorder(File(context.filesDir, "stats"), appScope, content.json)
}
