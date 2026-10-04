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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.data.settings.Appearance
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.SheetHandle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsListRow
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.component.TsToggle
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.BibleVersion
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Ts.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) { content() }
    }
}

/** ஆய்வு அமைப்பு · Study settings (design 1D). */
@Composable
fun StudySettingsSheet(
    settings: Settings,
    sources: List<CommentarySource>,
    onChange: ((Settings) -> Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ts.colors
    TsSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text(tr("ஆய்வு அமைப்பு", "Study settings"), style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.weight(1f))
                Text("Study Bible", style = Ts.type.caption, color = c.muted)
            }
            HDivider()
            TsListRow(
                tr("தலைப்புகள்", "Section headings"), subtitle = tr("பகுதித் தலைப்புகள்", "Headings between passages"),
                trailing = { TsToggle(settings.headings, { v -> onChange { it.copy(headings = v) } }) },
            )
            HDivider()
            TsListRow(
                tr("தொடர்புள்ள வசனங்கள்", "Cross-references"), subtitle = tr("வசன இறுதியில் குறிப்புகள்", "Markers at the end of verses"),
                trailing = { TsToggle(settings.crossRefs, { v -> onChange { it.copy(crossRefs = v) } }) },
            )
            HDivider()
            TsListRow(
                tr("அடிக்குறிப்புகள்", "Footnotes"), subtitle = tr("மொழிபெயர்ப்புக் குறிப்புகள்", "Translation notes"),
                trailing = { TsToggle(settings.footnotes, { v -> onChange { it.copy(footnotes = v) } }) },
            )
            HDivider()
            Column(Modifier.background(c.surface2).padding(bottom = 18.dp)) {
                TsListRow(
                    tr("விளக்கவுரை", "Commentary"), subtitle = tr("வசனக் குழுவின் கீழ் மடிந்து", "Folded under each group of verses"),
                    trailing = { TsToggle(settings.commentary, { v -> onChange { it.copy(commentary = v) } }) },
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.forEach { s ->
                        TsChip(
                            s.short, s.id == settings.commentarySource,
                            { onChange { it.copy(commentarySource = s.id, commentary = true) } },
                            square = true, textStyle = Ts.type.label,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            HDivider()
            TsListRow(tr("எழுத்து அளவு", "Text size"), trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundTextButton("அ−", 14) { onChange { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(15)) } }
                    Text(settings.fontSize.toString(), style = Ts.type.label, color = c.ink)
                    RoundTextButton("அ+", 17) { onChange { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(30)) } }
                }
            })
            HDivider()
            TsListRow(tr("தோற்றம்", "Appearance"), trailing = {
                TsSegmented(
                    listOf(tr("இருள்", "Dark"), tr("ஒளி", "Light"), tr("தானியங்கி", "Auto")),
                    when (settings.appearance) { Appearance.Dark -> 0; Appearance.Light -> 1; Appearance.System -> 2 },
                    { i -> onChange { it.copy(appearance = listOf(Appearance.Dark, Appearance.Light, Appearance.System)[i]) } },
                    height = 36.dp, textStyle = Ts.type.labelSmall,
                )
            })
        }
    }
}

@Composable
private fun RoundTextButton(text: String, size: Int, onClick: () -> Unit) {
    val c = Ts.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.5.dp, c.line2, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ts.type.body.copy(fontSize = size.sp), color = c.ink2)
    }
}

/** தொடர்புள்ள வசனங்கள் for the selected verse, best first, top 10 then the rest (A-5.1). */
@Composable
fun CrossRefsSheet(
    reference: String,
    refs: List<CrossRef>,
    manifest: ContentManifest?,
    version: String,
    onOpen: (VerseId) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val services = LocalAppServices.current
    TsSheet(onDismiss) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp)) {
            Kicker(tr("தொடர்புள்ள வசனங்கள்", "Cross-references"), color = c.accent)
            Text(reference, style = Ts.type.sheetTitle, color = c.ink)
        }
        HDivider()
        if (refs.isEmpty()) {
            Text(tr("இந்த வசனத்துக்குத் தொடர்புகள் இல்லை.", "No cross-references for this verse."), style = Ts.type.body, color = c.muted, modifier = Modifier.padding(22.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(refs.take(25), key = { it.to + (it.end ?: "") }) { r ->
                val vid = VerseId.parse(r.to) ?: return@items
                val endVerse = r.end?.let(VerseId::parse)?.takeIf { it.chapter == vid.chapter }?.verse
                val book = manifest?.book(vid.book)
                val text by produceState<String?>(null, r.to) {
                    value = services.graph.content.chapter(version, vid.book, vid.chapter).firstOrNull()?.value?.verseText(vid.verse)
                }
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(vid) }.padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(book?.label(lang, vid.chapter, vid.verse, endVerse) ?: r.to, style = Ts.type.labelSmall, color = c.accent)
                    Text(
                        text ?: "…", style = Ts.type.scripture(16.sp, 1.7f), color = c.ink, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                HDivider()
            }
        }
    }
}

/** Version chooser for the "IRV ▾" control. */
@Composable
fun VersionSheet(versions: List<BibleVersion>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    TsSheet(onDismiss) {
        Text(tr("மொழிபெயர்ப்பு", "Translation"), style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.padding(start = 22.dp, bottom = 12.dp))
        versions.sortedBy { it.order }.forEach { v ->
            HDivider()
            TsListRow(
                v.short + " · " + (if (lang == UiLang.Tamil) v.nameNative ?: v.name else v.name),
                subtitle = v.licence,
                onClick = { onPick(v.code) },
                titleColor = if (v.code == current) c.accent else c.ink,
            )
        }
    }
}
