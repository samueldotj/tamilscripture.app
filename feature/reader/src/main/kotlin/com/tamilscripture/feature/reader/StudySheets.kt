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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.content.References
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.SchematicMap
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.ChapterMentions
import com.tamilscripture.core.model.MapDrawing
import com.tamilscripture.core.model.MapSvg
import com.tamilscripture.core.model.OriginalChapter
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr

/**
 * The verse in Hebrew or Greek, word by word, each opening its Strong's entry (M8-2).
 * Hebrew reads right to left, so its words are listed in reading order top to bottom.
 */
@Composable
fun OriginalWordsSheet(reference: String, book: String, chapter: Int, verse: Int, onStrongs: (String) -> Unit, onDismiss: () -> Unit) {
    TsSheet(onDismiss) {
        OriginalWordsContent(reference, book, chapter, verse, onStrongs, Modifier.heightIn(max = 560.dp).padding(start = 22.dp, end = 22.dp, bottom = 16.dp))
    }
}

/** The original-words list itself, in a sheet on phones or the study pane on wide windows. */
@Composable
fun OriginalWordsContent(reference: String, book: String, chapter: Int, verse: Int, onStrongs: (String) -> Unit, modifier: Modifier) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val state by produceState<Pair<Boolean, OriginalChapter?>>(true to null, book, chapter) { value = false to graph.study.original(book, chapter) }
    Column(modifier.verticalScroll(rememberScrollState())) {
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
    onAtlas: ((List<String>) -> Unit)? = null,
    only: StudyAid? = null,
    verses: List<Int> = listOfNotNull(verse),
    onDismiss: () -> Unit,
) {
    TsSheet(onDismiss) {
        PeoplePlacesContent(title, book, chapter, verse, onPerson, onPlace, Modifier.heightIn(max = 560.dp).padding(start = 22.dp, end = 22.dp, bottom = 16.dp), onAtlas, only, verses)
    }
}

/** One study aid on its own, as the verse card's map and people buttons open it (the website's `only`). */
enum class StudyAid { Map, People }

/** The people-and-places list itself, in a sheet on phones or the study pane on wide windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeoplePlacesContent(
    title: String,
    book: String,
    chapter: Int,
    verse: Int?,
    onPerson: (String) -> Unit,
    onPlace: (String) -> Unit,
    modifier: Modifier,
    /** M8-5e: opens the atlas fitted to the chapter's places. */
    onAtlas: ((List<String>) -> Unit)? = null,
    /** Just the map (the selected verses' places marked) or just the people (theirs first). */
    only: StudyAid? = null,
    /** The selected verses: their names are marked and listed first. */
    verses: List<Int> = listOfNotNull(verse),
) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val tamil = LocalUiLang.current == UiLang.Tamil
    val state by produceState<Pair<Boolean, ChapterMentions?>>(true to null, book, chapter) { value = false to graph.study.mentions(book, chapter) }
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    // The chapter's map, when the website drew one (mentions.map).
    val map by produceState<MapDrawing?>(null, state.second) {
        value = state.second?.takeIf { it.map }?.let { graph.study.file("maps/$book/$chapter.svg") }?.let(MapSvg::parse)
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        val heading = when (only) {
            StudyAid.Map -> tr("வரைபடம்", "Map")
            StudyAid.People -> tr("நபர்கள்", "People")
            null -> tr("நபர்கள் · இடங்கள்", "People and places")
        }
        Text("$heading · $title", style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
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
        val keys = verses.map { "$book.$chapter.$it" }.toSet()
        val named = m.verses.filter { it.verse in keys }
        val markedPlaces = named.flatMap { it.places }.filter { it in m.places }.toSet()
        val markedPeople = named.flatMap { it.people }.filter { it in m.people }.distinct()
        if (only != StudyAid.People) {
            map?.let { d ->
                SchematicMap(d, tamil, Modifier.padding(bottom = 6.dp), marked = markedPlaces, description = title, onPlace = onPlace)
            }
            if (onAtlas != null && m.places.isNotEmpty()) {
                // From the map aid, the atlas fits the verse's places; otherwise the chapter's.
                val fit = if (only == StudyAid.Map && markedPlaces.isNotEmpty()) markedPlaces.toList() else m.places.keys.toList()
                TsPillButton(
                    tr("வரைபடத்தில் திற", "Open in the atlas"), { onAtlas(fit) },
                    height = 36.dp, style = PillStyle.Tinted, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (only == StudyAid.Map) return@Column
        val here = verse?.let { v -> m.verses.firstOrNull { it.verse == "$book.$chapter.$v" } }
        val pad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        @Composable
        fun group(label: String, people: List<String>, places: List<String>) {
            if (people.isEmpty() && places.isEmpty()) return
            Kicker(label, Modifier.padding(top = 8.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { id ->
                    val p = m.people[id] ?: return@forEach
                    val name = if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn
                    // Two people of one name (John the Baptist, Peter's father John) are told apart
                    // by the start of their description.
                    val twin = m.people.values.count { (if (tamil) it.nameTa.ifBlank { it.nameEn } else it.nameEn) == name } > 1
                    // English: the start of their description. Tamil: where they are first named
                    // (the website's qualifier, "Mat 3:1"), as a Tamil reference; descriptions are English.
                    val hint = if (tamil) {
                        p.qualifier?.let { q -> References.parse(q, manifest) }?.let { r -> r.book.label(UiLang.Tamil, r.chapter, r.verse) }.orEmpty()
                    } else {
                        p.brief.substringBefore(',').removePrefix("The ").take(28).trim()
                    }
                    TsChip(if (twin && hint.isNotEmpty()) "$name · $hint" else name, false, { onPerson(id) }, contentPadding = pad)
                }
                places.forEach { id ->
                    val p = m.places[id] ?: return@forEach
                    TsChip("⌖ " + (if (tamil) p.nameTa.ifBlank { p.nameEn } else p.nameEn), false, { onPlace(id) }, contentPadding = pad)
                }
            }
        }
        if (only == StudyAid.People) {
            // The verse's people first, then the rest of the chapter's.
            val label = if (verses.size == 1) tr("வசனம் ${verses[0]}", "Verse ${verses[0]}") else tr("தெரிந்த வசனங்கள்", "Selected verses")
            group(label, markedPeople, emptyList())
            group(tr("இந்த அதிகாரத்தில் மற்றவர்கள்", "Others in this chapter"),
                m.people.entries.sortedByDescending { it.value.mentions }.map { it.key }.filterNot { it in markedPeople }, emptyList())
            return@Column
        }
        if (here != null && verse != null) group(tr("வசனம் $verse", "Verse $verse"), here.people, here.places)
        group(tr("இந்த அதிகாரம்", "This chapter"), m.people.entries.sortedByDescending { it.value.mentions }.map { it.key },
            m.places.entries.sortedByDescending { it.value.mentions }.map { it.key })
    }
}
