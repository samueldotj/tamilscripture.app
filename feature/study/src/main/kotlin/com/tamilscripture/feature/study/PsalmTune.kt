package com.tamilscripture.feature.study

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tamilscripture.core.designsystem.component.IconBox
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.tamilscripture.core.designsystem.theme.NotoSans
import com.tamilscripture.core.model.Lyrics
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.PsalmTune
import com.tamilscripture.core.model.PsalterDrawing
import com.tamilscripture.core.model.TuneEvents
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A tune's drawings and timings, loaded together. */
class TuneData(val incipit: PsalterDrawing, val full: PsalterDrawing, val events: TuneEvents)

/**
 * The psalter tune on the psalm's card (design: "Psalter tune"): the first line in notation,
 * play and pause, metre, key, time and progress, and "Full tune". The note sounding is drawn
 * in the accent colour. Audio is the website's pre-rendered MP3 (one stanza on organ),
 * played [stanzas] times with a breath between, with MediaPlayer under transient audio
 * focus so the Bible audio pauses meanwhile. [onPlace] hears the stanza and line being sung.
 * A tap on a note or a word sings from there, and the timeline can be dragged.
 */
@Composable
fun PsalmTuneBlock(tune: PsalmTune, stanzas: Int = 1, lyrics: List<List<List<String>>> = emptyList(), onPlace: (TunePlace?) -> Unit = {}) {
    val graph = LocalAppServices.current.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState<TuneData?>(null, tune.id) {
        val base = "psalter/${tune.id}"
        value = runCatching {
            val (i, f, e) = listOf("incipit.draw.json", "full.draw.json", "events.json").map { graph.study.file("$base/$it") }
            TuneData(PsalterDrawing.parse(i!!), PsalterDrawing.parse(f!!), TuneEvents.parse(e!!))
        }.getOrNull()
    }
    val player = remember(tune.id) { TunePlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var full by remember { mutableStateOf(false) }
    var stanza by remember { mutableIntStateOf(0) }
    var rate by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(playing) {
        while (playing) {
            withFrameNanos { }
            position = player.position()
            val once = data?.events?.durationMs ?: Long.MAX_VALUE
            // The stanza is sung; after the breath the next one starts (the organ's tail is cut).
            if (position >= once + BREATH_MS && stanza + 1 < stanzas) {
                stanza += 1
                player.restart()
                position = 0
            }
            if (!player.isPlaying()) {
                playing = false
                if (player.finished) {
                    position = 0
                    stanza = 0
                }
            }
        }
    }
    val line = data?.events?.takeIf { playing || position > 0 }?.line(position) ?: -1
    LaunchedEffect(stanza, line) { onPlace(if (line < 0) null else TunePlace(stanza, line)) }
    DisposableEffect(Unit) { onDispose { onPlace(null) } }

    var big by remember { mutableStateOf(false) }
    val start: () -> Unit = {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "psalter/${tune.id}.mp3").takeIf { it.exists() }
                    ?: graph.study.bytes("psalter/${tune.id}/audio.mp3")?.let { bytes ->
                        File(context.cacheDir, "psalter").mkdirs()
                        File(context.cacheDir, "psalter/${tune.id}.mp3").apply { writeBytes(bytes) }
                    }
            }
            if (file != null && player.play(file, rate)) playing = true
        }
    }
    val onToggle: () -> Unit = {
        if (playing) {
            player.pause()
            playing = false
        } else {
            start()
        }
    }
    /** Go to [ms] of the whole psalm, in the audio's own clock (stanzas a breath apart). */
    fun seek(ms: Long) {
        val ev = data?.events ?: return
        val (s, at) = ev.place(ms, BREATH_MS, stanzas)
        stanza = s
        position = at
        player.seek(at)
    }
    val onSeek: (Float) -> Unit = { f ->
        val once = data?.events?.durationMs ?: 0
        seek((f * (stanzas * once + (stanzas - 1) * BREATH_MS)).toLong())
    }
    // A tap on a note or its word sings from that note, in the stanza being sung.
    val onNote: (Int) -> Unit = { i ->
        data?.events?.let { ev ->
            seek(stanza * (ev.durationMs + BREATH_MS) + ev.notes[i].on)
            if (!playing) start()
        }
    }
    val onRate: (Float) -> Unit = { rate = it; player.speed(it) }

    PsalmTuneView(
        tune, data, playing, position, full, stanza = stanza, stanzas = stanzas,
        lyrics = lyrics, rate = rate, onRate = onRate, onToggle = onToggle,
        onFull = { full = !full }, onBig = { big = true }, onSeek = onSeek, onNote = onNote,
    )
    // Full screen for singing: the whole tune and its words as large as the screen allows,
    // the same player, the screen kept awake.
    if (big) {
        Dialog(onDismissRequest = { big = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val view = LocalView.current
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            PsalmTuneView(
                tune, data, playing, position, full = true, stanza = stanza, stanzas = stanzas,
                lyrics = lyrics, rate = rate, onRate = onRate, onToggle = onToggle, onFull = {},
                big = true, onBig = { big = false }, onSeek = onSeek, onNote = onNote,
            )
        }
    }
}

/** Where the organ is: the stanza and the line of it being sung. */
data class TunePlace(val stanza: Int, val line: Int)

/** A breath between stanzas, about a beat, as a congregation takes. */
const val BREATH_MS = 700L

/** Slower, as written, faster: the playback speed, pitch kept. */
val TEMPI = listOf(0.85f, 1f, 1.15f)

/**
 * The tune block without loading or audio (screenshot tests pass fixtures). [positionMs] is
 * within [stanza], in the audio's own clock; [lyrics] are the version's words, the current
 * stanza's written under the notes; [rate] is the tempo chosen. [onSeek] takes a point of the
 * timeline (0 to 1) and [onNote] a note tapped in the score.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PsalmTuneView(
    tune: PsalmTune, data: TuneData?, playing: Boolean, positionMs: Long, full: Boolean,
    stanza: Int = 0, stanzas: Int = 1, lyrics: List<List<List<String>>> = emptyList(),
    rate: Float = 1f, onRate: (Float) -> Unit = {}, onToggle: () -> Unit, onFull: () -> Unit,
    /** Full screen for singing ([onBig] closes it); otherwise [onBig] opens it. */
    big: Boolean = false, onBig: (() -> Unit)? = null,
    onSeek: (Float) -> Unit = {}, onNote: (Int) -> Unit = {},
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(12.dp)
    val note = data?.events?.takeIf { playing || positionMs > 0 }?.at(positionMs)
    Column(
        if (big) Modifier.fillMaxSize().background(c.surface).systemBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp) else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (big) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tune.name, style = Ts.type.rowTitle.copy(fontSize = 17.sp), color = c.ink)
                if (stanzas > 1) Text(tr("பத்தி ${stanza + 1} / $stanzas", "Stanza ${stanza + 1} of $stanzas"), style = Ts.type.rowTitle.copy(fontSize = 16.sp), color = c.amber, modifier = Modifier.weight(1f))
                else Box(Modifier.weight(1f))
                IconBox(TsIcons.Close, tr("முழுத் திரையிலிருந்து வெளியேறு", "Exit full screen"), { onBig?.invoke() })
            }
        } else {
            Kicker("${tr("சங்கீதப் பண்", "Psalter tune")} · ${tune.name}")
        }
        Column(
            if (big) Modifier.fillMaxWidth().weight(1f)
            else Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.5.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data != null) {
                val drawing = if (full) data.full else data.incipit
                val ev = data.events
                // The stanza's words under their notes; the syllable being sung is lit.
                val onNotes = lyrics.getOrNull(stanza)?.let { Lyrics.stanzaOnNotes(it, ev.slots) }.orEmpty()
                val sounding = note?.let { ev.notes.indexOf(it) } ?: -1
                val litSyllable = if (sounding < 0) -1 else onNotes.keys.filter { it <= sounding }.maxOrNull() ?: -1
                val words = onNotes.mapNotNull { (i, text) ->
                    val n = ev.notes.getOrNull(i) ?: return@mapNotNull null
                    val x = if (full) n.fullX else n.incipitX
                    val y = if (full) n.fullLyric else n.incipitLyric
                    if (x == null || y == null) null else Word(x, y, text, i == litSyllable)
                }
                val lit = if (full) note?.fullId else note?.incipitId
                val tap: (Float, Float) -> Unit = { x, y ->
                    ev.nearest(x, y, drawing.width, drawing.height, full).takeIf { it >= 0 }?.let(onNote)
                }
                if (big) {
                    // As large as the screen allows: the space left after the controls.
                    Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { Score(drawing, lit, null, words, fit = true, onTap = tap) }
                } else {
                    Score(drawing, lit, if (full) null else 130f, words, onTap = tap)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(64.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.accent).clickable(enabled = data != null, onClick = onToggle)
                        .semantics { contentDescription = if (playing) "Pause" else "Play" },
                    Alignment.Center,
                ) { Icon(if (playing) TsIcons.Pause else TsIcons.Play, null, Modifier.size(18.dp), tint = c.onAccent) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // In the audio's own clock, shown in the listener's: divided by the rate.
                    val once = data?.events?.durationMs ?: 0
                    val total = ((stanzas * once + (stanzas - 1) * BREATH_MS) / rate).toLong()
                    val elapsed = ((stanza * (once + BREATH_MS) + positionMs.coerceAtMost(once + BREATH_MS)) / rate).toLong()
                    Row {
                        val where = if (stanzas > 1) " · " + tr("பத்தி ${stanza + 1}/$stanzas", "stanza ${stanza + 1} of $stanzas") else ""
                        Text("${tune.meter} · ${tune.key}$where", style = Ts.type.caption, color = c.muted, modifier = Modifier.weight(1f))
                        Text("${clock(elapsed)} / ${clock(total)}", style = Ts.type.caption, color = c.muted)
                    }
                    // The timeline: a 4 dp track in a taller box, so a finger can take hold of it.
                    val f = if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val where = tr("பாடலில் இடம்", "Position in the psalm")
                    val label = "${clock(elapsed)} / ${clock(total)}"
                    val seek by rememberUpdatedState(onSeek)
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(24.dp)
                            .pointerInput(data != null) {
                                if (data == null) return@pointerInput
                                detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) }
                            }
                            .pointerInput(data != null) {
                                if (data == null) return@pointerInput
                                detectHorizontalDragGestures { change, _ -> seek((change.position.x / size.width).coerceIn(0f, 1f)) }
                            }
                            .semantics {
                                contentDescription = where
                                stateDescription = label
                                progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
                                setProgress { onSeek(it.coerceIn(0f, 1f)); true }
                            },
                        Alignment.CenterStart,
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.line)) {
                            Box(Modifier.fillMaxWidth(f).height(4.dp).background(c.accent))
                        }
                        Box(Modifier.padding(start = (maxWidth - 12.dp) * f).size(12.dp).clip(CircleShape).background(c.accent))
                    }
                }
            }
            data?.events?.bpm?.let { bpm ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(tr("வேகம்", "Tempo"), style = Ts.type.captionSmall, color = c.muted)
                    TEMPI.forEach { r ->
                        val on = r == rate
                        val label = when {
                            r < 1f -> tr("மெதுவாக", "Slower")
                            r > 1f -> tr("வேகமாக", "Faster")
                            else -> tr("இயல்பு", "Normal")
                        }
                        Text(
                            "$label ♩ = ${(bpm * r).roundToInt()}", style = Ts.type.labelSmall, color = if (on) c.onAccent else c.ink,
                            modifier = Modifier.clip(CircleShape).background(if (on) c.accent else c.surface)
                                .border(1.5.dp, if (on) c.accent else c.line2, CircleShape)
                                .clickable(role = Role.Tab) { onRate(r) }.semantics { selected = on }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Where the melody comes from, and the other names it goes by.
                val also = tune.otherNames.takeIf { it.isNotEmpty() }?.let { tr("வேறு பெயர்: ", "also known as ") + it.joinToString(", ") }
                val credit = listOf(tune.source, tune.composer.takeIf { it != "Anonymous" }, also).filter { !it.isNullOrBlank() }.joinToString(" · ")
                Text(credit, style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.weight(1f))
                if (!big) Text(
                    if (full) tr("முதல் வரி", "First line") else tr("முழுப் பண்", "Full tune"),
                    style = Ts.type.label, color = c.accent, modifier = Modifier.clickable(onClick = onFull).padding(6.dp),
                )
                if (!big && onBig != null && data != null) {
                    Text(
                        tr("முழுத் திரை", "Full screen"),
                        style = Ts.type.label, color = c.accent, modifier = Modifier.clickable(onClick = onBig).padding(6.dp),
                    )
                }
            }
        }
    }
}

private fun clock(ms: Long) = "${ms / 60000}:${((ms % 60000) / 1000).toString().padStart(2, '0')}"

/** A syllable to write under a note: its x and baseline as fractions of the drawing. */
private class Word(val x: Float, val y: Float, val text: String, val lit: Boolean)

/**
 * The drawing at the width available (no taller than [maxHeightDp]), the note [lit] in the
 * accent colour, [words] under the notes. [onTap] hears a tap in the drawing's units.
 */
@Composable
private fun Score(
    drawing: PsalterDrawing, lit: String?, maxHeightDp: Float?, words: List<Word> = emptyList(), fit: Boolean = false,
    onTap: ((Float, Float) -> Unit)? = null,
) {
    val c = Ts.colors
    val marks = remember(drawing) { marks(drawing) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (fit) Alignment.Center else Alignment.TopStart) {
        val ratio = drawing.height / drawing.width
        var w = maxWidth
        if (maxHeightDp != null && w * ratio > maxHeightDp.dp) w = maxHeightDp.dp / ratio
        // Full screen: no taller than the space given, whichever way the phone is held.
        if (fit && w * ratio > maxHeight) w = maxHeight / ratio
        // Keyed on the drawing only: the frames of playing must not restart the gesture.
        val current by rememberUpdatedState(onTap)
        val tap = Modifier.pointerInput(drawing) {
            detectTapGestures { p -> current?.invoke(p.x * drawing.width / size.width, p.y * drawing.width / size.width) }
        }
        Canvas(Modifier.width(w).height(w * ratio).then(tap).semantics { contentDescription = "Musical notation" }) {
            val s = size.width / drawing.width
            withTransform({ scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                for (m in marks) {
                    val color = if (lit != null && m.note == lit) c.accent else c.ink
                    drawPath(m.path, color, style = Fill)
                    if (m.stroke != null) drawPath(m.path, color, style = Stroke(m.stroke))
                }
            }
            // Words in the drawing's units (300 high, as on the website), placed under each notehead.
            val size = with(density) { (300 * s).toSp() }
            for (w in words) {
                val layout = measurer.measure(
                    w.text,
                    TextStyle(fontFamily = NotoSans, fontSize = size, fontWeight = if (w.lit) FontWeight.Bold else FontWeight.Normal, color = if (w.lit) c.accent else c.ink2),
                )
                val cx = (w.x * drawing.width + 80) * s
                val baseline = w.y * drawing.height * s
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, baseline - layout.firstBaseline))
            }
        }
    }
}

private class Mark(val path: Path, val stroke: Float?, val note: String?)

/** Every mark as a Compose path already placed by its matrix; glyph outlines parsed once. */
private fun marks(d: PsalterDrawing): List<Mark> {
    val glyphs = d.glyphs.mapValues { (_, data) -> PathParser().parsePathString(data).toPath() }
    return d.items.mapNotNull { m ->
        val source = m.glyph?.let { glyphs[it] } ?: m.path?.let { PathParser().parsePathString(it).toPath() } ?: return@mapNotNull null
        val (a, b, cc, dd, e, f) = m.matrix
        val matrix = Matrix().apply {
            values[Matrix.ScaleX] = a; values[Matrix.SkewY] = b
            values[Matrix.SkewX] = cc; values[Matrix.ScaleY] = dd
            values[Matrix.TranslateX] = e; values[Matrix.TranslateY] = f
        }
        Mark(Path().apply { addPath(source); transform(matrix) }, m.stroke, m.note)
    }
}

private operator fun List<Float>.component6() = this[5]

/** MediaPlayer for one tune, with transient audio focus while it plays. */
private class TunePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
    private var mp: MediaPlayer? = null
    var finished = false
        private set

    /** Plays at [rate] (1 is as written), pitch kept. */
    fun play(file: File, rate: Float = 1f): Boolean {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        val p = mp ?: MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(file.path)
            prepare()
            setOnCompletionListener { finished = true; audio.abandonAudioFocusRequest(focus) }
        }.also { mp = it }
        if (finished) p.seekTo(0)
        pending?.let { p.seekTo(it, MediaPlayer.SEEK_CLOSEST) }
        pending = null
        finished = false
        p.start()
        speed(rate)
        return true
    }

    fun pause() {
        mp?.takeIf { it.isPlaying }?.pause()
        audio.abandonAudioFocusRequest(focus)
    }

    private var rate = 1f

    /** The tempo: applied now while playing, else when play starts (setting it on a paused
     *  MediaPlayer would start it). */
    fun speed(r: Float) {
        rate = r
        val p = mp ?: return
        if (p.isPlaying) p.playbackParams = p.playbackParams.setSpeed(r)
    }

    /** Back to the start for the next stanza, still playing. */
    fun restart() {
        mp?.seekTo(0)
    }

    private var pending: Long? = null

    /** To [ms] of the stanza, playing or not; before the first play, from where play starts. */
    fun seek(ms: Long) {
        finished = false
        val p = mp
        if (p == null) pending = ms else p.seekTo(ms, MediaPlayer.SEEK_CLOSEST)
    }

    fun isPlaying() = mp?.isPlaying == true
    fun position(): Long = if (finished) 0 else mp?.currentPosition?.toLong() ?: 0

    fun release() {
        mp?.release()
        mp = null
        audio.abandonAudioFocusRequest(focus)
    }
}
