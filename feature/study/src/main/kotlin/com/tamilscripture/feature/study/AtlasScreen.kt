package com.tamilscripture.feature.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.GeoFeature
import com.tamilscripture.core.model.GeoJson
import com.tamilscripture.core.model.Journey
import com.tamilscripture.core.model.Mercator
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

/** Everything the atlas draws, read once (base map, places, journeys). */
internal class AtlasData(
    val land: List<GeoFeature>,
    val lakes: List<GeoFeature>,
    val rivers: List<GeoFeature>,
    val coast: List<GeoFeature>,
    val places: List<AtlasPlace>,
    val routes: Map<String, GeoFeature>,
    val journeys: List<Journey>,
)

internal class AtlasPlace(val id: String, val nameEn: String, val nameTa: String, val type: String, val mentions: Int, val x: Float, val y: Float)

/** World coordinates: x is longitude, y is the Mercator latitude flipped so north is up. */
private fun wy(lat: Double) = -Mercator.y(lat).toFloat()

private fun path(features: List<GeoFeature>, close: Boolean): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    features.forEach { f ->
        f.parts.forEach { pts ->
            if (pts.size < 4) return@forEach
            moveTo(pts[0].toFloat(), wy(pts[1]))
            var i = 2
            while (i < pts.size) {
                lineTo(pts[i].toFloat(), wy(pts[i + 1]))
                i += 2
            }
            if (close) close()
        }
    }
}

/**
 * The atlas (roadmap M8-5): base map, places by how often the Bible names them, and the
 * journeys. Pinch, drag, double-tap and the mouse wheel move the map; tapping a place
 * shows it with a way to its page. Drawn in Compose from the website's GeoJSON (ADR-12,
 * option A), so labels use the reader's typeface and language.
 */
@Composable
fun AtlasScreen(focus: String?, links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val tamil = lang == UiLang.Tamil
    val data by produceState<AtlasData?>(null, Unit) { value = loadAtlas { graph.study.file(it) } }
    var journey by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(focus) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), links.back)
            Text(tr("வரைபடம்", "Atlas"), style = Ts.type.barTitle, color = c.ink, modifier = Modifier.padding(start = 4.dp))
        }
        val d = data
        if (d == null || d.land.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (d == null) CircularProgressIndicator(color = c.accent)
                else Text(tr("வரைபடத்துக்கு இணைய இணைப்பு அல்லது ‘வரைபடங்கள்’ பொதி தேவை.", "The atlas needs a connection, or the Maps pack in Downloads."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(28.dp))
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val pad = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            TsChip(tr("இடங்கள்", "Places"), journey == null, { journey = null }, contentPadding = pad)
            d.journeys.forEach { j -> TsChip(if (tamil) j.nameTa.ifBlank { j.nameEn } else j.nameEn, journey == j.id, { journey = j.id; selected = null }, contentPadding = pad) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AtlasCanvas(d, tamil, journey, selected, focus) { selected = it }
            val place = d.places.firstOrNull { it.id == selected }
            val j = d.journeys.firstOrNull { it.id == journey }
            if (place != null) {
                InfoCard(Modifier.align(Alignment.BottomCenter)) {
                    Text(if (tamil) place.nameTa.ifBlank { place.nameEn } else place.nameEn, style = Ts.type.cardTitle, color = c.ink)
                    Text(
                        listOf(if (tamil) place.nameEn else place.nameTa, place.type, tr("${place.mentions} இடங்களில்", "${place.mentions} mentions"))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = Ts.type.caption, color = c.muted,
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TsPillButton(tr("திற", "Open"), { links.place(place.id) }, style = PillStyle.Filled, height = 36.dp)
                        TsPillButton(tr("மூடு", "Close"), { selected = null }, height = 36.dp)
                    }
                }
            } else if (j != null) {
                InfoCard(Modifier.align(Alignment.BottomCenter)) {
                    Text(if (tamil) j.nameTa.ifBlank { j.nameEn } else j.nameEn, style = Ts.type.cardTitle, color = c.ink)
                    Text((if (tamil) j.summaryTa.ifBlank { j.summaryEn } else j.summaryEn), style = Ts.type.caption, color = c.ink2, maxLines = 3)
                    Text(tr("${j.stops.size} இடங்கள்", "${j.stops.size} stops"), style = Ts.type.captionSmall, color = c.muted)
                }
            }
        }
    }
}

/** Reads the atlas files (entities/ paths) through [file]: a study pack, the cache, or the website. */
internal suspend fun loadAtlas(file: suspend (String) -> String?): AtlasData {
    suspend fun layer(path: String) = file(path)?.let { runCatching { GeoJson.parse(it) }.getOrNull() }.orEmpty()
    val json = Json { ignoreUnknownKeys = true }
    val places = layer("geo/places.geojson").mapNotNull { f ->
        val p = f.point ?: return@mapNotNull null
        AtlasPlace(
            f.props["id"] ?: return@mapNotNull null, f.props["name_en"].orEmpty(), f.props["name_ta"].orEmpty(),
            f.props["type"].orEmpty(), f.props["mentions"]?.toIntOrNull() ?: 0, p[0].toFloat(), wy(p[1]),
        )
    }.sortedByDescending { it.mentions }
    return AtlasData(
        layer("geo/base/land.geojson"), layer("geo/base/lakes.geojson"), layer("geo/base/rivers.geojson"), layer("geo/base/coast.geojson"),
        places,
        layer("geo/journeys.geojson").associateBy { it.props["id"].orEmpty() },
        file("journeys.json")?.let { runCatching { json.decodeFromString<List<Journey>>(it) }.getOrNull() }.orEmpty(),
    )
}

@Composable
private fun InfoCard(modifier: Modifier, content: @Composable () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.navigationBarsPadding().padding(14.dp).widthIn(max = 560.dp).fillMaxWidth().clip(shape).background(c.surface)
            .border(1.5.dp, c.line2, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
internal fun AtlasCanvas(d: AtlasData, tamil: Boolean, journey: String?, selected: String?, focus: String?, onSelect: (String?) -> Unit) {
    val c = Ts.colors
    val measurer = rememberTextMeasurer()
    val family = if (tamil) Ts.type.scripture else Ts.type.label.fontFamily
    val land = remember(d) { path(d.land, close = true) }
    val lakes = remember(d) { path(d.lakes, close = true) }
    val rivers = remember(d) { path(d.rivers, close = false) }
    val coast = remember(d) { path(d.coast, close = false) }
    val route = remember(d, journey) { journey?.let { d.routes[it] }?.let { path(listOf(it), close = false) } }
    val stops = remember(d, journey) { d.journeys.firstOrNull { it.id == journey }?.stops.orEmpty() }
    // Label layouts (text and its halo), measured once per language and theme, not every frame.
    val labels = remember(d, tamil, c) { HashMap<String, Pair<TextLayoutResult, TextLayoutResult>>() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // The land's extent (the website's base map): it fills the window at first, and zooming
        // out shows all of it.
        val fit = remember(w, h) { min(w / (52f - 10f), h / (wy(22.0) - wy(46.0))) }
        val cover = remember(w, h) { max(w / (52f - 10f), h / (wy(22.0) - wy(46.0))) }
        var scale by rememberSaveable { mutableFloatStateOf(0f) }
        var cx by rememberSaveable { mutableFloatStateOf(31f) }
        var cy by rememberSaveable { mutableFloatStateOf(wy(34.0)) }
        if (scale == 0f) {
            scale = cover
            d.places.firstOrNull { it.id == focus }?.let { cx = it.x; cy = it.y; scale = fit * 6f }
        }
        // A new journey brings its stops into view.
        LaunchedEffect(journey) {
            if (stops.isNotEmpty()) {
                val xs = stops.map { it.lon.toFloat() }
                val ys = stops.map { wy(it.lat) }
                cx = (xs.min() + xs.max()) / 2f
                cy = (ys.min() + ys.max()) / 2f
                scale = min(w / max(xs.max() - xs.min(), 1f), h / max(ys.max() - ys.min(), 1f)).times(0.75f).coerceIn(fit * 0.8f, fit * 40f)
            }
        }
        fun toScreen(x: Float, y: Float) = Offset((x - cx) * scale + w / 2f, (y - cy) * scale + h / 2f)
        fun zoomAt(p: Offset, factor: Float) {
            val wx = (p.x - w / 2f) / scale + cx
            val wyv = (p.y - h / 2f) / scale + cy
            scale = (scale * factor).coerceIn(fit * 0.8f, fit * 40f)
            cx = wx - (p.x - w / 2f) / scale
            cy = wyv - (p.y - h / 2f) / scale
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = if (tamil) "வேதாகம வரைபடம்" else "Bible atlas" }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (zoom != 1f) zoomAt(centroid, zoom)
                        cx -= pan.x / scale
                        cy -= pan.y / scale
                    }
                }
                .pointerInput(d) {
                    detectTapGestures(
                        onDoubleTap = { zoomAt(it, 2f) },
                        onTap = { tap ->
                            val hit = d.places.take(600).minByOrNull { p -> (toScreen(p.x, p.y) - tap).getDistance() }
                            onSelect(hit?.takeIf { (toScreen(it.x, it.y) - tap).getDistance() <= 24.dp.toPx() }?.id)
                        },
                    )
                }
                .pointerInput(Unit) {
                    // The mouse wheel zooms at the pointer (ALOS, ChromeOS).
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Scroll) {
                                val ch = e.changes.first()
                                zoomAt(ch.position, if (ch.scrollDelta.y < 0) 1.25f else 0.8f)
                                ch.consume()
                            }
                        }
                    }
                },
        ) {
            drawRect(c.mapSea)
            withTransform({
                translate(w / 2f - cx * scale, h / 2f - cy * scale)
                scale(scale, scale, Offset.Zero)
            }) {
                val px = 1f / scale
                drawPath(land, c.mapLand, style = Fill)
                drawPath(coast, c.mapCoast, style = Stroke(1f * px, join = StrokeJoin.Round))
                drawPath(lakes, c.mapLake, style = Fill)
                drawPath(rivers, c.mapRiver, style = Stroke(1.1f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
                route?.let {
                    drawPath(
                        it, c.accent.copy(alpha = 0.85f),
                        style = Stroke(2.6f * px, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * px, 5f * px))),
                    )
                }
            }
            val dot = 4.5.dp.toPx()
            val labelStyle = TextStyle(fontFamily = family, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            val placed = ArrayList<Rect>()
            /** Where a label would go, or null when it would overlap one already placed. */
            fun place(key: String, text: String, at: Offset, ink: androidx.compose.ui.graphics.Color = c.ink, force: Boolean = false): Pair<Rect, Pair<TextLayoutResult, TextLayoutResult>>? {
                val layouts = labels.getOrPut(key) {
                    measurer.measure(text, labelStyle.copy(color = ink)) to measurer.measure(text, labelStyle.copy(color = c.mapLand))
                }
                val (layout, _) = layouts
                val r = Rect(at.x + dot + 4f, at.y - layout.size.height / 2f, at.x + dot + 4f + layout.size.width, at.y + layout.size.height / 2f)
                if (!force && placed.any { it.overlaps(r) }) return null
                placed += r
                return r to layouts
            }
            fun drawLabel(spot: Pair<Rect, Pair<TextLayoutResult, TextLayoutResult>>) {
                val (r, layouts) = spot
                // A halo of the label in the land colour, as the website's maps have.
                val k = 1.5f
                for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) drawText(layouts.second, topLeft = r.topLeft + Offset(dx * k, dy * k))
                drawText(layouts.first, topLeft = r.topLeft)
            }
            fun label(key: String, text: String, at: Offset) = place(key, text, at)?.let(::drawLabel) != null
            if (stops.isEmpty()) {
                // Places in order of how often they are named: the selected one first, then a
                // label wherever there is room; drawn as small dots, labelled dots, labels.
                val sel = d.places.firstOrNull { it.id == selected }
                val selSpot = sel?.let { p -> place(p.id + "*", if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, toScreen(p.x, p.y), c.amber, force = true) }
                val labelled = ArrayList<Pair<Offset, Pair<Rect, Pair<TextLayoutResult, TextLayoutResult>>>>()
                val small = ArrayList<Offset>()
                for (p in d.places) {
                    if (p.id == selected) continue
                    val s = toScreen(p.x, p.y)
                    if (s.x < -20 || s.y < -20 || s.x > w + 20 || s.y > h + 20) continue
                    val spot = place(p.id, if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, s)
                    if (spot != null) labelled += s to spot else if (scale > fit * 4f && small.size < 400) small += s
                }
                small.forEach { s ->
                    drawCircle(c.surface, dot * 0.6f + 1.5f, s)
                    drawCircle(c.accent, dot * 0.6f, s)
                }
                labelled.forEach { (s, _) ->
                    drawCircle(c.surface, dot + 1.5f, s)
                    drawCircle(c.accent, dot, s)
                }
                labelled.forEach { (_, spot) -> drawLabel(spot) }
                if (sel != null && selSpot != null) {
                    val s = toScreen(sel.x, sel.y)
                    drawCircle(c.surface, 7.dp.toPx() + 1.5f, s)
                    drawCircle(c.amber, 7.dp.toPx(), s)
                    drawLabel(selSpot)
                }
            } else {
                stops.forEachIndexed { i, st ->
                    val s = toScreen(st.lon.toFloat(), wy(st.lat))
                    drawCircle(c.surface, 8.dp.toPx(), s)
                    drawCircle(c.amber, 6.5.dp.toPx(), s)
                    val n = measurer.measure("${i + 1}", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.surface))
                    drawText(n, topLeft = s - Offset(n.size.width / 2f, n.size.height / 2f))
                    label("stop-$journey-$i", if (tamil) st.nameTa.ifBlank { st.nameEn } else st.nameEn, s + Offset(4f, 0f))
                }
            }
        }
    }
}
