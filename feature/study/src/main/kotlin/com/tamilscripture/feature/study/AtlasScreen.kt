package com.tamilscripture.feature.study

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tamilscripture.core.designsystem.component.HDivider
import androidx.compose.foundation.layout.fillMaxHeight
import com.tamilscripture.core.designsystem.component.VDivider
import com.tamilscripture.core.model.VerseId
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/** Everything the atlas draws, read once (base map, places, journeys). */
/**
 * One-pixel outlines whatever the zoom: the GPU draws these without tessellating a stroke,
 * which for the coast and kingdoms' borders was most of a frame on a phone.
 */
private val Hairline = Stroke(0f)

/**
 * The atlas as a list (M8-5f): a journey's stops in order, or the places by how often the
 * Bible names them. A row opens the place's page; its map button shows it on the map.
 */
@Composable
private fun PlaceList(d: AtlasData, journey: String?, tamil: Boolean, onOpen: (String) -> Unit, onShow: (String) -> Unit) {
    val c = Ts.colors
    val stops = d.journeys.firstOrNull { it.id == journey }?.stops
    val byId = remember(d) { d.places.associateBy { it.id } }
    val rows: List<Pair<String, AtlasPlace?>> = remember(d, journey) {
        stops?.mapIndexed { i, st -> "${i + 1}. " + (if (tamil) st.nameTa.ifBlank { st.nameEn } else st.nameEn) to byId[st.place] }
            ?: d.places.take(400).map { (if (tamil) it.nameTa.ifBlank { it.nameEn } else it.nameEn) to it }
    }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(rows.size) { i ->
            val (title, p) = rows[i]
            Row(
                Modifier.fillMaxWidth().then(if (p != null) Modifier.clickable { onOpen(p.id) } else Modifier)
                    .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Ts.type.label, color = c.ink)
                    if (p != null) Text(
                        listOf(if (tamil) p.nameEn else p.nameTa, p.type, if (tamil) "${p.mentions} இடங்களில்" else if (p.mentions == 1) "1 mention" else "${p.mentions} mentions")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = Ts.type.caption, color = c.muted,
                    )
                }
                if (p != null) IconBox(TsIcons.MapView, if (tamil) "வரைபடத்தில் காட்டு" else "Show on the map", { onShow(p.id) }, tint = c.muted, iconSize = 20.dp)
            }
            HDivider(Modifier.padding(start = 18.dp))
        }
    }
}

/** Places whose Tamil or English name matches [query]: names that start with it first, then by mentions. */
internal fun findPlaces(places: List<AtlasPlace>, query: String, limit: Int = 8): List<AtlasPlace> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    fun starts(p: AtlasPlace) = p.nameEn.lowercase().startsWith(q) || p.nameTa.startsWith(q)
    return places.filter { it.nameEn.lowercase().contains(q) || it.nameTa.contains(q) }
        .sortedWith(compareByDescending<AtlasPlace> { starts(it) }.thenByDescending { it.mentions })
        .take(limit)
}

/** How far the atlas zooms out, as a share of the detailed box: about Europe to India in view. */
private const val MIN_ZOOM = 0.3f

internal class AtlasData(
    val land: List<GeoFeature>,
    val lakes: List<GeoFeature>,
    val rivers: List<GeoFeature>,
    val coast: List<GeoFeature>,
    val places: List<AtlasPlace>,
    val routes: Map<String, GeoFeature>,
    val journeys: List<Journey>,
    /** The rest of the world, coarse (Natural Earth 1:110m, the detailed box cut out), as on the website. */
    val world: List<GeoFeature> = emptyList(),
)

internal class AtlasPlace(val id: String, val nameEn: String, val nameTa: String, val type: String, val mentions: Int, val x: Float, val y: Float)

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

/** The controls floating over the map (2B): the sheet's colour, nearly opaque. */
@Composable
internal fun floatColor(): Color = sheetColor().copy(alpha = 0.9f)

/** A chip floating over the map: gold when chosen, else the float colour with a hairline. */
@Composable
private fun MapChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.clip(shape).background(if (selected) c.accent else floatColor())
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line2, shape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = Ts.type.labelSmall, color = if (selected) c.onAccent else c.ink2, maxLines = 1)
    }
}

/**
 * The atlas (roadmap M8-5; laid out as the design's 2B): the map fills the screen, with the
 * back button, search and layers floating at the top over chips for the journeys' groups,
 * zoom and recentre at the right, and a sheet from the bottom for the chosen journey (its
 * stops, and a tour along them), place or year. Pinch, drag, double-tap and the mouse wheel
 * move the map. Drawn in Compose from the website's GeoJSON (ADR-12, option A), so labels
 * use the reader's typeface and language.
 */
@Composable
fun AtlasScreen(focus: String?, links: StudyLinks, startJourney: String? = null, fitPlaces: List<String> = emptyList()) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val tamil = lang == UiLang.Tamil
    val density = LocalDensity.current
    val data by produceState<AtlasData?>(null, Unit) { value = loadAtlas { graph.study.file(it) } }
    var journey by rememberSaveable { mutableStateOf(startJourney) }
    // M8-5c: "places", "kingdoms" (a year on the timeline) or "church".
    var layer by rememberSaveable { mutableStateOf("places") }
    val kingdoms by produceState<Kingdoms?>(null, layer == "kingdoms") {
        if (layer == "kingdoms" && value == null) value = loadKingdoms { graph.study.file(it) }
    }
    val church by produceState<List<ChurchPoint>>(emptyList(), layer == "church") {
        if (layer == "church" && value.isEmpty()) value = loadChurch { graph.study.file(it) }
    }
    var yearIndex by rememberSaveable { mutableIntStateOf(-1) }
    var selected by rememberSaveable { mutableStateOf(focus) }
    // M8-5d: find a place (or a journey) by name.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // M8-5f: the places as a list, in rank order (a journey's stops in order), for TalkBack and for reading.
    var listView by rememberSaveable { mutableStateOf(false) }
    var layersMenu by remember { mutableStateOf(false) }
    // 2B: the tour's stop (-1 before it starts), whether it is moving on, and the sheet opened up.
    var step by rememberSaveable(journey) { mutableIntStateOf(-1) }
    var playing by remember(journey) { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // How much of the map the floating controls and the sheet cover.
    var topH by remember { mutableStateOf(0.dp) }
    var sheetH by remember { mutableStateOf(0.dp) }
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val refLabel: (String) -> String = { id ->
        VerseId.parse(id)?.let { v ->
            manifest?.book(v.book)?.let { b -> ((if (tamil) b.abbrTa else b.abbrEn).firstOrNull() ?: b.name(lang)) + " ${v.chapter}:${v.verse}" }
        } ?: id
    }
    fun chooseJourney(id: String?) {
        layer = "places"; journey = id; selected = null; sheetOpen = false
    }
    val d = data
    val j = d?.journeys?.firstOrNull { it.id == journey }
    val groups = remember(d) { d?.let { journeyGroups(it.journeys) }.orEmpty() }
    // The tour: a stop every few seconds, stopping at the last.
    LaunchedEffect(playing) {
        val last = j?.stops?.lastIndex ?: return@LaunchedEffect
        while (playing) {
            if (step >= last) { playing = false; break }
            step += 1
            delay(2600)
        }
    }

    Box(Modifier.fillMaxSize().background(if (listView) c.bg else c.mapSea)) {
        if (d == null || d.land.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (d == null) CircularProgressIndicator(color = c.accent)
                else Text(tr("வரைபடத்துக்கு இணைய இணைப்பு அல்லது ‘வரைபடங்கள்’ பொதி தேவை.", "The atlas needs a connection, or the Maps pack in Downloads."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(28.dp))
            }
        } else if (listView) {
            Box(Modifier.fillMaxSize().padding(top = topH)) {
                PlaceList(d, journey, tamil, onOpen = { links.place(it) }, onShow = { id -> layer = "places"; journey = null; selected = id; listView = false })
            }
        } else {
            // M8-5b: the sheet on phones, narrower at the bottom left on medium windows, and a
            // pane beside the map on wide ones (with a journey's stops in order).
            val place = d.places.firstOrNull { it.id == selected }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 840.dp
                val medium = maxWidth >= 600.dp
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val sheetMax = maxHeight * 0.62f
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val k = kingdoms
                        val year = k?.years?.let { ys ->
                            if (yearIndex !in ys.indices) yearIndex = ys.indexOfFirst { it >= -1000 }.coerceAtLeast(0)
                            ys[yearIndex]
                        }
                        AtlasCanvas(
                            d, tamil, journey, selected, focus, fitPlaces = fitPlaces, onOpenPlace = { links.place(it) },
                            kingdoms = if (layer == "kingdoms" && k != null && year != null) k.shapes.filter { year in it.from..it.to } else emptyList(),
                            church = if (layer == "church") church else emptyList(),
                            step = if (j != null) step else -1,
                            // With no sheet up, the buttons stay above the navigation bar.
                            insets = PaddingValues(top = topH, bottom = if (expanded) navBottom else maxOf(sheetH, navBottom)),
                        ) { selected = it }
                        if (!expanded) Box(
                            (if (medium) Modifier.align(Alignment.BottomStart).padding(start = 14.dp).widthIn(max = 400.dp) else Modifier.align(Alignment.BottomCenter))
                                .fillMaxWidth().onSizeChanged { sheetH = with(density) { it.height.toDp() } },
                        ) {
                            when {
                                place != null -> MapSheet(sheetOpen, { sheetOpen = it }, header = {
                                    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        val kick = listOf(place.type, tr("${place.mentions} இடங்களில்", if (place.mentions == 1) "1 mention" else "${place.mentions} mentions"))
                                            .filter { it.isNotBlank() }.joinToString(" · ")
                                        Text(if (tamil) kick else kick.uppercase(), style = if (tamil) Ts.type.kicker.copy(letterSpacing = 0.sp) else Ts.type.kicker, color = c.accent)
                                        Text(if (tamil) place.nameTa.ifBlank { place.nameEn } else place.nameEn, style = Ts.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.ink)
                                        (if (tamil) place.nameEn else place.nameTa).takeIf { it.isNotBlank() }?.let { Text(it, style = Ts.type.caption.copy(fontSize = 13.sp), color = c.muted) }
                                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TsPillButton(tr("திற", "Open"), { links.place(place.id) }, style = PillStyle.Filled, height = 40.dp)
                                            TsPillButton(tr("மூடு", "Close"), { selected = null }, height = 40.dp)
                                        }
                                    }
                                })
                                layer == "kingdoms" -> MapSheet(false, {}, header = {
                                    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 12.dp)) {
                                        if (k == null || year == null) {
                                            CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        } else {
                                            Text(tr("அரசுகள்", "KINGDOMS"), style = if (tamil) Ts.type.kicker.copy(letterSpacing = 0.sp) else Ts.type.kicker, color = c.accent)
                                            Text(yearLabel(year, tamil), style = Ts.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.ink)
                                            // Continuous, rounded to the nearest year at which the map changes: a step per
                                            // year would draw a tick for each of the timeline's ~200 changes.
                                            Slider(
                                                value = yearIndex.toFloat(), onValueChange = { yearIndex = it.roundToInt() },
                                                valueRange = 0f..(k.years.size - 1).toFloat(),
                                                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line2),
                                            )
                                            Text("Cliopatria (CC BY 4.0)", style = Ts.type.captionSmall, color = c.muted)
                                        }
                                    }
                                })
                                j != null -> {
                                    val subtitle = remember(j, manifest, tamil) {
                                        fun book(code: String) = manifest?.book(code)?.name(lang) ?: code
                                        val a = j.passages.firstOrNull()?.substringBefore('-')?.let { VerseId.parse(it) }
                                        val b = j.passages.lastOrNull()?.substringAfter('-')?.let { VerseId.parse(it) }
                                        val range = when {
                                            a == null -> ""
                                            b == null || b == a -> "${book(a.book)} ${a.chapter}:${a.verse}"
                                            a.book == b.book -> "${book(a.book)} ${a.chapter}:${a.verse} – ${b.chapter}:${b.verse}"
                                            else -> "${book(a.book)} ${a.chapter}:${a.verse} – ${book(b.book)} ${b.chapter}:${b.verse}"
                                        }
                                        listOf(range, kmLabel(journeyKm(d.routes[j.id], j.stops), tamil)).filter { it.isNotBlank() }.joinToString(" · ")
                                    }
                                    JourneySheet(
                                        j, groups.firstOrNull { g -> g.journeys.any { it.id == j.id } }, tamil, subtitle, refLabel,
                                        step = step, playing = playing, expanded = sheetOpen, maxHeight = sheetMax,
                                        onExpand = { sheetOpen = it },
                                        onPlay = {
                                            if (!playing && step >= j.stops.lastIndex) step = -1
                                            playing = !playing
                                        },
                                        onStep = { playing = false; step = it },
                                        onOpenPlace = { links.place(it) },
                                        onJourney = { chooseJourney(it) },
                                    )
                                }
                            }
                        }
                    }
                    if (expanded && (place != null || j != null)) {
                        VDivider()
                        DetailsPane(
                            place, j, tamil, onOpen = { links.place(it) }, onSelect = { selected = it }, onClose = { selected = null; journey = null },
                            refLabel = refLabel,
                        )
                    }
                }
            }
        }

        // The floating top (2B): back, search and layers, then the journeys' groups.
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { topH = with(density) { it.height.toDp() } }.statusBarsPadding()) {
            val ring = Modifier.border(1.dp, c.line2, CircleShape)
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), links.back, ring, tint = c.ink, background = floatColor())
                val pill = RoundedCornerShape(999.dp)
                Row(
                    Modifier.weight(1f).height(44.dp).clip(pill).background(floatColor()).border(1.dp, c.line2, pill)
                        .then(if (searching) Modifier else Modifier.clickable(role = Role.Button) { searching = true })
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(TsIcons.Search, null, Modifier.size(18.dp), tint = c.muted)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(tr("இடம், பயணம்", "Place or journey"), style = Ts.type.body.copy(fontSize = 14.sp), color = c.muted, maxLines = 1)
                        if (searching) {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                            BasicTextField(
                                query, { query = it }, singleLine = true, textStyle = Ts.type.body.copy(fontSize = 14.sp, color = c.ink),
                                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().focusRequester(fr),
                            )
                        }
                    }
                    if (searching) IconBox(TsIcons.Close, tr("தேடலை மூடு", "Close search"), { searching = false; query = "" }, tint = c.muted, iconSize = 18.dp)
                }
                Box {
                    IconBox(TsIcons.Filter, tr("அடுக்குகள்", "Layers"), { layersMenu = true }, ring, tint = c.ink, iconSize = 20.dp, background = floatColor())
                    DropdownMenu(expanded = layersMenu, onDismissRequest = { layersMenu = false }, containerColor = c.surface) {
                        @Composable
                        fun item(on: Boolean, ta: String, en: String, onClick: () -> Unit) = DropdownMenuItem(
                            text = { Text((if (on) "✓  " else "     ") + tr(ta, en), color = c.ink) },
                            onClick = { layersMenu = false; onClick() },
                        )
                        item(!listView && layer == "places" && journey == null, "இடங்கள்", "Places") { listView = false; chooseJourney(null) }
                        item(!listView && layer == "kingdoms", "அரசுகள்", "Kingdoms") { listView = false; layer = "kingdoms"; journey = null; selected = null }
                        item(!listView && layer == "church", "ஆதித் திருச்சபை", "Early church") { listView = false; layer = "church"; journey = null; selected = null }
                        HDivider()
                        item(listView, "பட்டியலாகக் காட்டு", "Show as a list") { listView = !listView }
                    }
                }
            }
            if (d != null && d.land.isNotEmpty() && !searching) {
                val chips = rememberLazyListState()
                // The chosen group's chip scrolls into view (Paul is off the edge of a phone).
                val chosen = if (layer == "church") groups.size else groups.indexOfFirst { g -> g.journeys.any { it.id == journey } }
                LaunchedEffect(chosen) { if (chosen >= 0) chips.animateScrollToItem(chosen, -with(density) { 48.dp.roundToPx() }) }
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), state = chips,
                    contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(groups.size) { i ->
                        val g = groups[i]
                        val on = layer == "places" && g.journeys.any { it.id == journey }
                        // A second tap on the chosen group clears it, back to the places.
                        MapChip(if (tamil) g.nameTa else g.nameEn, on) { chooseJourney(if (on) null else g.journeys.first().id) }
                    }
                    item {
                        MapChip(tr("ஆதித் திருச்சபை", "Early church"), layer == "church") {
                            if (layer == "church") layer = "places" else { layer = "church"; journey = null; selected = null }
                        }
                    }
                }
            }
        }

        // Search results, under the search: journeys by name, then places.
        if (d != null && searching && query.isNotBlank()) {
            val q = query.trim().lowercase()
            val js = remember(d, q) { d.journeys.filter { it.nameEn.lowercase().contains(q) || it.nameTa.contains(q) }.take(3) }
            val found = remember(d, q) { findPlaces(d.places, q, limit = 8 - js.size) }
            if (js.isNotEmpty() || found.isNotEmpty()) Column(
                Modifier.align(Alignment.TopCenter).padding(top = topH, start = 14.dp, end = 14.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line2, RoundedCornerShape(16.dp)),
            ) {
                js.forEach { jj ->
                    Column(Modifier.fillMaxWidth().clickable { listView = false; chooseJourney(jj.id); searching = false; query = "" }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(if (tamil) jj.nameTa.ifBlank { jj.nameEn } else jj.nameEn, style = Ts.type.label, color = c.ink)
                        Text(tr("பயணம் · ${jj.stops.size} இடங்கள்", "Journey · ${jj.stops.size} stops"), style = Ts.type.caption, color = c.muted)
                    }
                }
                found.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            listView = false; layer = "places"; journey = null; selected = p.id; searching = false; query = ""
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, style = Ts.type.label, color = c.ink)
                        Text(
                            listOf(if (tamil) p.nameEn else p.nameTa, p.type).filter { it.isNotBlank() }.joinToString(" · "),
                            style = Ts.type.caption, color = c.muted,
                        )
                    }
                }
            }
        }
    }
}

/** The details pane beside the map on wide windows (M8-5b): the place, or a journey with its stops. */
@Composable
internal fun DetailsPane(
    place: AtlasPlace?, j: Journey?, tamil: Boolean, onOpen: (String) -> Unit, onSelect: (String) -> Unit, onClose: () -> Unit,
    /** "ACT.13.1" as a reader would write it ("அப் 13:1"). */
    refLabel: (String) -> String = { it },
) {
    val c = Ts.colors
    Column(Modifier.width(340.dp).fillMaxHeight().background(c.pane).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    place != null -> if (tamil) place.nameTa.ifBlank { place.nameEn } else place.nameEn
                    j != null -> if (tamil) j.nameTa.ifBlank { j.nameEn } else j.nameEn
                    else -> ""
                },
                style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.weight(1f),
            )
            IconBox(TsIcons.Close, if (tamil) "மூடு" else "Close", onClose, tint = c.muted)
        }
        if (place != null) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOf(if (tamil) place.nameEn else place.nameTa, place.type, if (tamil) "${place.mentions} இடங்களில்" else if (place.mentions == 1) "1 mention" else "${place.mentions} mentions")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = Ts.type.caption, color = c.muted,
                )
                TsPillButton(if (tamil) "திற" else "Open", { onOpen(place.id) }, style = PillStyle.Filled, height = 38.dp)
            }
        } else if (j != null) {
            Text((if (tamil) j.summaryTa.ifBlank { j.summaryEn } else j.summaryEn), style = Ts.type.caption, color = c.ink2, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(j.stops.size) { i ->
                    val st = j.stops[i]
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = st.place.isNotBlank()) { onSelect(st.place) }.padding(horizontal = 18.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = Ts.type.labelSmall, color = c.amber, modifier = Modifier.width(22.dp))
                        Column {
                            Text(if (tamil) st.nameTa.ifBlank { st.nameEn } else st.nameEn, style = Ts.type.label, color = c.ink)
                            st.ref?.let { Text(refLabel(it), style = Ts.type.captionSmall, color = c.muted) }
                        }
                    }
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
        world = layer("geo/base/world.geojson"),
    )
}

/** The base map's paths, held as one immutable value so [BaseMap] skips recomposition. */
@Immutable
private class BaseLayers(val land: Path, val coast: Path, val lakes: Path, val rivers: Path, val world: Path)

@Composable
private fun BaseMap(layers: BaseLayers, view: () -> Triple<Float, Float, Float>) {
    val c = Ts.colors
    Canvas(Modifier.fillMaxSize().clipToBounds().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val (cx, cy, scale) = view()
        drawRect(c.mapSea)
        withTransform({
            translate(size.width / 2f - cx * scale, size.height / 2f - cy * scale)
            scale(scale, scale, Offset.Zero)
        }) {
            // The world first: zoomed out, it fills in around the detailed box (lon 10-52, lat 22-46).
            drawPath(layers.world, c.mapLand, style = Fill)
            drawPath(layers.land, c.mapLand, style = Fill)
            drawPath(layers.coast, c.mapCoast, style = Hairline)
            drawPath(layers.lakes, c.mapLake, style = Fill)
            drawPath(layers.rivers, c.mapRiver, style = Hairline)
        }
    }
}

@Composable
internal fun AtlasCanvas(
    d: AtlasData,
    tamil: Boolean,
    journey: String?,
    selected: String?,
    focus: String?,
    kingdoms: List<PolityShape> = emptyList(),
    church: List<ChurchPoint> = emptyList(),
    /** Places to bring into view on opening (a chapter's places, M8-5e). */
    fitPlaces: List<String> = emptyList(),
    /** The right-click menu's "Open": the place's page. */
    onOpenPlace: (String) -> Unit = {},
    /** The journey tour's stop (2B): stops before it are travelled, it is lit; -1 for none. */
    step: Int = -1,
    /** What floats over the map's edges (search above, the sheet below): fits and centring keep clear of them. */
    insets: PaddingValues = PaddingValues(0.dp),
    onSelect: (String?) -> Unit,
) {
    val c = Ts.colors
    val measurer = rememberTextMeasurer()
    val family = if (tamil) Ts.type.scripture else Ts.type.label.fontFamily
    val land = remember(d) { path(d.land, close = true) }
    val world = remember(d) { path(d.world, close = true) }
    val lakes = remember(d) { path(d.lakes, close = true) }
    val rivers = remember(d) { path(d.rivers, close = false) }
    val coast = remember(d) { path(d.coast, close = false) }
    val route = remember(d, journey) { journey?.let { d.routes[it] }?.let { path(listOf(it), close = false) } }
    val stops = remember(d, journey) { d.journeys.firstOrNull { it.id == journey }?.stops.orEmpty() }
    // Label layouts (text and its halo), measured once per language and theme, not every frame.
    val labels = remember(d, tamil, c) { HashMap<String, Pair<TextLayoutResult, TextLayoutResult>>() }
    // The places drawn with a name in the last frame (dot and label), so a tap goes to the place
    // the reader sees named rather than an unnamed one a pixel nearer (Jerusalem, not Gibeah).
    val named = remember(d) { HashMap<String, Pair<Offset, Rect>>() }

    val mapFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val keyStepPx = with(density) { 80.dp.toPx() }
    val buttonsPx = with(density) { 66.dp.toPx() }
    // Read when a glide starts, so a fit uses the sheet's height once it has one.
    val edges by rememberUpdatedState(with(density) { insets.calculateTopPadding().toPx() to insets.calculateBottomPadding().toPx() })
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
        val scope = rememberCoroutineScope()
        var menuAt by remember { mutableStateOf<Offset?>(null) }
        var menuPlace by remember { mutableStateOf<AtlasPlace?>(null) }
        var motion by remember { mutableStateOf<Job?>(null) }
        // The centre stays over the region the map has detail for, give or take a screen.
        fun clampCentre() {
            cx = cx.coerceIn(0f, 62f)
            cy = cy.coerceIn(wy(52.0), wy(14.0))
        }

        /** Glides the camera to a centre and scale (fit to a journey, centre on a place). */
        fun glideTo(tx: Float, ty0: Float, ts: Float, clear: Boolean = false) {
            motion?.cancel()
            // [clear]: put the point in the middle of what the search and the sheet leave visible.
            val ty = if (clear) ty0 - (edges.first - edges.second) / 2f / ts else ty0
            val (x0, y0, s0) = Triple(cx, cy, scale)
            motion = scope.launch {
                animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { t, _ ->
                    cx = x0 + (tx - x0) * t
                    cy = y0 + (ty - y0) * t
                    // Scale moves in log space, so zooming in and out feel alike.
                    scale = s0 * (ts / s0).pow(t)
                }
            }
        }

        /** The journey's stops in the visible part of the map. */
        fun fitStops() {
            if (stops.isEmpty()) return
            val xs = stops.map { it.lon.toFloat() }
            val ys = stops.map { wy(it.lat) }
            val visible = max(h - edges.first - edges.second, h * 0.3f)
            // Clear of the zoom buttons on the right (and as much on the left, to stay centred).
            val across = max(w - 2 * buttonsPx, w * 0.5f)
            glideTo(
                (xs.min() + xs.max()) / 2f, (ys.min() + ys.max()) / 2f,
                min(across / max(xs.max() - xs.min(), 1f), visible / max(ys.max() - ys.min(), 1f)).times(0.9f).coerceIn(fit * MIN_ZOOM, fit * 40f),
                clear = true,
            )
        }

        // A new journey brings its stops into view, once the sheet below has its height.
        LaunchedEffect(journey) {
            if (stops.isNotEmpty()) {
                withFrameNanos { }
                withFrameNanos { }
                fitStops()
            }
        }
        // The tour follows its stop, keeping the zoom.
        LaunchedEffect(step, journey) {
            val st = stops.getOrNull(step) ?: return@LaunchedEffect
            glideTo(st.lon.toFloat(), wy(st.lat), max(scale, fit * 1.5f), clear = true)
        }
        // A chapter's places, when the atlas was opened from its map (M8-5e).
        LaunchedEffect(fitPlaces) {
            val ps = d.places.filter { it.id in fitPlaces.toSet() }
            if (ps.isNotEmpty()) {
                val xs = ps.map { it.x }
                val ys = ps.map { it.y }
                glideTo(
                    (xs.min() + xs.max()) / 2f, (ys.min() + ys.max()) / 2f,
                    min(w / max(xs.max() - xs.min(), 0.5f), h / max(ys.max() - ys.min(), 0.5f)).times(0.7f).coerceIn(fit * MIN_ZOOM, fit * 40f),
                )
            }
        }
        // A place chosen from the list or search glides into view, clear of the search and sheet.
        LaunchedEffect(selected) {
            val p = d.places.firstOrNull { it.id == selected } ?: return@LaunchedEffect
            withFrameNanos { }
            val sp = Offset((p.x - cx) * scale + w / 2f, (p.y - cy) * scale + h / 2f)
            if (sp.x !in 0f..w || sp.y !in edges.first..max(edges.first, h - edges.second)) glideTo(p.x, p.y, max(scale, fit * 4f), clear = true)
        }

        /** The recentre button: back to the journey, the chosen place, or the whole region. */
        fun recentre() {
            val p = d.places.firstOrNull { it.id == selected }
            when {
                stops.isNotEmpty() -> fitStops()
                p != null -> glideTo(p.x, p.y, max(scale, fit * 4f), clear = true)
                else -> glideTo(31f, wy(34.0), cover, clear = true)
            }
        }
        fun toScreen(x: Float, y: Float) = Offset((x - cx) * scale + w / 2f, (y - cy) * scale + h / 2f)

        /** The place under [at]: a named one (its dot or its label) first, else the nearest dot. */
        fun hitPlace(at: Offset, reach: Float): AtlasPlace? {
            // On a label counts as on the place; otherwise the distance to its dot.
            fun gap(v: Pair<Offset, Rect>) = if (v.second.inflate(reach / 4f).contains(at)) 0f else (v.first - at).getDistance()
            val byName = named.entries.filter { gap(it.value) <= reach }.minByOrNull { gap(it.value) }?.key
            byName?.let { id -> d.places.firstOrNull { it.id == id } }?.let { return it }
            return d.places.take(600).minByOrNull { p -> (toScreen(p.x, p.y) - at).getDistance() }
                ?.takeIf { (toScreen(it.x, it.y) - at).getDistance() <= reach }
        }
        fun zoomAt(p: Offset, factor: Float) {
            val wx = (p.x - w / 2f) / scale + cx
            val wyv = (p.y - h / 2f) / scale + cy
            scale = (scale * factor).coerceIn(fit * MIN_ZOOM, fit * 40f)
            cx = wx - (p.x - w / 2f) / scale
            cy = wyv - (p.y - h / 2f) / scale
            clampCentre()
        }

        /** Zoom about the centre in steps, as the + and - buttons and keys do, gliding. */
        fun zoomStep(factor: Float) = glideTo(cx, cy, (scale * factor).coerceIn(fit * MIN_ZOOM, fit * 40f))

        // The base map in its own layer: it reads the view only while drawing, so a new year
        // or selection redraws the overlay alone and the base is composited from its texture.
        BaseMap(remember(land, coast, lakes, rivers, world) { BaseLayers(land, coast, lakes, rivers, world) }) { Triple(cx, cy, scale) }
        Canvas(
            // Clipped: a Canvas draws outside its bounds, over the header and chips.
            Modifier.fillMaxSize().clipToBounds()
                .semantics { contentDescription = if (tamil) "வேதாகம வரைபடம்" else "Bible atlas" }
                .focusRequester(mapFocus)
                .focusable()
                .onKeyEvent { e ->
                    // Keyboard on ALOS and tablets (M8-5d): arrows pan, + and - zoom.
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = keyStepPx / scale
                    when (e.key) {
                        Key.DirectionLeft -> { cx -= step; clampCentre(); true }
                        Key.DirectionRight -> { cx += step; clampCentre(); true }
                        Key.DirectionUp -> { cy -= step; clampCentre(); true }
                        Key.DirectionDown -> { cy += step; clampCentre(); true }
                        Key.Plus, Key.Equals, Key.NumPadAdd -> { zoomStep(1.6f); true }
                        Key.Minus, Key.NumPadSubtract -> { zoomStep(1 / 1.6f); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    // Pinch and pan, and a fling that carries on after a one-finger flick (M8-5a).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        motion?.cancel()
                        val tracker = VelocityTracker()
                        var multi = false
                        // A two-finger tap zooms out (the map convention): two fingers, briefly, barely moving.
                        val started = currentEvent.changes.first().uptimeMillis
                        var ended = started
                        var travel = 0f
                        var zoomed = 1f
                        var tapAt = Offset.Zero
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            ended = e.changes.first().uptimeMillis
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) { multi = true; tapAt = e.calculateCentroid() }
                            val zoom = e.calculateZoom()
                            val pan = e.calculatePan()
                            travel += pan.getDistance()
                            zoomed *= zoom
                            if (zoom != 1f) zoomAt(e.calculateCentroid(), zoom)
                            if (pan != Offset.Zero) {
                                cx -= pan.x / scale
                                cy -= pan.y / scale
                                clampCentre()
                                e.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            pressed.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                        }
                        if (multi && ended - started < 300 && travel < 24.dp.toPx() && abs(zoomed - 1f) < 0.08f) {
                            zoomAt(tapAt, 1 / 1.6f)
                            return@awaitEachGesture
                        }
                        val v = tracker.calculateVelocity()
                        if (!multi && (abs(v.x) > 300f || abs(v.y) > 300f)) {
                            motion = scope.launch {
                                var last = Offset.Zero
                                AnimationState(Offset.VectorConverter, Offset.Zero, Offset(v.x, v.y))
                                    .animateDecay(exponentialDecay(frictionMultiplier = 1.6f)) {
                                        val d = value - last
                                        last = value
                                        cx -= d.x / scale
                                        cy -= d.y / scale
                                        clampCentre()
                                    }
                            }
                        }
                    }
                }
                .pointerInput(d) {
                    detectTapGestures(
                        onDoubleTap = { zoomAt(it, 2f) },
                        onTap = { tap ->
                            onSelect(hitPlace(tap, 24.dp.toPx())?.id)
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
                            } else if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                // Right-click (M8-5d): a menu for the nearest place and the spot.
                                val at = e.changes.first().position
                                menuAt = at
                                menuPlace = hitPlace(at, 48.dp.toPx())
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
            withTransform({
                translate(w / 2f - cx * scale, h / 2f - cy * scale)
                scale(scale, scale, Offset.Zero)
            }) {
                val px = 1f / scale
                // Kingdoms of the chosen year, each in its own colour (M8-5c).
                kingdoms.forEach { k ->
                    val col = polityColor(k.id, c.isDark)
                    drawPath(k.path, col.copy(alpha = 0.16f), style = Fill)
                    drawPath(k.path, col.copy(alpha = 0.7f), style = Hairline)
                }
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
            named.clear()
            /** Where a label would go, or null when it would overlap one already placed. */
            fun place(
                key: String, text: String, at: Offset, ink: androidx.compose.ui.graphics.Color = c.ink, force: Boolean = false, centred: Boolean = false,
            ): Pair<Rect, Pair<TextLayoutResult, TextLayoutResult>>? {
                val layouts = labels.getOrPut(key) {
                    measurer.measure(text, labelStyle.copy(color = ink)) to measurer.measure(text, labelStyle.copy(color = c.mapLand))
                }
                val (layout, _) = layouts
                val left = if (centred) at.x - layout.size.width / 2f else at.x + dot + 4f
                val r = Rect(left, at.y - layout.size.height / 2f, left + layout.size.width, at.y + layout.size.height / 2f)
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
            fun label(key: String, text: String, at: Offset, centred: Boolean = false) = place(key, text, at, centred = centred)?.let(::drawLabel) != null
            if (kingdoms.isNotEmpty() || church.isNotEmpty()) {
                // Kingdom names, the widest first; or the early church's people and places.
                kingdoms.sortedByDescending { it.span }.forEach { k ->
                    val s = toScreen(k.x, k.y)
                    if (s.x in -40f..(w + 40f) && s.y in -40f..(h + 40f)) {
                        label("k-" + k.id + k.from, if (tamil) k.nameTa.ifBlank { k.nameEn } else k.nameEn, s, centred = true)
                    }
                }
                // Every marker first, then the names, so no marker covers a name.
                church.forEach { p ->
                    val s = toScreen(p.x, p.y)
                    drawRect(c.surface, topLeft = s - Offset(dot + 1.5f, dot + 1.5f), size = androidx.compose.ui.geometry.Size(2 * dot + 3f, 2 * dot + 3f))
                    drawRect(c.amber, topLeft = s - Offset(dot, dot), size = androidx.compose.ui.geometry.Size(2 * dot, 2 * dot))
                }
                church.forEach { p -> label("c-" + p.id, if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, toScreen(p.x, p.y)) }
            } else if (stops.isEmpty()) {
                // Places in order of how often they are named: the selected one first, then a
                // label wherever there is room; drawn as small dots, labelled dots, labels.
                val sel = d.places.firstOrNull { it.id == selected }
                val selSpot = sel?.let { p -> place(p.id + "*", if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, toScreen(p.x, p.y), c.amber, force = true) }
                val labelled = ArrayList<Pair<Offset, Pair<Rect, Pair<TextLayoutResult, TextLayoutResult>>>>()
                val small = ArrayList<Offset>()
                if (sel != null && selSpot != null) named[sel.id] = toScreen(sel.x, sel.y) to selSpot.first
                for (p in d.places) {
                    if (p.id == selected) continue
                    val s = toScreen(p.x, p.y)
                    if (s.x < -20 || s.y < -20 || s.x > w + 20 || s.y > h + 20) continue
                    val spot = place(p.id, if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, s)
                    if (spot != null) { labelled += s to spot; named[p.id] = s to spot.first } else if (scale > fit * 4f && small.size < 400) small += s
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
            } else if (step < 0) {
                stops.forEachIndexed { i, st ->
                    val s = toScreen(st.lon.toFloat(), wy(st.lat))
                    drawCircle(c.surface, 8.dp.toPx(), s)
                    drawCircle(c.amber, 6.5.dp.toPx(), s)
                    val n = measurer.measure("${i + 1}", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.surface))
                    drawText(n, topLeft = s - Offset(n.size.width / 2f, n.size.height / 2f))
                    label("stop-$journey-$i", if (tamil) st.nameTa.ifBlank { st.nameEn } else st.nameEn, s + Offset(4f, 0f))
                }
            } else {
                // On tour (2B): travelled stops gold and joined, the stop it is at orange with a halo, the rest rings.
                val here = step.coerceAtMost(stops.lastIndex)
                val r = 6.dp.toPx()
                val travelled = Path()
                stops.take(here + 1).forEachIndexed { i, st ->
                    val s = toScreen(st.lon.toFloat(), wy(st.lat))
                    if (i == 0) travelled.moveTo(s.x, s.y) else travelled.lineTo(s.x, s.y)
                }
                if (here > 0) drawPath(travelled, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                stops.forEachIndexed { i, st ->
                    if (i == here) return@forEachIndexed
                    val s = toScreen(st.lon.toFloat(), wy(st.lat))
                    if (i < here) {
                        drawCircle(c.mapLand, r + 1.5f, s)
                        drawCircle(c.accent, r, s)
                    } else {
                        drawCircle(c.mapLand, r, s)
                        drawCircle(c.lineStrong, r - 1.dp.toPx(), s, style = Stroke(2.dp.toPx()))
                    }
                }
                // Names: the stop it is at, then its neighbours, then wherever there is room.
                stops.indices.sortedBy { abs(it - here) }.forEach { i ->
                    val st = stops[i]
                    label("stop-$journey-$i", if (tamil) st.nameTa.ifBlank { st.nameEn } else st.nameEn, toScreen(st.lon.toFloat(), wy(st.lat)) + Offset(4f, 0f))
                }
                val s = toScreen(stops[here].lon.toFloat(), wy(stops[here].lat))
                drawCircle(c.amber.copy(alpha = 0.25f), r + 6.dp.toPx(), s)
                drawCircle(c.amber, r + 1.dp.toPx(), s)
            }
        }
        menuAt?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { menuAt = null }, containerColor = c.surface) {
                    menuPlace?.let { p ->
                        DropdownMenuItem(
                            text = { Text((if (tamil) "திற: " else "Open ") + (if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn), color = c.ink) },
                            onClick = { menuAt = null; onSelect(p.id); onOpenPlace(p.id) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (tamil) "இங்கே மையப்படுத்து" else "Centre here", color = c.ink) },
                        onClick = { menuAt = null; glideTo((at.x - w / 2f) / scale + cx, (at.y - h / 2f) / scale + cy, scale) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (tamil) "இங்கே பெரிதாக்கு" else "Zoom in here", color = c.ink) },
                        onClick = { menuAt = null; glideTo((at.x - w / 2f) / scale + cx, (at.y - h / 2f) / scale + cy, (scale * 2f).coerceAtMost(fit * 40f)) },
                    )
                }
            }
        }
        // Zoom and recentre (M8-5f, 2B): for one hand, a mouse, and TalkBack; just above the sheet,
        // and out of the way while an opened sheet leaves no room for them below the search.
        if (maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() >= 172.dp) Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = insets.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val bg = floatColor()
            val ring = Modifier.border(1.dp, c.line2, CircleShape)
            IconBox(TsIcons.Plus, if (tamil) "பெரிதாக்கு" else "Zoom in", { zoomStep(1.6f) }, ring, tint = c.ink, background = bg)
            IconBox(TsIcons.Minus, if (tamil) "சிறிதாக்கு" else "Zoom out", { zoomStep(1 / 1.6f) }, ring, tint = c.ink, background = bg)
            IconBox(TsIcons.Target, if (tamil) "மையப்படுத்து" else "Recentre", { recentre() }, ring, tint = c.accent, iconSize = 20.dp, background = bg)
        }
    }
}
