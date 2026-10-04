package com.tamilscripture.core.data

import android.content.Context
import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.content.CommentaryRepository
import com.tamilscripture.core.data.content.ContentRepository
import com.tamilscripture.core.data.content.SearchRepository
import com.tamilscripture.core.data.net.BuiltInBootstrap
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.plans.PlanRepository
import com.tamilscripture.core.data.settings.SettingsRepository
import com.tamilscripture.core.data.stats.StatsRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The app's object graph, built once in the Application. Plain constructor injection keeps
 * start-up free of reflection and code generation (NF-2).
 *
 * [packsBaseOverride] points the `packs` origin at a local pack server in debug builds
 * (tools/serve-packs.py); release builds always use the bootstrap.
 */
class AppGraph(context: Context, packsBaseOverride: String? = null) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    val origins = OriginResolver(
        if (packsBaseOverride.isNullOrBlank()) BuiltInBootstrap.value
        else BuiltInBootstrap.value.copy(origins = BuiltInBootstrap.value.origins + ("packs" to listOf(packsBaseOverride))),
    )
    val http = Http(origins)
    val onlineCache = OnlineCache(File(context.filesDir, "online_cache"))
    val packs = PackRepository(context, http, json, appScope)
    val content = ContentRepository(http, onlineCache, packs, json, appScope)
    val commentary = CommentaryRepository(http, onlineCache, json)
    val search = SearchRepository(http, packs, json)
    val settings = SettingsRepository(context)
    val plans = PlanRepository(context)
    val stats = StatsRecorder(File(context.filesDir, "stats"), appScope, json)
}

/** Implemented by the Application so WorkManager workers reach the single graph. */
interface GraphHost {
    val graph: AppGraph
}
