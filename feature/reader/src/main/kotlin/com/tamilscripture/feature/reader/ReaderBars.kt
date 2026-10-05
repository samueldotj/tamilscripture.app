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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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

/** The floating verse action card (1B). */
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
    onShare: () -> Unit,
    onNote: () -> Unit,
    onHighlight: () -> Unit,
    /** One tap highlights in a colour (null removes); the selection's current colour is ringed. */
    currentColor: HighlightColor? = null,
    onColor: ((HighlightColor?) -> Unit)? = null,
    onOriginal: () -> Unit,
    onPeople: () -> Unit,
    onShareImage: () -> Unit,
    onWebsite: () -> Unit = {},
    onLarge: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, c.line2, shape)
            // A phone on its side has less height than the card: it scrolls rather than clips.
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(reference, style = Ts.type.label, color = c.accent, modifier = Modifier.weight(1f))
            Icon(TsIcons.Close, tr("மூடு", "Close"), Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose), tint = c.muted)
        }
        if (onColor != null) HighlightSwatches(currentColor, onColor)
        // Four across, or two by two when the card is narrow (the text column beside a wide
        // study pane), where four would cut long Tamil labels.
        BoxWithConstraints {
            val big: List<@Composable (Modifier) -> Unit> = listOfNotNull(
                if (hasAudio) { m -> BigAction(TsIcons.Play, tr("இங்கே", "From here"), primary = true, onClick = onPlayHere, modifier = m) } else null,
                { m -> BigAction(TsIcons.Commentary, tr("விளக்கவுரை", "Commentary"), onClick = onCommentary, modifier = m) },
                { m -> BigAction(TsIcons.Link, tr("தொடர்பு", "Cross-refs"), onClick = onCrossRefs, modifier = m) },
                { m -> BigAction(TsIcons.Bookmark, tr("குறி", "Bookmark"), onClick = onBookmark, modifier = m) },
            )
            val perRow = if (maxWidth < 400.dp) 2 else big.size
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                big.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                }
            }
        }
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(tr("நகல்", "Copy"), onCopy, Modifier.weight(1f))
            SmallAction(tr("பகிர்", "Share"), onShare, Modifier.weight(1f))
            SmallAction(tr("குறிப்பு", "Note"), onNote, Modifier.weight(1f))
            if (onColor == null) SmallAction(tr("முனைப்பு", "Highlight"), onHighlight, Modifier.weight(1f))
        }
        // M8-2, M8-4: the verse in Hebrew or Greek; who and where it names.
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(tr("மூல மொழி", "Original words"), onOriginal, Modifier.weight(1f))
            SmallAction(tr("நபர்கள் · இடங்கள்", "People · places"), onPeople, Modifier.weight(1f))
            SmallAction(tr("படமாக", "As image"), onShareImage, Modifier.weight(1f))
        }
        // M8-6: the verse large on its own (present mode, from this verse); M6-9c: on the website.
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(tr("பெரிதாக", "Large view"), onLarge, Modifier.weight(1f))
            SmallAction(tr("இணையதளம்", "Website"), onWebsite, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val c = Ts.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        // At large text sizes (M2-12) the button grows and its label wraps rather than clipping.
        modifier.heightIn(min = 64.dp).clip(shape).background(if (primary) c.accent else c.surface2).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val fg = if (primary) c.onAccent else c.ink
        Icon(icon, null, Modifier.size(if (primary) 16.dp else 20.dp), tint = fg)
        BasicText(
            label, style = Ts.type.labelSmall.copy(color = fg, textAlign = TextAlign.Center), maxLines = labelLines(label),
            autoSize = TextAutoSize.StepBased(minFontSize = (8f / LocalDensity.current.fontScale).sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label, style = Ts.type.label.copy(color = Ts.colors.ink2, textAlign = TextAlign.Center), maxLines = labelLines(label),
            autoSize = TextAutoSize.StepBased(minFontSize = (8f / LocalDensity.current.fontScale).sp, maxFontSize = 13.sp),
        )
    }
}

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
                Text(tr("தமிழ் வேதாகமம்", "Tamil Scripture"), style = Ts.type.cardTitle.copy(fontSize = 20.sp), color = c.ink)
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
 * A label wraps only between words; a single word shrinks to fit instead of breaking, down
 * to 8 sp on screen whatever the text-size setting (the minimum above is divided by it).
 */
private fun labelLines(label: String) = if (label.trim().contains(' ')) 2 else 1

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
