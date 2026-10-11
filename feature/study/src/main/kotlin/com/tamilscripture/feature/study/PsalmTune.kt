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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
 * in the accent colour. Audio is the website's pre-rendered MP3, played with MediaPlayer
 * under transient audio focus so the Bible audio pauses meanwhile.
 */
@Composable
fun PsalmTuneBlock(tune: PsalmTune) {
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

    LaunchedEffect(playing) {
        while (playing) {
            withFrameNanos { }
            position = player.position()
            if (!player.isPlaying()) {
                playing = false
                if (player.finished) position = 0
            }
        }
    }

    PsalmTuneView(
        tune, data, playing, position, full,
        onToggle = {
            if (playing) {
                player.pause()
                playing = false
            } else {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "psalter/${tune.id}.mp3").takeIf { it.exists() }
                            ?: graph.study.bytes("psalter/${tune.id}/audio.mp3")?.let { bytes ->
                                File(context.cacheDir, "psalter").mkdirs()
                                File(context.cacheDir, "psalter/${tune.id}.mp3").apply { writeBytes(bytes) }
                            }
                    }
                    if (file != null && player.play(file)) playing = true
                }
            }
        },
        onFull = { full = !full },
    )
}

/** The tune block without loading or audio (screenshot tests pass fixtures). */
@Composable
fun PsalmTuneView(tune: PsalmTune, data: TuneData?, playing: Boolean, positionMs: Long, full: Boolean, onToggle: () -> Unit, onFull: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(12.dp)
    val note = data?.events?.takeIf { playing || positionMs > 0 }?.at(positionMs)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker("${tr("சங்கீதப் பண்", "Psalter tune")} · ${tune.name}")
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.5.dp, c.line, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data != null) {
                val drawing = if (full) data.full else data.incipit
                Score(drawing, if (full) note?.fullId else note?.incipitId, if (full) null else 110f)
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
                    val total = data?.events?.durationMs ?: 0
                    Row {
                        Text("${tune.meter} · ${tune.key}", style = Ts.type.caption, color = c.muted, modifier = Modifier.weight(1f))
                        Text("${clock(positionMs)} / ${clock(total)}", style = Ts.type.caption, color = c.muted)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.line)) {
                        val f = if (total > 0) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(f).height(4.dp).background(c.accent))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val credit = listOf(tune.source, tune.composer.takeIf { it != "Anonymous" }).filter { !it.isNullOrBlank() }.joinToString(" · ")
                Text(credit, style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.weight(1f))
                Text(
                    if (full) tr("முதல் வரி", "First line") else tr("முழுப் பண்", "Full tune"),
                    style = Ts.type.label, color = c.accent, modifier = Modifier.clickable(onClick = onFull).padding(6.dp),
                )
            }
        }
    }
}

private fun clock(ms: Long) = "${ms / 60000}:${((ms % 60000) / 1000).toString().padStart(2, '0')}"

/** The drawing at the width available (no taller than [maxHeightDp]), the note [lit] in the accent colour. */
@Composable
private fun Score(drawing: PsalterDrawing, lit: String?, maxHeightDp: Float?) {
    val c = Ts.colors
    val marks = remember(drawing) { marks(drawing) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ratio = drawing.height / drawing.width
        var w = maxWidth
        if (maxHeightDp != null && w * ratio > maxHeightDp.dp) w = maxHeightDp.dp / ratio
        Canvas(Modifier.width(w).height(w * ratio).semantics { contentDescription = "Musical notation" }) {
            val s = size.width / drawing.width
            withTransform({ scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                for (m in marks) {
                    val color = if (lit != null && m.note == lit) c.accent else c.ink
                    drawPath(m.path, color, style = Fill)
                    if (m.stroke != null) drawPath(m.path, color, style = Stroke(m.stroke))
                }
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

    fun play(file: File): Boolean {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        val p = mp ?: MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(file.path)
            prepare()
            setOnCompletionListener { finished = true; audio.abandonAudioFocusRequest(focus) }
        }.also { mp = it }
        if (finished) p.seekTo(0)
        finished = false
        p.start()
        return true
    }

    fun pause() {
        mp?.takeIf { it.isPlaying }?.pause()
        audio.abandonAudioFocusRequest(focus)
    }

    fun isPlaying() = mp?.isPlaying == true
    fun position(): Long = if (finished) 0 else mp?.currentPosition?.toLong() ?: 0

    fun release() {
        mp?.release()
        mp = null
        audio.abandonAudioFocusRequest(focus)
    }
}
