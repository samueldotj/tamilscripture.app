package com.tamilscripture.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons copied from the design's inline SVGs (24×24, stroke 2, round caps).
 * Tinted by the caller through `Icon(tint = …)`.
 */
object TsIcons {
    private fun line(name: String, vararg paths: String, filled: Set<Int> = emptySet()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEachIndexed { i, d ->
                if (i in filled) {
                    addPath(addPathNodes(d), fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()

    val Book = line(
        "book",
        "M4 4h6a3 3 0 0 1 3 3v13a2 2 0 0 0-2-2H4z",
        "M20 4h-6a3 3 0 0 0-3 3v13a2 2 0 0 1 2-2h7z",
    )
    val Calendar = line(
        "calendar",
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M3 10h18M8 3v4M16 3v4",
        "M9 15l2 2 4-4",
    )
    val Compass = line(
        "compass",
        "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z",
        "M15.5 8.5l-2 5-5 2 2-5z",
    )
    val Search = line(
        "search",
        "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z",
        "M20 20l-4-4",
    )
    val StudySettings = line(
        "study_settings",
        "M4 6h16M4 12h16M4 18h10",
        "M19 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0z",
    )
    val Commentary = line("commentary", "M4 5h16v14H4z", "M8 9h8M8 13h5")
    val Link = line("link", "M7 17l10-10M9 7h8v8")
    val Bookmark = line("bookmark", "M6 3h12v18l-6-4-6 4z")
    val Filter = line("filter", "M4 7h16M7 12h10M10 17h4")
    val ChevronLeft = line("chevron_left", "M15 18l-6-6 6-6")
    val ChevronRight = line("chevron_right", "M9 18l6-6-6-6")
    val ChevronDown = line("chevron_down", "M6 9l6 6 6-6")
    val ChevronUp = line("chevron_up", "M6 15l6-6 6 6")
    val Compare = line("compare", "M4 5h7v14H4z", "M13 5h7v14h-7z")
    val Close = line("close", "M18 6L6 18M6 6l12 12")
    val Plus = line("plus", "M12 5v14M5 12h14")
    val Minus = line("minus", "M5 12h14")
    val Check = line("check", "M5 12l5 5 9-10")
    val Target = line("target", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M13.5 12a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0z")
    val Play = line("play", "M8 5v14l11-7z", filled = setOf(0))
    val Pause = line("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z", filled = setOf(0))
    val Share = line("share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7", "M12 3v12M8 7l4-4 4 4")
    val Copy = line("copy", "M9 9h11v11H9z", "M5 15H4V4h11v1")
    val Note = line("note", "M5 4h10l4 4v12H5z", "M15 4v4h4", "M8 13h8M8 17h5")
    val Highlight = line("highlight", "M4 20h7", "M14.5 4.5l5 5L10 19H5v-5z")
    val ListView = line("list", "M9 6h11M9 12h11M9 18h11", "M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    val MapView = line("map", "M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z", "M9 4v14M15 6v14")
    private const val STAR = "M12 2.5l2.9 5.9 6.6.9-4.8 4.6 1.1 6.5L12 17.3l-5.8 3.1 1.1-6.5-4.8-4.6 6.6-.9z"
    /** Bookmark: a star, filled once the verse is bookmarked. */
    val Star = line("star", STAR)
    val StarFilled = line("star_filled", STAR, filled = setOf(0))
    /** Write or edit a note. */
    val Edit = line("edit", "M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z", "M15 5l4 4")
    val People = line(
        "people",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0z",
        "M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75",
    )
}
