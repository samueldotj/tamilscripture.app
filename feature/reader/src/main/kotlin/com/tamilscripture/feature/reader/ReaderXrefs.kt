package com.tamilscripture.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** Boxes opened at first; the rest wait for "Show all" (each box may fetch a chapter). */
private const val BOXES = 8

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
 * fmt-xref list). A tap opens them as boxes with each verse's text; a box opens that verse.
 */
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
    var open by rememberSaveable(verseKey) { mutableStateOf(false) }
    val line = refs.take(SHOWN).joinToString(" · ") { refLabel(it, manifest, lang) } +
        if (refs.size > SHOWN) " · +${refs.size - SHOWN}" else ""
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 4.dp)) {
        Text(
            (if (open) "▾ " else "↗ ") + line,
            style = Ts.type.caption.copy(fontSize = 12.sp),
            color = c.accent,
            maxLines = if (open) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = tr("தொடர்புள்ள வசனங்கள்", "Cross-references")) { open = !open }
                .padding(vertical = 4.dp)
                .semantics { contentDescription = (if (lang == UiLang.Tamil) "தொடர்புள்ள வசனங்கள்: " else "Cross-references: ") + line },
        )
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var all by rememberSaveable(verseKey) { mutableStateOf(false) }
                (if (all) refs else refs.take(BOXES)).forEach { r -> RefBox(r, manifest, version, onOpen) }
                if (!all && refs.size > BOXES) {
                    Text(
                        tr("அனைத்தும் (${refs.size})", "Show all ${refs.size}"),
                        style = Ts.type.label, color = c.accent,
                        modifier = Modifier.clickable(role = Role.Button) { all = true }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
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
