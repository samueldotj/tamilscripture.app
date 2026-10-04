package com.tamilscripture.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.MapDrawing
import com.tamilscripture.core.model.MapLabel
import kotlin.math.max

/**
 * A static map from the website (chapter, place or journey; roadmap M8-4) drawn natively,
 * styled as the website's `.map` CSS: land, lakes, rivers and a journey's dashed route,
 * places as dots with Tamil or English labels. Tapping near a place reports its id.
 */
@Composable
fun SchematicMap(
    drawing: MapDrawing,
    tamil: Boolean,
    modifier: Modifier = Modifier,
    selected: String? = null,
    description: String = "",
    onPlace: ((String) -> Unit)? = null,
) {
    val c = Ts.colors
    val measurer = rememberTextMeasurer()
    val paths = remember(drawing) { drawing.paths.map { (cls, d) -> cls to PathParser().parsePathString(d).toPath() } }
    val labelFamily = if (tamil) Ts.type.scripture else Ts.type.label.fontFamily
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(drawing.width / drawing.height)
            .clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = description }
            .then(
                if (onPlace == null) Modifier else Modifier.pointerInput(drawing) {
                    detectTapGestures { tap ->
                        val k = size.width / drawing.width
                        val hit = drawing.places.minByOrNull { p -> (Offset(p.x * k, p.y * k) - tap).getDistance() }
                        if (hit != null && (Offset(hit.x * k, hit.y * k) - tap).getDistance() <= 24.dp.toPx()) onPlace(hit.id)
                    }
                },
            ),
    ) {
        val k = size.width / drawing.width
        drawRect(c.mapSea)
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach { (cls, p) ->
                when (cls) {
                    "land" -> {
                        drawPath(p, c.mapLand, style = Fill)
                        drawPath(p, c.mapCoast, style = Stroke(1f, join = StrokeJoin.Round))
                    }
                    "lake" -> {
                        drawPath(p, c.mapLake, style = Fill)
                        drawPath(p, c.mapCoast, style = Stroke(0.6f))
                    }
                    "river" -> drawPath(p, c.mapRiver, style = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    "route" -> drawPath(
                        p, c.accent.copy(alpha = 0.85f),
                        style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    )
                }
            }
        }
        drawing.places.forEach { p ->
            val centre = Offset(p.x * k, p.y * k)
            val on = p.id == selected
            val r = (if (on) 7f else p.r) * k
            drawCircle(c.surface, r + 1.6f * k, centre)
            drawCircle(if (p.em || on) c.amber else c.accent, r, centre)
            p.number?.let { n ->
                val style = TextStyle(fontSize = (9f * k).toSp(), fontWeight = FontWeight.Bold, color = if (p.em) c.surface else c.onAccent)
                val layout = measurer.measure(n, style)
                drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f))
            }
        }
        // Labels after every dot, so no place's dot covers another's name.
        // Labels keep the website's size relative to the map, but never under 11 sp on screen:
        // large maps (800 units wide) would otherwise shrink them below reading size on a phone.
        val labelPx = max((if (tamil) 12.5f else 12f) * k, 11.sp.toPx())
        drawing.places.forEach { p ->
            val on = p.id == selected
            if (!p.crowded || on) {
                (if (tamil) p.ta ?: p.en else p.en ?: p.ta)?.let { label ->
                    drawLabel(measurer, label, k, TextStyle(fontFamily = labelFamily, fontSize = labelPx.toSp(), fontWeight = FontWeight.SemiBold), if (on) c.amber else c.ink, c.mapLand)
                }
            }
        }
        drawing.credit?.let { credit ->
            // A long credit (journeys list their route sources) shrinks to fit the width.
            val full = measurer.measure(credit, TextStyle(fontSize = (9f * k).toSp()))
            val fit = (size.width - 12f * k) / full.size.width
            val layout = measurer.measure(credit, TextStyle(fontSize = (9f * k * fit.coerceAtMost(1f)).toSp(), color = c.muted))
            drawText(layout, topLeft = Offset(size.width - layout.size.width - 6f * k, size.height - layout.size.height - 3f * k))
        }
    }
}

/** A label on its SVG baseline and anchor, with the website's halo in the land colour. */
private fun DrawScope.drawLabel(measurer: TextMeasurer, label: MapLabel, k: Float, style: TextStyle, ink: Color, halo: Color) {
    val fill = measurer.measure(label.text, style.copy(color = ink))
    // The halo is the label in the land colour drawn around itself: stroked text is not
    // drawn in its colour everywhere (Robolectric draws it in black).
    val outline = measurer.measure(label.text, style.copy(color = halo))
    val x = label.x * k - when (label.anchor) {
        "middle" -> fill.size.width / 2f
        "end" -> fill.size.width.toFloat()
        else -> 0f
    }
    val topLeft = Offset(x, label.y * k - fill.firstBaseline)
    val d = 1.5f * k
    for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) drawText(outline, topLeft = topLeft + Offset(dx * d, dy * d))
    drawText(fill, topLeft = topLeft)
}
