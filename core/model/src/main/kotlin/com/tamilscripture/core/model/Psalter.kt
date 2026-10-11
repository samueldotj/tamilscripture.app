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

/** When each note sounds (ms), its pitch, and its head in each drawing: [x, y] as fractions, and its id. */
data class TuneNote(val on: Long, val off: Long, val midi: Int, val fullId: String, val incipitId: String?)

/** [lines]: when each line of a stanza starts (ms), for lighting the line being sung. */
data class TuneEvents(val durationMs: Long, val incipitNotes: Int, val notes: List<TuneNote>, val lines: List<Long> = emptyList()) {
    /** The note sounding at [ms], or null between notes and after the end. */
    fun at(ms: Long): TuneNote? = notes.firstOrNull { ms >= it.on && ms < it.off }

    /** The line of the stanza sung at [ms], or -1 before the first note and after the last. */
    fun line(ms: Long): Int = if (ms < 0 || ms >= durationMs) -1 else lines.indexOfLast { it <= ms }

    companion object {
        fun parse(text: String): TuneEvents {
            val o = Json.parseToJsonElement(text).jsonObject
            fun id(e: kotlinx.serialization.json.JsonElement?): String? =
                (e as? JsonArray)?.getOrNull(2)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            return TuneEvents(
                durationMs = o.getValue("duration").jsonPrimitive.long,
                incipitNotes = o.getValue("incipit_notes").jsonPrimitive.double.toInt(),
                lines = o["lines"]?.jsonArray?.map { it.jsonPrimitive.long }.orEmpty(),
                notes = o.getValue("notes").jsonArray.map { n ->
                    val m = n.jsonObject
                    TuneNote(
                        m.getValue("on").jsonPrimitive.long, m.getValue("off").jsonPrimitive.long,
                        m.getValue("midi").jsonPrimitive.double.toInt(), id(m["full"])!!, id(m["incipit"]),
                    )
                },
            )
        }
    }
}
