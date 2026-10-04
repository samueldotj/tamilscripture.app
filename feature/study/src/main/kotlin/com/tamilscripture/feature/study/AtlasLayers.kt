package com.tamilscripture.feature.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.tamilscripture.core.model.GeoJson
import com.tamilscripture.core.model.Mercator

/** A kingdom or empire for one period (Cliopatria, via the website's `geo/polities`). */
internal class PolityShape(
    val id: String,
    val nameEn: String,
    val nameTa: String,
    val from: Int,
    val to: Int,
    val x: Float,
    val y: Float,
    val span: Float,
    val path: Path,
)

internal class Kingdoms(val shapes: List<PolityShape>, val years: List<Int>)

/** A person or place of the early church (`geo/church.geojson`). */
internal class ChurchPoint(val id: String, val nameEn: String, val nameTa: String, val kind: String, val x: Float, val y: Float)

internal const val FIRST_YEAR = -4000
internal const val LAST_YEAR = 800

/**
 * The timeline's data, as the website's `loadTimeline`: every polity row, and the years at
 * which the map changes. Outlines are thinned to points at least [MIN_STEP] degrees apart,
 * about what the atlas can show at its regional zoom, so a year redraws quickly (ADR-12's open question).
 */
internal suspend fun loadKingdoms(file: suspend (String) -> String?): Kingdoms? {
    val features = file("geo/polities/polities.geojson")?.let { runCatching { GeoJson.parse(it) }.getOrNull() } ?: return null
    val years = sortedSetOf<Int>()
    val shapes = features.mapNotNull { f ->
        val from = f.props["from"]?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
        val to = f.props["to"]?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
        years += maxOf(from, FIRST_YEAR)
        if (to < LAST_YEAR) years += to + 1
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            f.parts.forEach { ring -> addThinned(ring) }
        }
        PolityShape(
            f.props["id"].orEmpty(), f.props["name_en"].orEmpty(), f.props["name_ta"].orEmpty(), from, to,
            f.props["lon"]?.toFloatOrNull() ?: 0f, wy(f.props["lat"]?.toDoubleOrNull() ?: 0.0),
            f.props["span"]?.toFloatOrNull() ?: 0f, path,
        )
    }
    return Kingdoms(shapes, years.toList())
}

internal suspend fun loadChurch(file: suspend (String) -> String?): List<ChurchPoint> =
    file("geo/church.geojson")?.let { runCatching { GeoJson.parse(it) }.getOrNull() }.orEmpty().mapNotNull { f ->
        val p = f.point ?: return@mapNotNull null
        ChurchPoint(f.props["id"].orEmpty(), f.props["name_en"].orEmpty(), f.props["name_ta"].orEmpty(), f.props["type"].orEmpty(), p[0].toFloat(), wy(p[1]))
    }

private const val MIN_STEP = 0.1

private fun Path.addThinned(ring: DoubleArray) {
    if (ring.size < 6) return
    var lx = ring[0]
    var ly = ring[1]
    moveTo(lx.toFloat(), wy(ly))
    var i = 2
    while (i < ring.size) {
        val x = ring[i]
        val y = ring[i + 1]
        val last = i == ring.size - 2
        if (last || kotlin.math.abs(x - lx) + kotlin.math.abs(y - ly) >= MIN_STEP) {
            lineTo(x.toFloat(), wy(y))
            lx = x
            ly = y
        }
        i += 2
    }
    close()
}

/** World y as in the atlas: Mercator latitude, north up. */
internal fun wy(lat: Double) = -Mercator.y(lat).toFloat()

/** The website's eight timeline hues (`--j-1 … --j-8`), light and dark. */
private val HUES_LIGHT = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948)
private val HUES_DARK = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767)

/** A polity keeps its colour down the timeline: the hue comes from its id, as on the website. */
internal fun polityColor(id: String, dark: Boolean): Color {
    var h = 0L
    for (ch in id) h = (h * 31 + ch.code) and 0xFFFFFFFFL
    val list = if (dark) HUES_DARK else HUES_LIGHT
    return Color(list[(h % list.size).toInt()])
}

/** "1400 BCE" / "AD 33", or கி.மு. / கி.பி. */
internal fun yearLabel(year: Int, tamil: Boolean): String =
    if (year < 0) (if (tamil) "கி.மு. ${-year}" else "${-year} BCE") else (if (tamil) "கி.பி. $year" else "AD $year")
