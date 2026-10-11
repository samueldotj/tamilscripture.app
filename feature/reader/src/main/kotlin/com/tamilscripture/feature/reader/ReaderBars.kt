package com.tamilscripture.feature.reader

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.model.HighlightColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.Avatar
import com.tamilscripture.core.designsystem.component.CrossMark
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.KeyHint
import com.tamilscripture.core.designsystem.component.Pill
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.ReferencePill
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.media.AudioState
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.services.tr

/** Phone top bar (1B): back, reference pill, play, study settings. */
@Composable
fun ReaderTopBar(
    title: String,
    version: String,
    hasAudio: Boolean,
    commentaryOn: Boolean,
    onBack: () -> Unit,
    onPicker: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    comparing: Boolean,
    onCompare: () -> Unit,
    /** "2 கொரி 5": used on a phone, where the full name beside four buttons would be cut. */
    shortTitle: String = title,
) {
    val c = Ts.colors
    androidx.compose.foundation.layout.BoxWithConstraints {
    val shown = if (maxWidth < 480.dp) shortTitle else title
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            ReferencePill(shown, version, onPicker, Modifier.weight(1f))
            if (hasAudio) IconBox(TsIcons.Play, tr("கேள்", "Listen"), onPlay, tint = c.accent, iconSize = 18.dp)
            // Two columns on and off (A-3.3); the Bible itself opens in one.
            IconBox(
                TsIcons.Compare, if (comparing) tr("ஒப்பீட்டை மூடு", "Stop comparing") else tr("ஒப்பிடு", "Compare"), onCompare,
                tint = if (comparing) c.accent else c.ink2, badge = comparing,
            )
            IconBox(
                TsIcons.StudySettings, tr("ஆய்வு அமைப்பு", "Study settings"), onSettings,
                tint = if (commentaryOn) c.accent else c.ink2, badge = commentaryOn,
            )
        }
        HDivider(thickness = 1.5.dp)
    }
    }
}

/** Commentary source chips shown under the top bar while inline commentary is on (1C). */
@Composable
fun CommentaryChipRow(sources: List<CommentarySource>, selected: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { s -> TsChip(s.short, s.id == selected, { onSelect(s.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp)) }
        }
        TsPillButton(tr("மூடு", "Close"), onClick = onClose, style = PillStyle.Ghost, height = 34.dp, textStyle = Ts.type.labelSmall)
    }
}

/** Bottom bar: "‹ யோவான் 2 · 3 / 21 · யோவான் 4 ›". */
@Composable
fun ChapterNavBar(
    prevLabel: String?,
    nextLabel: String?,
    position: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    /** Short forms ("2 கொரி 4") used when the full ones would crowd a phone's width. */
    prevShort: String? = prevLabel,
    nextShort: String? = nextLabel,
) {
    val c = Ts.colors
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.background(c.surface2)) {
    val narrow = maxWidth < 480.dp
    val prev = if (narrow) prevShort else prevLabel
    val next = if (narrow) nextShort else nextLabel
    Column {
        HDivider(thickness = 1.5.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (prev != null) TsPillButton("‹ $prev", onPrev) else Box(Modifier.size(1.dp))
            Text(position, style = Ts.type.caption, color = c.muted)
            if (next != null) TsPillButton("$next ›", onNext) else Box(Modifier.size(1.dp))
        }
    }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Audio player bar (1C): progress, play/pause, verse, time left, speed, close. */
@Composable
fun PlayerBar(state: AudioState, title: String, onToggle: () -> Unit, onSpeed: () -> Unit, onClose: () -> Unit, onTitle: () -> Unit) {
    val c = Ts.colors
    val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Column(Modifier.background(c.surface2)) {
        HDivider(thickness = 1.5.dp)
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(c.accent))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.accent).clickable(role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.playing) TsIcons.Pause else TsIcons.Play, if (state.playing) tr("நிறுத்து", "Pause") else tr("இயக்கு", "Play"),
                    Modifier.size(20.dp), tint = c.onAccent)
            }
            Column(Modifier.weight(1f).clickable(onClick = onTitle)) {
                Text(title, style = Ts.type.barTitle.copy(fontSize = 16.sp), color = c.ink, maxLines = 1)
                val verse = state.verse
                val left = mmss(state.durationMs - state.positionMs)
                Text(
                    if (verse != null && state.verseCount > 0) "$verse / ${state.verseCount} · $left ${tr("மீதம்", "left")}" else "$left ${tr("மீதம்", "left")}",
                    style = Ts.type.caption, color = c.muted, maxLines = 1,
                )
            }
            Box(
                Modifier.height(40.dp).widthIn(min = 44.dp).clip(Pill).border(1.5.dp, c.line2, Pill).clickable(onClick = onSpeed).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatSpeed(state.speed), style = Ts.type.label, color = c.ink2)
            }
            IconBox(TsIcons.Close, tr("மூடு", "Close"), onClose, tint = c.muted, iconSize = 20.dp)
        }
    }
}

private fun formatSpeed(s: Float): String = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "×"

/**
 * The floating verse action card (1B), compact: a row or two of icon buttons, each with
 * a spoken label (and a tooltip on long press). Highlight is one dot in the verse's colour
 * (green when it has none) that opens the four colours; the root-words button shows Aleph
 * or Alpha by testament. Map and people are left out when the verse names no place or no one.
 */
@Composable
fun VerseActionCard(
    reference: String,
    hasAudio: Boolean,
    onClose: () -> Unit,
    onPlayHere: () -> Unit,
    onCommentary: () -> Unit,
    onCrossRefs: () -> Unit,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    /** The share-as-image sheet: the verse as a picture, sent with its text and link. */
    onShare: () -> Unit,
    onNote: () -> Unit,
    /** Without [onColor] (signed out), the highlight dot calls this instead. */
    onHighlight: () -> Unit,
    /** One tap highlights in a colour (null removes); the selection's current colour is ringed. */
    currentColor: HighlightColor? = null,
    onColor: ((HighlightColor?) -> Unit)? = null,
    onOriginal: () -> Unit,
    /** Who the verse names; null when it names no one. */
    onPeople: (() -> Unit)?,
    /** The atlas fitted to the verse's places; null when it names none. */
    onMap: (() -> Unit)? = null,
    bookmarked: Boolean = false,
    /** Old Testament: the root-words button shows א, else α. */
    oldTestament: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(22.dp)
    var colours by remember { mutableStateOf(false) }
    Column(
        modifier
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, c.line2, shape)
            // A phone on its side has less height than the card: it scrolls rather than clips.
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(reference, style = Ts.type.label, color = c.accent, modifier = Modifier.weight(1f), maxLines = 1)
            IconBox(TsIcons.Close, tr("மூடு", "Close"), onClose, tint = c.muted, iconSize = 20.dp)
        }
        if (colours && onColor != null) HighlightSwatches(currentColor) { colours = false; onColor(it) }
        val tools: List<@Composable (Modifier) -> Unit> = listOfNotNull(
            if (hasAudio) {
                { m -> ToolButton(tr("இந்த வசனத்திலிருந்து கேள்", "Play from this verse"), onPlayHere, m, primary = true) { ToolIcon(TsIcons.Play, it, 20.dp) } }
            } else null,
            { m ->
                ToolButton(tr("முனைப்பு", "Highlight"), { if (onColor != null) colours = !colours else onHighlight() }, m, selected = colours) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(swatch(currentColor ?: HighlightColor.Green))
                            .border(if (currentColor != null) 2.dp else 0.dp, if (currentColor != null) c.ink else Color.Transparent, CircleShape),
                    )
                }
            },
            { m -> ToolButton(tr("குறிப்பு எழுது", "Write a note"), onNote, m) { ToolIcon(TsIcons.Edit, it) } },
            { m ->
                ToolButton(if (bookmarked) tr("குறியை நீக்கு", "Remove bookmark") else tr("குறி", "Bookmark"), onBookmark, m) {
                    ToolIcon(if (bookmarked) TsIcons.StarFilled else TsIcons.Star, if (bookmarked) c.accent else it)
                }
            },
            { m -> ToolButton(tr("நகலெடு", "Copy"), onCopy, m) { ToolIcon(TsIcons.Copy, it) } },
            { m -> ToolButton(tr("பகிர்", "Share"), onShare, m) { ToolIcon(TsIcons.Share, it) } },
            { m ->
                ToolButton(tr("மூல மொழிச் சொற்கள்", "Hebrew and Greek words"), onOriginal, m) {
                    Text(if (oldTestament) "א" else "α", fontSize = 22.sp, lineHeight = 22.sp, color = it, fontFamily = FontFamily.Serif)
                }
            },
            onMap?.let { open -> { m: Modifier -> ToolButton(tr("வரைபடத்தில் காட்டு", "Show on the map"), open, m) { ToolIcon(TsIcons.MapView, it) } } },
            onPeople?.let { open -> { m: Modifier -> ToolButton(tr("இவ்வசனத்தின் நபர்கள்", "People in this verse"), open, m) { ToolIcon(TsIcons.People, it) } } },
            { m -> ToolButton(tr("விளக்கவுரை", "Commentary"), onCommentary, m) { ToolIcon(TsIcons.Commentary, it) } },
            { m -> ToolButton(tr("தொடர்புள்ள வசனங்கள்", "Cross-references"), onCrossRefs, m) { ToolIcon(TsIcons.Link, it) } },
        )
        // As many across as fit at about 48 dp, in even rows (6 and 6 on a phone).
        BoxWithConstraints {
            val fit = ((maxWidth + 6.dp) / 54.dp).toInt().coerceIn(4, tools.size)
            val rows = (tools.size + fit - 1) / fit
            val perRow = (tools.size + rows - 1) / rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tools.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { it(Modifier.weight(1f)) }
                        repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One icon button of the verse card: 48 dp high, its [label] spoken and shown on long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    content: @Composable (Color) -> Unit,
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(14.dp)
    // The row's weight sizes this box; the tooltip's anchor fills it.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(shape)
                    .background(if (primary) c.accent else if (selected) c.accentSoft else c.surface2)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { content(if (primary) c.onAccent else c.ink) }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, tint: Color, size: Dp = 22.dp) = Icon(icon, null, Modifier.size(size), tint = tint)

/** 14″ top bar (2D): mark, reference, search, listen, study settings, avatar. */
@Composable
fun WideTopBar(
    title: String,
    version: String,
    hasAudio: Boolean,
    onPicker: () -> Unit,
    onSearch: () -> Unit,
    onListen: () -> Unit,
    onSettings: () -> Unit,
    comparing: Boolean,
    onCompare: () -> Unit,
    onHome: () -> Unit,
) {
    val c = Ts.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onHome).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CrossMark(height = 20.dp)
                BasicText(
                    tr("தமிழ் வேதாகமம்", "Tamil Scripture"), style = Ts.type.cardTitle.copy(fontSize = 20.sp, color = c.ink), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp),
                )
            }
            ReferencePill(title, version, onPicker, height = 40.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().height(40.dp).clip(Pill).background(c.surface).border(1.5.dp, c.line2, Pill)
                        .clickable(onClick = onSearch).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(TsIcons.Search, null, Modifier.size(16.dp), tint = c.muted)
                    Text(tr("வசனம், சொல், இடம்", "Verse, word, place"), style = Ts.type.body.copy(fontSize = 14.sp), color = c.muted, modifier = Modifier.weight(1f))
                    Box(Modifier.border(1.dp, c.line2, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("/", style = Ts.type.captionSmall, color = c.muted)
                    }
                }
            }
            if (hasAudio) TsPillButton("▶ " + tr("கேள்", "Listen"), onListen, style = PillStyle.Tinted, height = 40.dp)
            TsPillButton(
                (if (comparing) "✓ " else "") + tr("ஒப்பிடு", "Compare"), onCompare,
                style = if (comparing) PillStyle.Tinted else PillStyle.Outlined, height = 40.dp,
            )
            TsPillButton(tr("ஆய்வு அமைப்பு", "Study settings"), onSettings, height = 40.dp)
            Avatar(tr("அ", "A"), onClick = onSettings)
        }
        HDivider(thickness = 1.5.dp)
    }
}

data class Hint(val keys: List<String>, val label: String, val action: () -> Unit)

/** The persistent key-hint strip on 14″ windows; every hint is also a tap target. */
@Composable
fun KeyHintStrip(hints: List<Hint>, trailing: Hint?) {
    val c = Ts.colors
    Column(Modifier.background(c.surface2)) {
        HDivider(thickness = 1.5.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            hints.forEach { KeyHint(it.keys, it.label, it.action) }
            Box(Modifier.weight(1f))
            trailing?.let { KeyHint(it.keys, it.label, it.action) }
        }
    }
}

/**
 * The four highlight colours as circles, and a fifth that removes the highlight: one tap
 * marks the selected verses, as on the website's action bar.
 */
@Composable
private fun HighlightSwatches(current: HighlightColor?, onColor: (HighlightColor?) -> Unit) {
    val c = Ts.colors
    val tamil = LocalUiLang.current == com.tamilscripture.core.model.UiLang.Tamil
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        HighlightColor.entries.forEach { color ->
            val on = color == current
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(swatch(color))
                    .border(if (on) 3.dp else 0.dp, if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = tr("முனைப்பு", "Highlight")) { onColor(color) }
                    .semantics { contentDescription = colorLabel(color, tamil) + if (on) (if (tamil) " (இப்போது)" else " (current)") else "" },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(TsIcons.Check, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color(0xFF1B1D22)) }
        }
        // Remove: an empty circle with a cross.
        Box(
            Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.line2, CircleShape)
                .clickable(role = Role.Button, enabled = current != null) { onColor(null) }
                .semantics { contentDescription = if (tamil) "முனைப்பை நீக்கு" else "Remove highlight" },
            contentAlignment = Alignment.Center,
        ) { Icon(TsIcons.Close, null, Modifier.size(16.dp), tint = if (current != null) c.ink2 else c.faint) }
    }
}

/**
 * The colour of a swatch: full and bright, so it reads as a button. The highlight behind
 * text is a dimmer tint of the same colour (dark mode keeps text readable over it).
 */
private fun swatch(color: HighlightColor): androidx.compose.ui.graphics.Color = when (color) {
    HighlightColor.Yellow -> androidx.compose.ui.graphics.Color(0xFFF2C94C)
    HighlightColor.Green -> androidx.compose.ui.graphics.Color(0xFF5CC27A)
    HighlightColor.Blue -> androidx.compose.ui.graphics.Color(0xFF5B9BEF)
    HighlightColor.Pink -> androidx.compose.ui.graphics.Color(0xFFF07AA0)
}

private fun colorLabel(color: HighlightColor, tamil: Boolean) = when (color) {
    HighlightColor.Yellow -> if (tamil) "மஞ்சள்" else "Yellow"
    HighlightColor.Green -> if (tamil) "பச்சை" else "Green"
    HighlightColor.Blue -> if (tamil) "நீலம்" else "Blue"
    HighlightColor.Pink -> if (tamil) "இளஞ்சிவப்பு" else "Pink"
}
