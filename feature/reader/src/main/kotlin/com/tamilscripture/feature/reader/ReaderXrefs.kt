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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

/** A cross-reference's identity within its verse (the reference and its end). */
internal fun CrossRef.key(): String = to + (end?.let { "-$it" } ?: "")

/**
 * Study Bible: the verse's cross-references in small type right after its text, on the same
 * line (the website's fmt-xref list). Each is a link; [openKey] is the one whose box is open.
 */
internal fun AnnotatedString.Builder.appendCrossRefs(
    refs: List<CrossRef>,
    all: Boolean,
    openKey: String?,
    manifest: ContentManifest?,
    c: com.tamilscripture.core.designsystem.theme.TsColors,
    lang: UiLang,
    onRef: (String) -> Unit,
    onMore: () -> Unit,
) {
    val small = SpanStyle(fontSize = 12.sp, color = c.accent, fontWeight = FontWeight.Normal, baselineShift = BaselineShift.None)
    withStyle(small.copy(color = c.muted)) { append("↗") }
    for (r in if (all) refs else refs.take(SHOWN)) {
        append("\u2002")
        val k = r.key()
        val style = if (k == openKey) small.copy(fontWeight = FontWeight.Bold, background = c.accentSoft) else small
        // No-break spaces: a reference never splits across lines.
        withLink(LinkAnnotation.Clickable(k, TextLinkStyles(style)) { onRef(k) }) { withStyle(style) { append(refLabel(r, manifest, lang).replace(' ', '\u00A0')) } }
    }
    if (!all && refs.size > SHOWN) {
        append("\u2002")
        withLink(LinkAnnotation.Clickable("more", TextLinkStyles(small.copy(color = c.muted))) { onMore() }) { append("+${refs.size - SHOWN}") }
    }
}

/** The box of the cross-reference opened under its verse, if any. */
@Composable
fun CrossRefBox(refs: List<CrossRef>, openKey: String?, manifest: ContentManifest?, version: String, onOpen: (VerseId) -> Unit) {
    val current = refs.firstOrNull { it.key() == openKey }
    AnimatedVisibility(current != null) {
        current?.let { r -> Column(Modifier.padding(start = 40.dp, end = 10.dp, top = 2.dp, bottom = 8.dp)) { RefBox(r, manifest, version, onOpen) } }
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
    val barColor = c.accent
    Column(
        // Indented, smaller and marked by a bar on the left, so it reads as a note, not the text.
        Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.line, shape)
            .drawBehind { drawRect(barColor, size = Size(3.dp.toPx(), size.height)) }
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
            style = Ts.type.scripture(15.sp, 1.65f), color = c.ink2, maxLines = 6, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [main] with [trailer] carried on right after its last word, as one run of text, while the
 * trailer keeps its own, tighter line spacing: Compose gives a paragraph one line height,
 * so the trailer is a second text laid out from where [main] ends, on the same baseline.
 * When too little room is left on that line, the trailer starts on the next one.
 */
@Composable
fun TextWithTrailer(
    main: AnnotatedString,
    mainStyle: TextStyle,
    color: Color,
    trailer: AnnotatedString?,
    trailerStyle: TextStyle,
    modifier: Modifier = Modifier,
    /** The main text's layout, for the stylus (positions are relative to [modifier]'s content). */
    onMainLayout: (TextLayoutResult) -> Unit = {},
) {
    if (trailer == null) {
        Text(main, style = mainStyle, color = color, modifier = modifier, onTextLayout = onMainLayout)
        return
    }
    // Laid out afresh when the references change (the open one): inside the reader's lazy
    // list a SubcomposeLayout kept its earlier measure, so the open reference never showed.
    androidx.compose.runtime.key(trailer.text, trailer.spanStyles) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        var mainLayout: TextLayoutResult? = null
        // Slots keyed by what they show, so a change of style (the open reference) is laid out anew.
        val mainPlaceable = subcompose(main) {
            Text(main, style = mainStyle, color = color, onTextLayout = { mainLayout = it; onMainLayout(it) })
        }.single().measure(loose)
        val m = mainLayout!!
        val last = m.lineCount - 1
        val gap = 6.dp.toPx()
        val lineEnd = m.getLineRight(last) + gap
        // Start the trailer on the verse's last line when a reference or two fits there.
        val sameLine = loose.maxWidth - lineEnd > 72.dp.toPx()
        val indent = if (sameLine) lineEnd else 0f
        var tLayout: TextLayoutResult? = null
        val trailerPlaceable = subcompose(Triple("trailer", trailer.text, trailer.spanStyles)) {
            Text(
                trailer, color = color, onTextLayout = { tLayout = it },
                style = trailerStyle.copy(textIndent = TextIndent(firstLine = indent.toSp())),
            )
        }.single().measure(loose)
        val t = tLayout!!
        val y = if (sameLine) (m.getLineBaseline(last) - t.getLineBaseline(0)).toInt()
        else mainPlaceable.height + 2.dp.roundToPx()
        val width = maxOf(mainPlaceable.width, trailerPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = maxOf(mainPlaceable.height, y + trailerPlaceable.height).coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            mainPlaceable.place(0, 0)
            trailerPlaceable.place(0, y)
        }
    }
    }
}
