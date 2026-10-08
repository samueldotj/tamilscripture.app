package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.SheetHandle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Journey

/** The sheet's colour: the design's #20232A on dark, the card white on light. */
@Composable
internal fun sheetColor(): Color = Ts.colors.let { if (it.isDark) it.surface2 else it.surface }

/**
 * The atlas's bottom sheet (2B): a handle that a tap or a drag opens and closes, then [content].
 * [onSwipe] gets -1 or +1 for a sideways swipe across the handle and header.
 */
@Composable
internal fun MapSheet(
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSwipe: ((Int) -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(modifier.shadow(24.dp, shape).clip(shape).background(sheetColor()).navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth()
                .pointerInput(expanded) {
                    var dy = 0f
                    detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = {
                        if (dy < -24.dp.toPx()) onExpand(true) else if (dy > 24.dp.toPx()) onExpand(false)
                    }) { _, d -> dy += d }
                }
                .then(
                    if (onSwipe == null) Modifier else Modifier.pointerInput(onSwipe) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                            if (dx < -56.dp.toPx()) onSwipe(1) else if (dx > 56.dp.toPx()) onSwipe(-1)
                        }) { _, d -> dx += d }
                    },
                ),
        ) {
            SheetHandle(
                Modifier.clickable(role = Role.Button) { onExpand(!expanded) }.semantics {
                    contentDescription = "⇕"
                    stateDescription = if (expanded) "expanded" else "collapsed"
                },
            )
            header()
        }
        content()
    }
}

/** A stop's dot: travelled (gold), here (orange, its row lit), or still ahead (a ring). */
internal enum class StopState { Done, Here, Ahead }

@Composable
private fun StopDot(state: StopState) {
    val c = Ts.colors
    val m = Modifier.size(10.dp).clip(CircleShape)
    Box(
        when (state) {
            StopState.Done -> m.background(c.accent)
            StopState.Here -> m.background(c.amber)
            StopState.Ahead -> m.border(2.dp, c.lineStrong, CircleShape)
        },
    )
}

@Composable
private fun StopRow(state: StopState, title: String, caption: String, onClick: (() -> Unit)?, chevron: Boolean = false) {
    val c = Ts.colors
    Row(
        Modifier.fillMaxWidth()
            .background(if (state == StopState.Here) c.amber.copy(alpha = 0.08f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StopDot(state)
        Column(Modifier.weight(1f)) {
            Text(title, style = Ts.type.rowTitle, color = if (state == StopState.Ahead) c.ink2 else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (caption.isNotBlank()) Text(caption, style = Ts.type.caption, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) Text("›", fontSize = 16.sp, color = c.accent)
    }
}

@Composable
private fun RowDivider() = HDivider()

/**
 * The journey sheet (2B): which journey of its group, its passages and length, a way to be
 * led along it stop by stop, and the stops — what is behind, where the tour is, what is ahead.
 * Expanded, every stop is listed (tap one to go there) with the group's other journeys.
 */
@Composable
internal fun JourneySheet(
    j: Journey,
    group: JourneyGroup?,
    tamil: Boolean,
    /** "அப்போஸ்தலர் 15:36 – 18:22 · ≈ 4,500 கி.மீ" */
    subtitle: String,
    /** "ACT.15.40" as a reader writes it. */
    refLabel: (String) -> String,
    step: Int,
    playing: Boolean,
    expanded: Boolean,
    maxHeight: Dp,
    onExpand: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onStep: (Int) -> Unit,
    onOpenPlace: (String) -> Unit,
    onJourney: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ts.colors
    val stops = j.stops
    fun name(i: Int) = stops[i].let { if (tamil) it.nameTa.ifBlank { it.nameEn } else it.nameEn }
    val at = group?.journeys?.indexOfFirst { it.id == j.id } ?: -1
    val n = group?.journeys?.size ?: 0
    MapSheet(
        expanded, onExpand, modifier.heightIn(max = maxHeight),
        onSwipe = if (group != null && n > 1) { d -> onJourney(group.journeys[(at + d + n) % n].id) } else null,
        header = {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val over = listOfNotNull(
                        if (n > 1 && at >= 0) (if (tamil) "பயணம் " else "Journey ") + "${at + 1} / $n" else null,
                        periodName(j.period, tamil).takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    // Tamil is not letter-spaced: spread out, its vowel signs read as letters of their own.
                    if (over.isNotEmpty()) Text(if (tamil) over else over.uppercase(), style = if (tamil) Ts.type.kicker.copy(letterSpacing = 0.sp) else Ts.type.kicker, color = c.accent)
                    Text(
                        if (tamil) j.nameTa.ifBlank { j.nameEn } else j.nameEn,
                        style = Ts.type.headline.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.ink,
                    )
                    Text(subtitle, style = Ts.type.caption.copy(fontSize = 13.sp), color = c.muted)
                }
                if (stops.size > 1) TsPillButton(
                    if (playing) (if (tamil) "❚❚ நிறுத்து" else "❚❚ Pause") else (if (tamil) "▶ வழிநடத்து" else "▶ Guide me"),
                    onPlay, height = 40.dp, textStyle = Ts.type.labelSmall,
                    style = if (playing) PillStyle.Tinted else PillStyle.Outlined,
                )
            }
        },
    ) {
        RowDivider()
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (expanded) {
                val summary = if (tamil) j.summaryTa.ifBlank { j.summaryEn } else j.summaryEn
                if (summary.isNotBlank()) Text(summary, style = Ts.type.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = c.ink2, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                stops.indices.forEach { i ->
                    if (i > 0 || summary.isNotBlank()) RowDivider()
                    val state = if (step < 0 || i > step) StopState.Ahead else if (i == step) StopState.Here else StopState.Done
                    StopRow(
                        if (step < 0) StopState.Done else state, "${i + 1}. ${name(i)}", stops[i].ref?.let(refLabel).orEmpty(),
                        onClick = { if (i == step && stops[i].place.isNotBlank()) onOpenPlace(stops[i].place) else onStep(i) },
                        chevron = i == step && stops[i].place.isNotBlank(),
                    )
                }
                if (group != null && n > 1) {
                    RowDivider()
                    Text(
                        if (tamil) "இதே தொகுப்பில்" else "In this group", style = Ts.type.kicker, color = c.muted,
                        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.journeys.forEach { o ->
                            TsChip(if (tamil) o.nameTa.ifBlank { o.nameEn } else o.nameEn, o.id == j.id, { onJourney(o.id) })
                        }
                    }
                }
            } else {
                // Collapsed: what is behind as one row, where the tour is, and what is ahead.
                val here = step.coerceIn(-1, stops.lastIndex)
                fun refs(a: Int, b: Int): String {
                    val r1 = stops[a].ref ?: return stops[b].ref?.let(refLabel).orEmpty()
                    val r2 = stops[b].ref ?: return refLabel(r1)
                    val l1 = refLabel(r1)
                    val l2 = refLabel(r2)
                    if (a == b || l1 == l2) return l1
                    // "அப் 15:40–16:8": the second reference without the book they share.
                    val book = l1.substringBeforeLast(' ')
                    return if (l2.startsWith("$book ")) l1 + "–" + l2.removePrefix("$book ") else "$l1 – $l2"
                }
                // Before the tour starts, the journey in a sentence above its first stops.
                val summary = if (tamil) j.summaryTa.ifBlank { j.summaryEn } else j.summaryEn
                if (here < 0 && summary.isNotBlank()) Text(
                    summary, style = Ts.type.caption.copy(fontSize = 13.sp, lineHeight = 19.sp), color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                )
                var first = here < 0 && summary.isBlank()
                if (here > 0) {
                    first = false
                    StopRow(
                        StopState.Done, if (here == 1) name(0) else name(0) + " → " + name(here - 1),
                        refs(0, here - 1), onClick = { onExpand(true) },
                    )
                }
                if (here >= 0) {
                    if (!first) RowDivider()
                    first = false
                    val st = stops[here]
                    StopRow(
                        StopState.Here, name(here),
                        listOfNotNull((if (tamil) st.nameEn else st.nameTa).takeIf { it.isNotBlank() && it != name(here) }, st.ref?.let(refLabel)).joinToString(" · "),
                        onClick = if (st.place.isNotBlank()) ({ onOpenPlace(st.place) }) else null, chevron = st.place.isNotBlank(),
                    )
                }
                val ahead = (here + 1..stops.lastIndex).toList()
                if (ahead.isNotEmpty()) {
                    if (!first) RowDivider()
                    val shown = ahead.take(3)
                    val more = ahead.size - shown.size
                    StopRow(
                        if (here < 0) StopState.Done else StopState.Ahead,
                        shown.joinToString(" · ") { name(it) },
                        if (more > 0) (if (tamil) "+ $more நிறுத்தங்கள்" else "+ $more more stops") else refs(shown.first(), shown.last()),
                        onClick = { onExpand(true) },
                    )
                }
            }
        }
    }
}
