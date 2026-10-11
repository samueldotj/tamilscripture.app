package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/*
 * A psalter tune as the website's tools/psalter renders it (website
 * docs/feature_psalms_explorer.md §8): `entities/psalter/{tune}/{incipit,full}.draw.json`,
 * `events.json` and `audio.mp3`.
 */

/**
 * Engraved notation reduced to glyph outlines and placed marks. Each mark is a glyph (by
 * name) or path data, with the matrix `[a, b, c, d, e, f]` that places it in a drawing
 * [width] × [height] units, a stroke width when it is stroked as well as filled, and the
 * id of the note it belongs to.
 */
@Serializable
data class PsalterDrawing(
    @SerialName("w") val width: Float,
    @SerialName("h") val height: Float,
    val glyphs: Map<String, String>,
    val items: List<Mark>,
) {
    @Serializable
    data class Mark(
        @SerialName("g") val glyph: String? = null,
        @SerialName("d") val path: String? = null,
        @SerialName("m") val matrix: List<Float>,
        @SerialName("s") val stroke: Float? = null,
        @SerialName("n") val note: String? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): PsalterDrawing = json.decodeFromString(text)
    }
}

/**
 * When each note sounds (ms), its pitch, and in each drawing its id, its head's x and the
 * baseline for the words under it, as fractions of the drawing (null when not drawn).
 */
data class TuneNote(
    val on: Long, val off: Long, val midi: Int, val fullId: String, val incipitId: String?,
    val fullX: Float? = null, val fullLyric: Float? = null, val incipitX: Float? = null, val incipitLyric: Float? = null,
    val fullY: Float? = null, val incipitY: Float? = null,
)

/**
 * [lines]: when each line of a stanza starts (ms), for lighting the line being sung.
 * [slots]: per line, the notes that start a syllable, where the words go. [bpm]: the tempo
 * the audio was rendered at.
 */
data class TuneEvents(
    val durationMs: Long, val incipitNotes: Int, val notes: List<TuneNote>,
    val lines: List<Long> = emptyList(), val slots: List<List<Int>> = emptyList(), val bpm: Int = 84,
) {
    /** The note sounding at [ms], or null between notes and after the end. */
    fun at(ms: Long): TuneNote? = notes.firstOrNull { ms >= it.on && ms < it.off }

    /** The line of the stanza sung at [ms], or -1 before the first note and after the last. */
    fun line(ms: Long): Int = if (ms < 0 || ms >= durationMs) -1 else lines.indexOfLast { it <= ms }

    /**
     * The note nearest a point ([x], [y] in a drawing's units, [width] by [height]): to its
     * head or to the word under it, whichever is closer, so a tap on either finds it. -1 when
     * no note is drawn. As the website's seek.ts; noteheads sit 80 units right of their x.
     */
    fun nearest(x: Float, y: Float, width: Float, height: Float, full: Boolean): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        notes.forEachIndexed { i, n ->
            val hx = (if (full) n.fullX else n.incipitX) ?: return@forEachIndexed
            val hy = (if (full) n.fullY else n.incipitY) ?: return@forEachIndexed
            val lyric = if (full) n.fullLyric else n.incipitLyric
            val dx = hx * width + 80 - x
            val dy = minOf(kotlin.math.abs(hy * height - y), lyric?.let { kotlin.math.abs(it * height - y) } ?: Float.MAX_VALUE)
            val d = dx * dx + dy * dy * 4 // a system's height matters more than a note's step
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /**
     * A point [ms] in the whole psalm (the audio's own clock, [stanzas] a [breathMs] apart) as
     * a stanza and a position in it; a point in a breath goes to the end of the stanza before.
     */
    fun place(ms: Long, breathMs: Long, stanzas: Int): Pair<Int, Long> {
        val per = durationMs + breathMs
        val stanza = (ms / per).toInt().coerceIn(0, maxOf(0, stanzas - 1))
        return stanza to (ms - stanza * per).coerceIn(0, durationMs)
    }

    companion object {
        fun parse(text: String): TuneEvents {
            val o = Json.parseToJsonElement(text).jsonObject
            fun id(e: kotlinx.serialization.json.JsonElement?): String? =
                (e as? JsonArray)?.getOrNull(2)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            fun num(e: kotlinx.serialization.json.JsonElement?, i: Int): Float? =
                (e as? JsonArray)?.getOrNull(i)?.takeIf { it !is JsonNull }?.jsonPrimitive?.double?.toFloat()
            return TuneEvents(
                durationMs = o.getValue("duration").jsonPrimitive.long,
                incipitNotes = o.getValue("incipit_notes").jsonPrimitive.double.toInt(),
                lines = o["lines"]?.jsonArray?.map { it.jsonPrimitive.long }.orEmpty(),
                slots = o["slots"]?.jsonArray?.map { l -> l.jsonArray.map { it.jsonPrimitive.double.toInt() } }.orEmpty(),
                bpm = o["bpm"]?.jsonPrimitive?.double?.toInt() ?: 84,
                notes = o.getValue("notes").jsonArray.map { n ->
                    val m = n.jsonObject
                    TuneNote(
                        m.getValue("on").jsonPrimitive.long, m.getValue("off").jsonPrimitive.long,
                        m.getValue("midi").jsonPrimitive.double.toInt(), id(m["full"])!!, id(m["incipit"]),
                        num(m["full"], 0), num(m["full"], 3), num(m["incipit"], 0), num(m["incipit"], 3),
                        num(m["full"], 1), num(m["incipit"], 1),
                    )
                },
            )
        }
    }
}
