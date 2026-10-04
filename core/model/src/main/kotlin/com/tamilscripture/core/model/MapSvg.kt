package com.tamilscripture.core.model

/**
 * The website's static maps (`entities/maps/…svg`, written by entity-ingest) reduced to
 * what they draw: a few classed paths and the places, each with a dot and its Tamil and
 * English labels. The app draws them itself in the reader's colours and language (M8-4)
 * instead of rendering SVG that depends on the website's stylesheet.
 */
data class MapDrawing(
    val width: Float,
    val height: Float,
    /** class ("land", "lake", "river", "route") → SVG path data, in drawing order. */
    val paths: List<Pair<String, String>>,
    val places: List<MapPlace>,
    val credit: String?,
)

data class MapPlace(
    val id: String,
    val x: Float,
    val y: Float,
    val r: Float,
    /** Emphasised (a journey's stops), with [number] in the dot. */
    val em: Boolean,
    /** Too close to others: the website shows its label only on hover or selection. */
    val crowded: Boolean,
    val number: String?,
    val ta: MapLabel?,
    val en: MapLabel?,
)

/** [anchor] is start, middle or end, as in SVG `text-anchor`. */
data class MapLabel(val text: String, val x: Float, val y: Float, val anchor: String)

object MapSvg {
    private val viewBox = Regex("""viewBox="0 0 ([\d.]+) ([\d.]+)"""")
    private val path = Regex("""<path class="(\w+)"[^>]*?\sd="([^"]+)"""")
    private val place = Regex("""<a [^>]*class="place([^"]*)" data-place="([^"]+)">(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val circle = Regex("""<circle cx="([\d.\-]+)" cy="([\d.\-]+)" r="([\d.]+)"""")
    private val text = Regex("""<text class="(\w+)"[^>]*? x="([\d.\-]+)" y="([\d.\-]+)" text-anchor="(\w+)">([^<]*)</text>""")
    private val credit = Regex("""<text class="credit"[^>]*>([^<]*)</text>""")

    fun parse(svg: String): MapDrawing? {
        val vb = viewBox.find(svg) ?: return null
        val places = place.findAll(svg).mapNotNull { m ->
            val classes = m.groupValues[1]
            val body = m.groupValues[3]
            val c = circle.find(body) ?: return@mapNotNull null
            val texts = text.findAll(body).associate { t ->
                t.groupValues[1] to MapLabel(unescape(t.groupValues[5]), t.groupValues[2].toFloat(), t.groupValues[3].toFloat(), t.groupValues[4])
            }
            MapPlace(
                id = m.groupValues[2],
                x = c.groupValues[1].toFloat(), y = c.groupValues[2].toFloat(), r = c.groupValues[3].toFloat(),
                em = "em" in classes.split(' '),
                crowded = "crowded" in classes.split(' '),
                number = texts["n"]?.text,
                ta = texts["ta"],
                en = texts["en"],
            )
        }.toList()
        return MapDrawing(
            vb.groupValues[1].toFloat(), vb.groupValues[2].toFloat(),
            path.findAll(svg).map { it.groupValues[1] to it.groupValues[2] }.toList(),
            places,
            credit.find(svg)?.groupValues?.get(1)?.let(::unescape),
        )
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
}
