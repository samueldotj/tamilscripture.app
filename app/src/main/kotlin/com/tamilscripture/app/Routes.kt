package com.tamilscripture.app

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.Passage
import kotlinx.serialization.Serializable

/** Top-level tabs (design: வேதம், திட்டங்கள், ஆய்வு, தேடல்). */
@Serializable data object HomeRoute : NavKey
@Serializable data object PlansRoute : NavKey
@Serializable data object StudyRoute : NavKey
@Serializable data class SearchRoute(val focus: Boolean = false) : NavKey

/** Full-screen destinations above the tabs. */
@Serializable data class ReaderRoute(val passage: Passage) : NavKey
@Serializable data class PickerRoute(val passage: Passage) : NavKey
@Serializable data class CommentaryRoute(val passage: Passage) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object DownloadsRoute : NavKey

enum class Tab { Home, Plans, Study, Search }

fun NavKey.tab(): Tab? = when (this) {
    HomeRoute -> Tab.Home
    PlansRoute -> Tab.Plans
    StudyRoute -> Tab.Study
    is SearchRoute -> Tab.Search
    else -> null
}

/**
 * Website links (A-2.10): `/irvtam/john/3`, `/irvtam/john/3/16`, `/irvtam/john/3.16`,
 * `/irvtam+bsb/john/3` (first version), `/john/3`. Anything else opens the home screen.
 */
fun parseDeepLink(uri: Uri, books: List<Book>, defaultVersion: String): Passage? {
    val seg = uri.pathSegments.filter { it.isNotBlank() }
    if (seg.isEmpty()) return null
    val versionCodes = setOf("irvtam", "tcv", "tov", "bsb", "web", "kjv")
    val first = seg[0].lowercase()
    val hasVersion = first.split('+').first() in versionCodes
    val version = if (hasVersion) first.split('+').first().uppercase() else defaultVersion
    val rest = if (hasVersion) seg.drop(1) else seg
    if (rest.size < 2) return null
    val book = books.firstOrNull { b -> b.slug == rest[0].lowercase() || rest[0].lowercase() in b.abbrEn.map { it.lowercase() } || b.code.equals(rest[0], true) }
        ?: return null
    val chapterPart = rest[1]
    val chapter = chapterPart.substringBefore('.').toIntOrNull() ?: return null
    val verse = chapterPart.substringAfter('.', "").substringBefore('-').toIntOrNull()
        ?: rest.getOrNull(2)?.substringBefore('-')?.toIntOrNull()
    return Passage(version, book.code, chapter, verse)
}
