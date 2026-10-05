package com.tamilscripture.app

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import kotlinx.serialization.Serializable

/** Top-level tabs (design: வேதம், திட்டங்கள், ஆய்வு, தேடல்). */
@Serializable data object HomeRoute : NavKey
@Serializable data object PlansRoute : NavKey
@Serializable data object StudyRoute : NavKey
@Serializable data class SearchRoute(val focus: Boolean = false, val query: String? = null) : NavKey

/** Full-screen destinations above the tabs. */
/** [compare] opens this reader side by side (a website link such as /irvtam+kjv/…). */
/** [select] false: Bible and Continue reading scroll to the verse without selecting it. */
@Serializable data class ReaderRoute(val passage: Passage, val compare: Boolean = false, val select: Boolean = true) : NavKey
@Serializable data class PickerRoute(val passage: Passage) : NavKey
@Serializable data class CommentaryRoute(val passage: Passage) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object DownloadsRoute : NavKey

/** The account and the reader's own marks (M6). [tab]: highlights, notes, bookmarks, history. */
@Serializable data object AccountRoute : NavKey
@Serializable data class MineRoute(val tab: Int = 0) : NavKey

/** Study pages (roadmap M8). */
@Serializable data class StrongsRoute(val number: String) : NavKey
@Serializable data class PersonRoute(val id: String) : NavKey
@Serializable data class PlaceRoute(val id: String) : NavKey
@Serializable data class ArticleRoute(val id: String) : NavKey
@Serializable data object DictionaryRoute : NavKey
@Serializable data object RootWordsRoute : NavKey
@Serializable data class AtlasRoute(val focus: String? = null) : NavKey
@Serializable data class PresentRoute(val passage: Passage) : NavKey

/** Home is the landing page (the cross); Bible opens the reader at the last chapter read. */
enum class Tab { Home, Bible, Plans, Study, Search }

/** The tab a screen is the root of; the reader is reached through Bible but opens over the tabs. */
fun NavKey.tab(): Tab? = when (this) {
    HomeRoute -> Tab.Home
    PlansRoute -> Tab.Plans
    StudyRoute -> Tab.Study
    is SearchRoute -> Tab.Search
    else -> null
}

/** The tab to show as selected: the reader and its picker count as Bible. */
fun NavKey.selectedTab(): Tab? = tab() ?: when (this) {
    is ReaderRoute, is PickerRoute, is CommentaryRoute, is PresentRoute -> Tab.Bible
    else -> null
}

/** Where a website link leads, and the second version when it names two (dual view). */
data class DeepLink(val route: NavKey, val compare: String? = null)

/**
 * Website links (A-2.10): `/irvtam/john/3`, `/irvtam/john/3/16`, `/irvtam/john/3.16`,
 * `/john/3`, `/irvtam+kjv/john/3` (dual view, as on the website) and `/search?q=…`.
 * Anything else opens the home screen.
 */
fun parseDeepLink(uri: Uri, manifest: ContentManifest, defaultVersion: String): DeepLink? {
    val seg = uri.pathSegments.filter { it.isNotBlank() }
    if (seg.isEmpty()) return null
    if (seg.size == 1 && seg[0] == "search") return DeepLink(SearchRoute(query = uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }))
    val codes = manifest.versions.associateBy { it.code.lowercase() }
    val parts = seg[0].lowercase().split('+')
    val hasVersion = parts.size <= 2 && parts.all { it in codes }
    val version = if (hasVersion) codes.getValue(parts[0]).code else defaultVersion
    val compare = if (hasVersion) parts.getOrNull(1)?.let { codes.getValue(it).code } else null
    val rest = if (hasVersion) seg.drop(1) else seg
    if (rest.size < 2) return null
    val book = manifest.books.firstOrNull { b -> b.slug == rest[0].lowercase() || rest[0].lowercase() in b.abbrEn.map { it.lowercase() } || b.code.equals(rest[0], true) }
        ?: return null
    val chapterPart = rest[1]
    val chapter = chapterPart.substringBefore('.').toIntOrNull() ?: return null
    val verse = chapterPart.substringAfter('.', "").substringBefore('-').toIntOrNull()
        ?: rest.getOrNull(2)?.substringBefore('-')?.toIntOrNull()
    return DeepLink(ReaderRoute(Passage(version, book.code, chapter, verse), compare = compare != null), compare)
}
