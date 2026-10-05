package com.tamilscripture.core.data.user

import com.tamilscripture.core.data.account.AccountRepository
import com.tamilscripture.core.data.testing.FakeSupabase
import com.tamilscripture.core.model.HighlightColor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M6-11: two devices and the website on one account, through [FakeSupabase]. Offline edits
 * wait and then go up, deletes reach the other side, the website's edits come down, and
 * whatever was made before signing in joins the account.
 */
class SyncTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val server = FakeSupabase("u1")

    /** One device, signed in as u1 (or not yet). */
    private fun device(signedIn: Boolean = true): UserDataRepository {
        val dir = Files.createTempDirectory("device").toFile()
        val account = File(dir, "account").apply { mkdirs() }
        if (signedIn) File(account, "session.json").writeText(
            """{"access_token":"t","refresh_token":"r","expires_at":${System.currentTimeMillis() / 1000 + 3600},"user_id":"u1","email":"a@b.c"}""",
        )
        return UserDataRepository(File(dir, "user"), server, AccountRepository(account, server, json), json)
    }

    @Test fun anEditOnOneDeviceReachesTheOther() = runTest {
        val phone = device()
        val tablet = device()
        phone.setHighlight("JHN", 3, listOf(16, 17), HighlightColor.Blue)
        phone.saveNote(null, "JHN", 3, 16, 16, "For God so loved")
        assertTrue(phone.sync())
        assertTrue(tablet.sync())
        assertEquals(listOf(16 to 17), tablet.data.value.highlights.map { it.verseStart to it.verseEnd })
        assertEquals("blue", tablet.data.value.highlights.single().color)
        assertEquals("For God so loved", tablet.data.value.notes.single().body)
        assertTrue(tablet.data.value.dirtyHighlights.isEmpty())
    }

    @Test fun offlineEditsWaitAndThenGoUp() = runTest {
        val phone = device()
        phone.sync()
        server.online = false
        phone.setHighlight("PSA", 23, listOf(1), HighlightColor.Yellow)
        phone.recordVisit("PSA", 23, "IRVTAM", null)
        runCatching { phone.sync() }
        assertEquals(1, phone.data.value.dirtyHighlights.size)
        assertEquals(1, phone.data.value.pendingVisits.size)
        server.online = true
        phone.sync()
        assertEquals(1, server.highlights.size)
        assertEquals(1, server.history.size)
        assertTrue(phone.data.value.pendingVisits.isEmpty())
        assertEquals(1, phone.data.value.history.size)
    }

    @Test fun deletesReachTheOtherDevice() = runTest {
        val phone = device()
        val tablet = device()
        phone.setHighlight("ROM", 8, listOf(28), HighlightColor.Green)
        phone.sync()
        tablet.sync()
        tablet.removeHighlight("ROM", 8, listOf(28))
        tablet.sync()
        assertTrue(server.highlights.isEmpty())
        phone.sync()
        assertTrue(phone.data.value.highlights.isEmpty())
    }

    @Test fun theWebsitesEditsComeDownAndItsDeletesToo() = runTest {
        val phone = device()
        phone.saveNote(null, "GEN", 1, 1, 1, "In the beginning")
        phone.sync()
        val id = server.notes.keys.single()
        server.websiteUpsert("notes", JsonObject(server.notes.getValue(id) + ("body" to JsonPrimitive("Edited on the website"))))
        server.websiteUpsert(
            "highlights",
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("w1"), "book" to JsonPrimitive("JHN"), "chapter" to JsonPrimitive(1),
                    "verse_start" to JsonPrimitive(1), "verse_end" to JsonPrimitive(1), "color" to JsonPrimitive("pink"),
                    "version" to JsonPrimitive("IRVTAM"), "char_start" to JsonPrimitive(4), "char_end" to JsonPrimitive(20),
                ),
            ),
        )
        phone.sync()
        assertEquals("Edited on the website", phone.data.value.notes.single().body)
        assertEquals(4, phone.data.value.highlights.single().charStart)

        server.notes.keys.toList().forEach { server.websiteDelete("notes", it) }
        phone.sync()
        assertTrue(phone.data.value.notes.isEmpty())
    }

    @Test fun whatWasMadeBeforeSigningInJoinsTheAccount() = runTest {
        server.websiteUpsert(
            "notes",
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("w-note"), "book" to JsonPrimitive("MAT"), "chapter" to JsonPrimitive(5),
                    "verse_start" to JsonPrimitive(3), "verse_end" to JsonPrimitive(3), "body" to JsonPrimitive("from the website"),
                ),
            ),
        )
        val dir = Files.createTempDirectory("device").toFile()
        val account = File(dir, "account").apply { mkdirs() }
        val repo = UserDataRepository(File(dir, "user"), server, AccountRepository(account, server, json), json)
        repo.saveNote(null, "MAT", 5, 4, 4, "made signed out")
        repo.setHighlight("MAT", 5, listOf(9), HighlightColor.Yellow)
        // Signing in (the session arrives), then the first sync.
        File(account, "session.json").writeText(
            """{"access_token":"t","refresh_token":"r","expires_at":${System.currentTimeMillis() / 1000 + 3600},"user_id":"u1"}""",
        )
        val signedIn = UserDataRepository(File(dir, "user"), server, AccountRepository(account, server, json), json)
        signedIn.sync()
        assertEquals(setOf("from the website", "made signed out"), signedIn.data.value.notes.map { it.body }.toSet())
        assertEquals(1, server.highlights.size)
        assertEquals("u1", server.highlights.values.single()["user_id"]!!.jsonPrimitive.content)
    }

    @Test fun clearingHistoryClearsItOnTheAccount() = runTest {
        val phone = device()
        phone.recordVisit("JHN", 3, "IRVTAM", null)
        phone.recordVisit("JHN", 4, "IRVTAM", null)
        phone.sync()
        assertEquals(2, server.history.size)
        phone.clearHistory()
        phone.sync()
        assertTrue(server.history.isEmpty())
        assertTrue(phone.visits().isEmpty())
    }

    @Test fun mixedRowsGoUpInOneBatch() = runTest {
        // A word-range highlight from the website, changed on the phone, beside a whole-verse one:
        // PostgREST refuses a batch whose rows have different keys.
        val phone = device()
        phone.setHighlight("JHN", 3, listOf(1), HighlightColor.Yellow)
        phone.setHighlight("JHN", 3, listOf(5), HighlightColor.Green)
        assertTrue(phone.sync())
        assertEquals(2, server.highlights.size)
    }

    @Test fun bookmarksFollowTheAccountOnePerVerse() = runTest {
        val phone = device()
        val tablet = device()
        phone.toggleBookmark("JHN", 3, 16, "IRVTAM")
        tablet.toggleBookmark("JHN", 3, 16, "BSB")
        phone.sync()
        tablet.sync()
        assertEquals(1, server.bookmarks.size)
        assertEquals(phone.data.value.bookmarks.single().id, tablet.data.value.bookmarks.single().id)

        tablet.toggleBookmark("JHN", 3, 16, "BSB")
        tablet.sync()
        assertTrue(server.bookmarks.isEmpty())
        phone.sync()
        assertTrue(phone.data.value.bookmarks.isEmpty())
    }

    @Test fun laterPullsAskOnlyForChanges() = runTest {
        val phone = device()
        val tablet = device()
        phone.setHighlight("JHN", 1, listOf(1), HighlightColor.Yellow)
        phone.saveNote(null, "JHN", 1, 2, 2, "the Word")
        phone.sync()
        tablet.sync()
        server.gets.clear()

        phone.removeHighlight("JHN", 1, listOf(1))
        phone.setHighlight("JHN", 1, listOf(14), HighlightColor.Pink)
        phone.sync()
        tablet.sync()
        assertTrue(server.gets.any { it.startsWith("/rest/v1/deleted_rows") })
        assertTrue(server.gets.filter { it.startsWith("/rest/v1/highlights") }.all { "updated_at=gte." in it })
        assertEquals(listOf(14), tablet.data.value.highlights.map { it.verseStart })
        assertEquals("the Word", tablet.data.value.notes.single().body)
        assertTrue(tablet.data.value.cursor != null)
    }
}
