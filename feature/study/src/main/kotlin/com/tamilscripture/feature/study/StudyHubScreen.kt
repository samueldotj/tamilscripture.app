package com.tamilscripture.feature.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.TsBadge
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.services.tr

class StudyNav(val commentary: () -> Unit, val dictionary: () -> Unit, val rootWords: () -> Unit, val atlas: () -> Unit, val soon: (String) -> Unit)

/** ஆய்வு · Study hub (design 1G). */
@Composable
fun StudyHubScreen(nav: StudyNav) {
    val c = Ts.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Text(tr("ஆய்வு", "Study"), style = Ts.type.screenTitle, color = c.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mod = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            val psalms = tr("சங்கீத ஆய்வு", "Psalm explorer")
            PsalmCard(mod) { nav.soon(psalms) }
            AtlasCard(mod, nav.atlas)
            Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dict = tr("அகராதி", "Dictionary")
                Tile(dict, tr("ஈஸ்டன் · ஸ்மித் · அக்வைஃபர்", "Easton · Smith · Aquifer"), Modifier.weight(1f), onClick = nav.dictionary)
                Tile(tr("விளக்கவுரை", "Commentary"), "M. Henry · Calvin · Geneva", Modifier.weight(1f), onClick = nav.commentary)
            }
            Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val roots = tr("மூலச்சொல்", "Root words")
                Tile(roots, tr("எபிரெயம் · கிரேக்கம்", "Hebrew · Greek"), Modifier.weight(1f), onClick = nav.rootWords)
                val contrib = tr("என் பங்களிப்புகள்", "My contributions")
                Tile(contrib, tr("உள்நுழைவுடன்", "With sign-in"), Modifier.weight(1f), soon = true) { nav.soon(contrib) }
            }
        }
    }
}

@Composable
private fun PsalmCard(mod: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(20.dp)
    // Genre counts from the website's Psalm Explorer.
    val bars = listOf(41 to c.accent, 30 to c.amber, 25 to c.brand, 18 to Color(0xFF7D8C5A), 14 to Color(0xFF8A6E9C), 12 to c.muted, 10 to c.lineStrong)
    Column(mod.clip(shape).background(c.surface2).border(1.5.dp, c.line, shape).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("சங்கீத ஆய்வு", "Psalm explorer"), style = Ts.type.cardTitle.copy(fontSize = 18.sp), color = c.ink)
                Text(tr("150 சங்கீதங்கள் · வகை × கருப்பொருள்", "150 psalms · genre × theme"), style = Ts.type.caption, color = c.muted)
            }
            TsBadge(tr("விரைவில்", "Soon"), tinted = false)
        }
        Row(Modifier.fillMaxWidth().height(76.dp).padding(start = 18.dp, end = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bars.forEach { (n, col) ->
                Box(Modifier.weight(n.toFloat()).fillMaxHeight(n / 41f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(col))
            }
        }
        // Scrolls sideways on a phone rather than squeezing the last badge into a column.
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TsBadge(tr("புலம்பல் 41", "Lament 41"))
            TsBadge(tr("துதி 30", "Praise 30"), tinted = false)
            TsBadge(tr("நன்றி 25", "Thanks 25"), tinted = false)
            TsBadge(tr("அரச 18", "Royal 18"), tinted = false)
        }
    }
}

@Composable
private fun AtlasCard(mod: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(20.dp)
    Box(mod.height(210.dp).clip(shape).background(c.mapSea).border(1.5.dp, c.line, shape).clickable(onClick = onClick)) {
        val land = c.mapLand
        val route = c.accent
        val stop = c.amber
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val coast = Path().apply {
                moveTo(0f, h * 0.25f)
                cubicTo(w * 0.25f, h * 0.15f, w * 0.45f, h * 0.35f, w * 0.7f, h * 0.22f)
                cubicTo(w * 0.85f, h * 0.15f, w * 0.95f, h * 0.3f, w, h * 0.28f)
                lineTo(w, 0f); lineTo(0f, 0f); close()
            }
            drawPath(coast, land)
            val east = Path().apply {
                moveTo(w * 0.86f, h * 0.35f)
                cubicTo(w * 0.8f, h * 0.55f, w * 0.82f, h * 0.8f, w * 0.78f, h)
                lineTo(w, h); lineTo(w, h * 0.35f); close()
            }
            drawPath(east, land)
            val a = Offset(w * 0.84f, h * 0.48f)
            val b = Offset(w * 0.55f, h * 0.3f)
            val d = Offset(w * 0.32f, h * 0.36f)
            drawLine(route, a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
            drawLine(route, b, d, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
            listOf(a, b).forEach {
                drawCircle(route.copy(alpha = 0.25f), 9.dp.toPx(), it)
                drawCircle(route, 5.dp.toPx(), it)
            }
            drawCircle(stop, 4.dp.toPx(), d)
            drawCircle(stop.copy(alpha = 0.6f), 8.dp.toPx(), d, style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("வரைபடம்", "Atlas"), style = Ts.type.cardTitle.copy(fontSize = 18.sp), color = c.ink)
            }
            Text(tr("பயணங்கள் · இடங்கள்", "Journeys · places"), style = Ts.type.caption, color = c.ink2)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(c.surface2.copy(alpha = 0.9f)).border(1.dp, c.line2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("பவுலின் 2-ஆம் பயணம்", "Paul's second journey"), style = Ts.type.label, color = c.ink)
                Text(tr("அந்தியோகியா → கொரிந்து · அப் 15:36–18:22", "Antioch → Corinth · Acts 15:36–18:22"), style = Ts.type.captionSmall, color = c.muted)
            }
            Icon(TsIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.accent)
        }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, modifier: Modifier, soon: Boolean = false, dashed: Boolean = false, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (dashed) c.surface2 else c.surface)
            .then(
                if (dashed) Modifier.dashedBorder(c.line2, 16.dp) else Modifier.border(1.5.dp, c.line, shape),
            )
            .clickable(enabled = !dashed, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = Ts.type.cardTitle, color = if (dashed) c.muted else c.ink, modifier = Modifier.weight(1f, fill = false))
            if (soon) TsBadge(tr("விரைவில்", "Soon"), tinted = false)
        }
        Text(subtitle, style = Ts.type.caption, color = if (dashed) c.faint else c.muted, maxLines = 1)
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    drawRoundRect(
        color,
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}
