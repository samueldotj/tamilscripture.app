package com.tamilscripture.core.data.user

import com.tamilscripture.core.data.account.AccountRepository
import com.tamilscripture.core.data.account.Supabase
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.contiguousRuns
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UserDataRepositoryTest {
    private lateinit var dir: File
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun repo(): UserDataRepository {
        val api = Supabase().apply { guard = { error("no network in this test: $it") } }
        return UserDataRepository(File(dir, "user"), api, AccountRepository(File(dir, "account"), api, json), json)
    }

    @Before fun setUp() { dir = Files.createTempDirectory("user").toFile() }

    @Test fun runs() {
        assertEquals(listOf(1..3, 5..5, 7..8), contiguousRuns(listOf(8, 1, 2, 3, 5, 7)))
        assertEquals(emptyList<IntRange>(), contiguousRuns(emptyList()))
    }

    @Test fun highlightIsOneRowPerRun() = runTest {
        val r = repo()
        r.setHighlight("JHN", 3, listOf(16, 17, 19), HighlightColor.Green)
        val rows = r.data.value.highlights.sortedBy { it.verseStart }
        assertEquals(listOf(16 to 17, 19 to 19), rows.map { it.verseStart to it.verseEnd })
        assertTrue(rows.all { it.color == "green" && it.id in r.data.value.dirtyHighlights })
    }

    @Test fun removingTheMiddleSplitsTheRow() = runTest {
        val r = repo()
        r.setHighlight("JHN", 3, (14..18).toList(), HighlightColor.Yellow)
        val id = r.data.value.highlights.single().id
        r.removeHighlight("JHN", 3, listOf(16))
        val rows = r.data.value.highlights.sortedBy { it.verseStart }
        assertEquals(listOf(14 to 15, 17 to 18), rows.map { it.verseStart to it.verseEnd })
        // The first part keeps the row's id (an update on the server); the second is new.
        assertEquals(id, rows[0].id)
        assertTrue(rows.all { it.id in r.data.value.dirtyHighlights })
        assertFalse(id in r.data.value.deletedHighlights)
    }

    @Test fun recolouringReplaces() = runTest {
        val r = repo()
        r.setHighlight("JHN", 3, listOf(16), HighlightColor.Yellow)
        val old = r.data.value.highlights.single().id
        r.setHighlight("JHN", 3, listOf(16), HighlightColor.Pink)
        assertEquals("pink", r.data.value.highlights.single().color)
        assertTrue(old in r.data.value.deletedHighlights)
    }

    @Test fun notesAndBookmarksSurviveARestart() = runTest {
        val r = repo()
        val id = r.saveNote(null, "PSA", 23, 1, 2, "The Lord is my shepherd")
        r.toggleBookmark("PSA", 23, 1, "irvtam")
        val again = repo()
        assertEquals("The Lord is my shepherd", again.data.value.notes.single { it.id == id }.body)
        assertEquals(1, again.data.value.bookmarks.size)
        again.toggleBookmark("PSA", 23, 1, "irvtam")
        assertTrue(again.data.value.bookmarks.isEmpty())
        again.deleteNote(id)
        assertTrue(id in again.data.value.deletedNotes)
    }

    @Test fun historySkipsRepeatsAndPauses() = runTest {
        val r = repo()
        r.recordVisit("JHN", 3, "irvtam", null)
        r.recordVisit("JHN", 3, "irvtam", 16)
        r.recordVisit("JHN", 4, "irvtam", null)
        assertEquals(2, r.visits().size)
        r.setHistoryPaused(true)
        r.recordVisit("JHN", 5, "irvtam", null)
        assertEquals(2, r.visits().size)
        r.clearHistory()
        assertTrue(r.visits().isEmpty())
        assertTrue(r.data.value.clearHistory)
    }

    @Test fun signedOutSyncMakesNoCalls() = runTest {
        assertFalse(repo().sync())
    }

    @Test fun aWordRangeReplacesTheRangesItOverlaps() = runTest {
        val r = repo()
        r.setRangeHighlight("JHN", 3, 16, "IRVTAM", 0, 10, "first", HighlightColor.Yellow)
        r.setRangeHighlight("JHN", 3, 16, "IRVTAM", 20, 30, "second", HighlightColor.Blue)
        r.setRangeHighlight("JHN", 3, 16, "IRVTAM", 5, 25, "across", HighlightColor.Pink)
        val rows = r.data.value.highlights
        assertEquals(listOf("across"), rows.map { it.quote })
        assertEquals(5 to 25, rows.single().charStart to rows.single().charEnd)
        assertEquals(2, r.data.value.deletedHighlights.size)
        // Another version's range is left alone.
        r.setRangeHighlight("JHN", 3, 16, "BSB", 0, 4, "For", HighlightColor.Green)
        r.removeRangeHighlight("JHN", 3, 16, "IRVTAM", 0, 100)
        assertEquals(listOf("For"), r.data.value.highlights.map { it.quote })
    }
}
