package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
) {
    val c = Ts.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            ReferencePill(title, version, onPicker, Modifier.weight(1f))
            if (hasAudio) IconBox(TsIcons.Play, tr("கேள்", "Listen"), onPlay, tint = c.accent, iconSize = 18.dp)
            IconBox(
                TsIcons.StudySettings, tr("ஆய்வு அமைப்பு", "Study settings"), onSettings,
                tint = if (commentaryOn) c.accent else c.ink2, badge = commentaryOn,
            )
        }
        HDivider(thickness = 1.5.dp)
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
fun ChapterNavBar(prevLabel: String?, nextLabel: String?, position: String, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = Ts.colors
    Column(Modifier.background(c.surface2)) {
        HDivider(thickness = 1.5.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (prevLabel != null) TsPillButton("‹ $prevLabel", onPrev) else Box(Modifier.size(1.dp))
            Text(position, style = Ts.type.caption, color = c.muted)
            if (nextLabel != null) TsPillButton("$nextLabel ›", onNext) else Box(Modifier.size(1.dp))
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
    onOriginal: () -> Unit,
    onPeople: () -> Unit,
    onShareImage: () -> Unit,
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
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(reference, style = Ts.type.label, color = c.accent, modifier = Modifier.weight(1f))
            Icon(TsIcons.Close, tr("மூடு", "Close"), Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose), tint = c.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasAudio) BigAction(TsIcons.Play, tr("இங்கே", "From here"), primary = true, onClick = onPlayHere, modifier = Modifier.weight(1f))
            BigAction(TsIcons.Commentary, tr("விளக்கவுரை", "Commentary"), onClick = onCommentary, modifier = Modifier.weight(1f))
            BigAction(TsIcons.Link, tr("தொடர்பு", "Cross-refs"), onClick = onCrossRefs, modifier = Modifier.weight(1f))
            BigAction(TsIcons.Bookmark, tr("குறி", "Bookmark"), onClick = onBookmark, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(tr("நகல்", "Copy"), onCopy, Modifier.weight(1f))
            SmallAction(tr("பகிர்", "Share"), onShare, Modifier.weight(1f))
            SmallAction(tr("குறிப்பு", "Note"), onNote, Modifier.weight(1f))
            SmallAction(tr("முனைப்பு", "Highlight"), onHighlight, Modifier.weight(1f))
        }
        // M8-2, M8-4: the verse in Hebrew or Greek; who and where it names.
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(tr("மூல மொழி", "Original words"), onOriginal, Modifier.weight(1f))
            SmallAction(tr("நபர்கள் · இடங்கள்", "People · places"), onPeople, Modifier.weight(1f))
            SmallAction(tr("படமாக", "As image"), onShareImage, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val c = Ts.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(64.dp).clip(shape).background(if (primary) c.accent else c.surface2).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val fg = if (primary) c.onAccent else c.ink
        Icon(icon, null, Modifier.size(if (primary) 16.dp else 20.dp), tint = fg)
        BasicText(
            label, style = Ts.type.labelSmall.copy(color = fg), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
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
            label, style = Ts.type.label.copy(color = Ts.colors.ink2), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp),
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
