package com.tamilscripture.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class Appearance { System, Dark, Light }

/**
 * The website's three reading formats, under its keys: Reader (flowing text, no verse
 * numbers), Standard (paragraphs with verse numbers, like a printed Bible) and Study Bible
 * (one verse per line with study aids).
 */
enum class ReadingFormat(val key: String) {
    Reader("reader"), Standard("standard"), Study("xref");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}
enum class Typeface { MuktaMalar, NotoSansTamil, NotoSerifTamil }

/** Reader and app settings (A-2.6, A-2.7, design 1D). Key names follow the website's settings. */
data class Settings(
    val appearance: Appearance = Appearance.System,
    val uiLang: UiLang = UiLang.Tamil,
    val version: String = "IRVTAM",
    /** Scripture size in sp. The design's default is 21. */
    val fontSize: Int = 21,
    val typeface: Typeface = Typeface.MuktaMalar,
    val format: ReadingFormat = ReadingFormat.Standard,
    val headings: Boolean = true,
    val footnotes: Boolean = false,
    /** Community highlight heat behind verses (M8-7), as the website's setting. */
    val heat: Boolean = false,
    val crossRefs: Boolean = true,
    val dictionaryWords: Boolean = true,
    val commentary: Boolean = false,
    val commentarySource: String = "henry",
    val lastRead: Passage? = null,
    val shareStats: Boolean = true,
    /**
     * The version last compared with (A-3.3), offered by the reader's compare button. Whether a
     * reader shows two columns is the reader's own state, never a setting: the Bible opens
     * in one column.
     */
    val compare: String? = null,
    /** Newest first, at most 8, kept on this device only (A-4.5, like the website's). */
    val recentSearches: List<String> = emptyList(),
    /** Large downloads wait for Wi-Fi (DL-5). */
    val downloadWifiOnly: Boolean = true,
    /** Chapters read online per version, for the download offer (M1-9f). */
    val onlineChapters: Map<String, Int> = emptyMap(),
    /** Versions whose download offer was turned down. */
    val offersDeclined: Set<String> = emptySet(),
    /** Daily reading reminder, minutes past midnight; null when off (M7-5). */
    val reminderMinutes: Int? = null,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val appearance = stringPreferencesKey("appearance")
        val uiLang = stringPreferencesKey("uiLang")
        val version = stringPreferencesKey("version")
        val fontSize = intPreferencesKey("fontSize")
        val typeface = stringPreferencesKey("typeface")
        val format = stringPreferencesKey("format")
        val headings = booleanPreferencesKey("headings")
        val footnotes = booleanPreferencesKey("footnotes")
        val heat = booleanPreferencesKey("heat")
        val crossRefs = booleanPreferencesKey("crossRefs")
        val dictionaryWords = booleanPreferencesKey("dictionaryWords")
        val commentary = booleanPreferencesKey("commentary")
        val commentarySource = stringPreferencesKey("commentarySource")
        val lastRead = stringPreferencesKey("lastRead")
        val shareStats = booleanPreferencesKey("shareStats")
        val compare = stringPreferencesKey("compare")
        val recentSearches = stringPreferencesKey("recentSearches")
        val downloadWifiOnly = booleanPreferencesKey("downloadWifiOnly")
        val onlineChapters = stringPreferencesKey("onlineChapters")
        val offersDeclined = stringPreferencesKey("offersDeclined")
        val reminderMinutes = intPreferencesKey("reminderMinutes")
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            appearance = p[K.appearance]?.let { runCatching { Appearance.valueOf(it) }.getOrNull() } ?: d.appearance,
            uiLang = if (p[K.uiLang] == "en") UiLang.English else UiLang.Tamil,
            version = p[K.version] ?: d.version,
            fontSize = p[K.fontSize] ?: d.fontSize,
            typeface = p[K.typeface]?.let { runCatching { Typeface.valueOf(it) }.getOrNull() } ?: d.typeface,
            format = ReadingFormat.of(p[K.format]) ?: d.format,
            headings = p[K.headings] ?: d.headings,
            footnotes = p[K.footnotes] ?: d.footnotes,
            heat = p[K.heat] ?: d.heat,
            crossRefs = p[K.crossRefs] ?: d.crossRefs,
            dictionaryWords = p[K.dictionaryWords] ?: d.dictionaryWords,
            commentary = p[K.commentary] ?: d.commentary,
            commentarySource = p[K.commentarySource] ?: d.commentarySource,
            lastRead = p[K.lastRead]?.let(::parsePassage),
            shareStats = p[K.shareStats] ?: d.shareStats,
            compare = p[K.compare],
            recentSearches = p[K.recentSearches]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            downloadWifiOnly = p[K.downloadWifiOnly] ?: d.downloadWifiOnly,
            onlineChapters = p[K.onlineChapters]?.split(',')?.mapNotNull { e ->
                e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } }
            }?.toMap().orEmpty(),
            offersDeclined = p[K.offersDeclined]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            reminderMinutes = p[K.reminderMinutes],
        )
    }

    val settings: Flow<Settings> = context.settingsStore.data.map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { p ->
            val cur = read(p)
            val n = transform(cur)
            p[K.appearance] = n.appearance.name
            p[K.uiLang] = n.uiLang.code
            p[K.version] = n.version
            p[K.fontSize] = n.fontSize
            p[K.typeface] = n.typeface.name
            p[K.format] = n.format.key
            p[K.headings] = n.headings
            p[K.footnotes] = n.footnotes
            p[K.heat] = n.heat
            p[K.crossRefs] = n.crossRefs
            p[K.dictionaryWords] = n.dictionaryWords
            p[K.commentary] = n.commentary
            p[K.commentarySource] = n.commentarySource
            n.lastRead?.let { p[K.lastRead] = formatPassage(it) }
            p[K.shareStats] = n.shareStats
            if (n.compare != null) p[K.compare] = n.compare else p.remove(K.compare)
            p[K.recentSearches] = n.recentSearches.joinToString("\n")
            p[K.downloadWifiOnly] = n.downloadWifiOnly
            p[K.onlineChapters] = n.onlineChapters.entries.joinToString(",") { "${it.key}=${it.value}" }
            p[K.offersDeclined] = n.offersDeclined.joinToString(",")
            if (n.reminderMinutes != null) p[K.reminderMinutes] = n.reminderMinutes else p.remove(K.reminderMinutes)
        }
    }

    suspend fun addRecentSearch(text: String) {
        val q = text.trim().replace(Regex("\\s+"), " ")
        if (q.isEmpty() || q.length > 120) return
        update { s -> s.copy(recentSearches = (listOf(q) + s.recentSearches.filter { !it.equals(q, ignoreCase = true) }).take(8)) }
    }

    suspend fun clearRecentSearches() = update { it.copy(recentSearches = emptyList()) }

    private fun formatPassage(p: Passage) = listOf(p.version, p.book, p.chapter, p.verse ?: 0).joinToString("/")

    private fun parsePassage(s: String): Passage? {
        val parts = s.split('/')
        if (parts.size < 3) return null
        val ch = parts[2].toIntOrNull() ?: return null
        val v = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }
        return Passage(parts[0], parts[1], ch, v)
    }
}
