package com.tamilscripture.core.data.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Named content origins (design §7.11, ADR-11). Every request is `origin + path`; no other
 * code knows a host name. Moving content between Vercel and R2 is a change to
 * `bootstrap.json`, never to the app.
 */
enum class Origin(val key: String) {
    /** Chapter, cross-reference, timing and entity JSON. Today: the website on Vercel. */
    Content("content"),
    /** Catalogue and downloadable packs. Today: R2 (packs.tamilaudiobible.com). */
    Packs("packs"),
    /** Commentary pointer and JSON. Today: R2 (stream.tamilaudiobible.com). */
    Commentary("commentary"),
    /** Chapter MP3s. Today: R2 (stream.tamilaudiobible.com). */
    Audio("audio"),
    /** Search, stats collector, auth callback. Always the website. */
    Api("api"),
}

@Serializable
data class Bootstrap(
    val schema: Int = 1,
    val origins: Map<String, List<String>> = emptyMap(),
    val catalogue: String = "packs/catalogue.json",
)

object BuiltInBootstrap {
    /**
     * Compiled-in fallback, used on first run and whenever no signed bootstrap has been
     * fetched yet. Kept in step with `bootstrap.json` on both providers.
     */
    val value = Bootstrap(
        origins = mapOf(
            "content" to listOf("https://www.tamilscripture.com/"),
            "packs" to listOf("https://packs.tamilaudiobible.com/"),
            "commentary" to listOf("https://stream.tamilaudiobible.com/"),
            "audio" to listOf("https://stream.tamilaudiobible.com/"),
            "api" to listOf("https://www.tamilscripture.com/"),
        ),
    )

    /** Where the app looks for the live bootstrap: one address per provider. */
    val addresses = listOf(
        "https://www.tamilscripture.com/app/bootstrap.json",
        "https://packs.tamilaudiobible.com/app/bootstrap.json",
    )
}

/** Resolves an origin to its ordered base URLs and remembers which ones are failing. */
class OriginResolver(initial: Bootstrap = BuiltInBootstrap.value) {
    private val state = MutableStateFlow(initial)
    val bootstrap: StateFlow<Bootstrap> = state

    private val downUntil = HashMap<String, Long>()

    fun update(b: Bootstrap) {
        if (b.origins.isNotEmpty()) state.value = b
    }

    /** Base URLs for [origin], healthy ones first. */
    fun bases(origin: Origin): List<String> {
        val all = state.value.origins[origin.key].orEmpty()
            .ifEmpty { BuiltInBootstrap.value.origins[origin.key].orEmpty() }
        val now = System.currentTimeMillis()
        val (down, up) = synchronized(downUntil) { all.partition { (downUntil[it] ?: 0) > now } }
        return up + down
    }

    fun url(origin: Origin, path: String): String = bases(origin).first().trimEnd('/') + "/" + path.trimStart('/')

    /** Called after a connection error or 5xx: try the next base for 10 minutes. */
    fun markDown(base: String) {
        synchronized(downUntil) { downUntil[base] = System.currentTimeMillis() + 10 * 60_000 }
    }
}
