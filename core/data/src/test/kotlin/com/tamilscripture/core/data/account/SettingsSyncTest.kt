package com.tamilscripture.core.data.account

import com.tamilscripture.core.data.settings.SettingsRepository
import com.tamilscripture.core.model.UiLang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.file.Files

/** A profiles row held in memory, answering the two calls SettingsSync makes. */
private class FakeProfiles(var settings: JsonObject) : Supabase() {
    var patches = 0
    override suspend fun request(method: String, path: String, body: String?, token: String?, prefer: String?): String = when (method) {
        "GET" -> """[{"settings":$settings}]"""
        "PATCH" -> {
            patches++
            settings = Json.parseToJsonElement(body!!).jsonObject["settings"]!!.jsonObject
            ""
        }
        else -> error("unexpected $method $path")
    }
}

@RunWith(RobolectricTestRunner::class)
class SettingsSyncTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = Files.createTempDirectory("sync").toFile()
    private val repo = SettingsRepository(RuntimeEnvironment.getApplication())
    private val any: (String) -> Boolean = { true }

    @Test fun emptyAccountTakesTheDevicesAndKeepsOtherKeys() = runTest {
        repo.update { it.copy(heat = true, uiLang = UiLang.English) }
        val api = FakeProfiles(Json.parseToJsonElement("""{"railWidth":320}""").jsonObject)
        SettingsSync(dir, api, repo, json).sync("t", "u1", any)
        assertEquals(1, api.patches)
        assertTrue(api.settings["heat"]!!.jsonPrimitive.boolean)
        assertEquals("en", api.settings["uiLang"]!!.jsonPrimitive.content)
        assertEquals("320", api.settings["railWidth"]!!.jsonPrimitive.content)
    }

    @Test fun anAccountWithSettingsWinsAtFirstThenLocalChangesGoUp() = runTest {
        val api = FakeProfiles(Json.parseToJsonElement("""{"footnotes":true,"version":"tcv","uiLang":"en"}""").jsonObject)
        val sync = SettingsSync(dir, api, repo, json)
        sync.sync("t", "u1", any)
        val s = repo.settings.first()
        assertEquals(0, api.patches)
        assertTrue(s.footnotes)
        assertEquals("TCV", s.version)
        assertEquals(UiLang.English, s.uiLang)

        repo.update { it.copy(headings = false) }
        sync.sync("t", "u1", any)
        assertEquals(1, api.patches)
        assertEquals(false, api.settings["headings"]!!.jsonPrimitive.boolean)

        // Changed on the website since: taken, nothing sent.
        api.settings = JsonObject(api.settings + ("heat" to kotlinx.serialization.json.JsonPrimitive(true)))
        sync.sync("t", "u1", any)
        assertEquals(1, api.patches)
        assertTrue(repo.settings.first().heat)
    }

    @Test fun anUnknownVersionIsNotTaken() = runTest {
        val api = FakeProfiles(Json.parseToJsonElement("""{"version":"nope"}""").jsonObject)
        SettingsSync(dir, api, repo, json).sync("t", "u1") { it != "NOPE" }
        assertEquals("IRVTAM", repo.settings.first().version)
    }
}
