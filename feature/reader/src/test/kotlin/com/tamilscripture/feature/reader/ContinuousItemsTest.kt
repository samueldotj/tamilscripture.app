package com.tamilscripture.feature.reader

import com.tamilscripture.core.model.Block
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.ChapterRef
import com.tamilscripture.core.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Continuous scrolling: the previews either side, and keys that survive opening a neighbour. */
class ContinuousItemsTest {
    private fun chapter(n: Int, verses: Int) = Chapter(
        "IRVTAM", "MAT", n,
        blocks = listOf(Block("para", segments = (1..verses).map { Segment("MAT.$n.$it", "$it", "verse $it of $n") })),
        prev = if (n > 1) ChapterRef("MAT", n - 1) else ChapterRef("MAL", 4),
        next = ChapterRef("MAT", n + 1),
    )

    private fun items(main: Chapter, prev: Chapter?, next: Chapter?) =
        continuousItems(main, main.toItems(false), prev?.let { it to it.toItems(false) }, next?.let { it to it.toItems(false) }) { "Matthew ${it.chapter}" }

    @Test fun previewsEitherSide() {
        val list = items(chapter(2, 23), chapter(1, 25), chapter(3, 17))
        assertEquals(PREVIEW_ROWS, list.takeWhile { it is ReaderItem.Preview }.size)
        val title = list.indexOfFirst { it is ReaderItem.ChapterTitle && !it.preview }
        assertEquals(PREVIEW_ROWS, title)
        assertEquals(1, list.indexOfVerse(1) - title)
        assertTrue(list.takeLast(PREVIEW_ROWS).all { it is ReaderItem.Preview })
        // Previews answer to no verse: selection and scrolling find only this chapter's.
        assertEquals(list.indexOfFirst { it is ReaderItem.Verse }, list.indexOfVerse(1))
    }

    @Test fun openingTheNextChapterKeepsItsRowsKeys() {
        val one = chapter(1, 25)
        val two = chapter(2, 23)
        val before = items(one, null, two)
        val after = items(two, one, null)
        val shown = before.dropWhile { !(it is ReaderItem.ChapterTitle && it.preview) }.map { it.key }
        assertTrue(shown.isNotEmpty())
        assertTrue(after.map { it.key }.containsAll(shown))
        // And the end of the chapter left behind is still there, greyed, above.
        assertTrue(after.map { it.key }.containsAll(before.filter { it.key.startsWith("MAT.1:v2") }.takeLast(PREVIEW_ROWS - 1).map { it.key }))
    }
}
