package com.tamilscripture.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull

/** How many references the line under a verse names before "+n". */
private const val SHOWN = 6


/** "யோவா 3:16", "Jn 3:16–18": the book's short form, as the website's study margin writes it. */
private fun refLabel(r: CrossRef, manifest: ContentManifest?, lang: UiLang): String {
    val vid = VerseId.parse(r.to) ?: return r.to
    val end = r.end?.let(VerseId::parse)
    val book = manifest?.book(vid.book)
    val name = book?.let { (if (lang == UiLang.Tamil) it.abbrTa else it.abbrEn).firstOrNull() ?: it.name(lang) } ?: vid.book
    val tail = when {
        end == null || end == vid -> ""
        end.chapter == vid.chapter -> "–${end.verse}"
        else -> "–${end.chapter}:${end.verse}"
    }
    return "$name ${vid.chapter}:${vid.verse}$tail"
}

/**
 * Study Bible: the verse's cross-references in small type under it (the website's
 * fmt-xref list). Each reference is its own button: a tap opens a box under the line with
 * that verse's text, a second tap (or another reference) closes it, and the box opens the
 * verse. "+n" shows the rest of the references.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerseCrossRefs(
    verseKey: String,
    refs: List<CrossRef>,
    manifest: ContentManifest?,
    version: String,
    onOpen: (VerseId) -> Unit,
) {
    if (refs.isEmpty()) return
    val c = Ts.colors
    val lang = LocalUiLang.current
    var openRef by rememberSaveable(verseKey) { mutableStateOf<String?>(null) }
    var all by rememberSaveable(verseKey) { mutableStateOf(false) }
    val key = { r: CrossRef -> r.to + (r.end?.let { "-$it" } ?: "") }
    val shown = if (all) refs else refs.take(SHOWN)
    val style = Ts.type.caption.copy(fontSize = 12.sp)
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 4.dp)) {
        FlowRow(verticalArrangement = Arrangement.Center) {
            Text("↗", style = style, color = c.muted, modifier = Modifier.padding(end = 2.dp, top = 6.dp, bottom = 6.dp))
            shown.forEach { r ->
                val selected = openRef == key(r)
                Text(
                    refLabel(r, manifest, lang),
                    style = style.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    color = c.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) c.accentSoft else Color.Transparent)
                        .clickable(role = Role.Button) { openRef = if (selected) null else key(r) }
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                )
            }
            if (!all && refs.size > SHOWN) {
                Text(
                    "+${refs.size - SHOWN}", style = style, color = c.muted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClickLabel = tr("அனைத்தும்", "Show all")) { all = true }
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                )
            }
        }
        val current = refs.firstOrNull { key(it) == openRef }
        AnimatedVisibility(current != null) {
            current?.let { r -> Column(Modifier.padding(top = 2.dp, bottom = 6.dp)) { RefBox(r, manifest, version, onOpen) } }
        }
    }
}

/** One referenced verse: its reference and text in a box; a tap goes there. */
@Composable
private fun RefBox(r: CrossRef, manifest: ContentManifest?, version: String, onOpen: (VerseId) -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val services = LocalAppServices.current
    val vid = VerseId.parse(r.to) ?: return
    val end = r.end?.let(VerseId::parse)?.takeIf { it.book == vid.book && it.chapter == vid.chapter }?.verse
    val text by produceState<String?>(null, r.to, r.end, version) {
        val chapter = runCatching { services.graph.content.chapter(version, vid.book, vid.chapter).firstOrNull()?.value }.getOrNull()
        value = chapter?.let { ch -> (vid.verse..(end ?: vid.verse)).joinToString(" ") { ch.verseText(it) }.trim() } ?: ""
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(c.surface2, shape).border(1.dp, c.line, shape)
            .clickable(role = Role.Button) { onOpen(vid) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(refLabel(r, manifest, lang), style = Ts.type.labelSmall, color = c.accent)
        Text(
            when {
                text == null -> "…"
                text!!.isEmpty() -> tr("இணைப்பின்றி இந்த வசனத்தைத் திறக்க முடியவில்லை.", "This verse needs a connection.")
                else -> text!!
            },
            style = Ts.type.scripture(16.sp, 1.7f), color = c.ink, maxLines = 6, overflow = TextOverflow.Ellipsis,
        )
    }
}
