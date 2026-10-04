package com.tamilscripture.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.ChapterMentions
import com.tamilscripture.core.model.OriginalChapter
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr

/**
 * The verse in Hebrew or Greek, word by word, each opening its Strong's entry (M8-2).
 * Hebrew reads right to left, so its words are listed in reading order top to bottom.
 */
@Composable
fun OriginalWordsSheet(reference: String, book: String, chapter: Int, verse: Int, onStrongs: (String) -> Unit, onDismiss: () -> Unit) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val state by produceState<Pair<Boolean, OriginalChapter?>>(true to null, book, chapter) { value = false to graph.study.original(book, chapter) }
    TsSheet(onDismiss) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp)) {
            Text(tr("மூல மொழி", "Original words") + " · " + reference, style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            val words = state.second?.words(verse).orEmpty()
            when {
                state.first -> CircularProgressIndicator(color = c.accent)
                words.isEmpty() -> Text(
                    tr("இதற்கு இணைய இணைப்பு அல்லது ‘மூல மொழிச் சொற்கள்’ பொதி தேவை.", "This needs a connection, or the Original words pack in Downloads."),
                    style = Ts.type.body, color = c.muted,
                )
                else -> words.forEachIndexed { i, w ->
                    if (i > 0) HDivider()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = w.strongs.isNotBlank()) { onStrongs(w.strongs) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.text, style = Ts.type.cardTitle.copy(fontSize = 22.sp), color = c.ink)
                            Text(w.translit, style = Ts.type.caption, color = c.muted)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(w.gloss, style = Ts.type.body, color = c.ink2)
                            Text(w.strongs, style = Ts.type.captionSmall, color = c.accent)
                        }
                    }
                }
            }
            if (state.second?.lang == "he" && lang == UiLang.Tamil) {
                Text("எபிரெயம் வலமிருந்து இடமாக வாசிக்கப்படும்.", style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * The people and places a chapter names (M8-4), the selected verse's first. Each opens
 * its page in the study screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeoplePlacesSheet(
    title: String,
    book: String,
    chapter: Int,
    verse: Int?,
    onPerson: (String) -> Unit,
    onPlace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val tamil = LocalUiLang.current == UiLang.Tamil
    val state by produceState<Pair<Boolean, ChapterMentions?>>(true to null, book, chapter) { value = false to graph.study.mentions(book, chapter) }
    TsSheet(onDismiss) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp)) {
            Text(tr("நபர்கள் · இடங்கள்", "People and places") + " · " + title, style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            val m = state.second
            if (state.first) {
                CircularProgressIndicator(color = c.accent)
                return@Column
            }
            if (m == null || (m.people.isEmpty() && m.places.isEmpty())) {
                Text(
                    if (m == null) tr("இதற்கு இணைய இணைப்பு அல்லது ‘நபர்களும் இடங்களும்’ பொதி தேவை.", "This needs a connection, or the People and places pack in Downloads.")
                    else tr("இந்த அதிகாரத்தில் பெயர்கள் இல்லை.", "This chapter names no one."),
                    style = Ts.type.body, color = c.muted,
                )
                return@Column
            }
            val here = verse?.let { v -> m.verses.firstOrNull { it.verse == "$book.$chapter.$v" } }
            val pad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            @Composable
            fun group(label: String, people: List<String>, places: List<String>) {
                if (people.isEmpty() && places.isEmpty()) return
                Kicker(label, Modifier.padding(top = 8.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { id ->
                        val p = m.people[id] ?: return@forEach
                        TsChip(if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn, false, { onPerson(id) }, contentPadding = pad)
                    }
                    places.forEach { id ->
                        val p = m.places[id] ?: return@forEach
                        TsChip("⌖ " + (if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn), false, { onPlace(id) }, contentPadding = pad)
                    }
                }
            }
            if (here != null && verse != null) group(tr("வசனம் $verse", "Verse $verse"), here.people, here.places)
            group(tr("இந்த அதிகாரம்", "This chapter"), m.people.entries.sortedByDescending { it.value.mentions }.map { it.key },
                m.places.entries.sortedByDescending { it.value.mentions }.map { it.key })
        }
    }
}
