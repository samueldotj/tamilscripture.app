package com.tamilscripture.core.data.account

import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.data.settings.SettingsRepository
import com.tamilscripture.core.data.settings.Typeface
import com.tamilscripture.core.model.UiLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Reader settings in the account (M6-9): `profiles.settings`, under the website's own keys,
 * so the website and the app agree on one set. Only the keys both have are touched; others
 * (the website's rail widths, say) are left as they are. Per-device choices stay local:
 * appearance, text size, downloads, the reminder.
 *
 * Three-way: what this device last sent is remembered. A local change since then is sent
 * (the newer edit wins); otherwise the account's values are taken.
 */
class SettingsSync(private val dir: File, private val api: Supabase, private val settings: SettingsRepository, private val json: Json) {
    @Serializable
    private data class State(val owner: String, val sent: Map<String, String>)

    private val file = File(dir, "settings-sync.json")

    /** The shared keys of [s], as the website writes them (strings, for comparison). */
    private fun shared(s: Settings): Map<String, String> = mapOf(
        "uiLang" to s.uiLang.code,
        "version" to s.version.lowercase(),
        "tamilFont" to when (s.typeface) { Typeface.MuktaMalar -> "mukta"; Typeface.NotoSansTamil -> "sans"; Typeface.NotoSerifTamil -> "serif" },
        "headings" to s.headings.toString(),
        "footnotes" to s.footnotes.toString(),
        "xrefs" to s.crossRefs.toString(),
        "heat" to s.heat.toString(),
        "commentary" to s.commentary.toString(),
        "commentarySource" to s.commentarySource,
    )

    private fun apply(s: Settings, r: Map<String, String>, versionExists: (String) -> Boolean): Settings {
        fun bool(k: String, d: Boolean) = r[k]?.toBooleanStrictOrNull() ?: d
        return s.copy(
            uiLang = UiLang.entries.firstOrNull { it.code == r["uiLang"] } ?: s.uiLang,
            version = r["version"]?.uppercase()?.takeIf(versionExists) ?: s.version,
            typeface = when (r["tamilFont"]) { "mukta" -> Typeface.MuktaMalar; "sans" -> Typeface.NotoSansTamil; "serif" -> Typeface.NotoSerifTamil; else -> s.typeface },
            headings = bool("headings", s.headings),
            footnotes = bool("footnotes", s.footnotes),
            crossRefs = bool("xrefs", s.crossRefs),
            heat = bool("heat", s.heat),
            commentary = bool("commentary", s.commentary),
            commentarySource = r["commentarySource"]?.takeIf { it.isNotBlank() } ?: s.commentarySource,
        )
    }

    private fun JsonElement.asText(): String? = (this as? JsonPrimitive)?.let { p -> p.booleanOrNull?.toString() ?: p.content }

    suspend fun sync(token: String, userId: String, versionExists: (String) -> Boolean) {
        val row = (json.parseToJsonElement(api.request("GET", "/rest/v1/profiles?select=settings", null, token)) as? JsonArray)?.firstOrNull()
            ?: return
        val remoteObj = (row.jsonObject["settings"] as? JsonObject) ?: JsonObject(emptyMap())
        val local = shared(settings.settings.first())
        val remote = local.keys.mapNotNull { k -> remoteObj[k]?.asText()?.let { k to it } }.toMap()
        val state = withContext(Dispatchers.IO) { runCatching { json.decodeFromString<State>(file.readText()) }.getOrNull() }
        val known = state?.takeIf { it.owner == userId }
        val push = when {
            // First time with this account: an account that has settings wins; an empty one takes this device's.
            known == null -> remote.isEmpty()
            local != known.sent -> true
            else -> false
        }
        val result = if (push) {
            val merged = buildJsonObject {
                remoteObj.forEach { (k, v) -> put(k, v) }
                local.forEach { (k, v) -> put(k, v.toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(v)) }
            }
            api.request("PATCH", "/rest/v1/profiles?user_id=eq.$userId", buildJsonObject { put("settings", merged) }.toString(), token, "return=minimal")
            local
        } else {
            if (remote.isNotEmpty() && remote != local) settings.update { apply(it, remote, versionExists) }
            shared(settings.settings.first())
        }
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            file.writeText(json.encodeToString(State.serializer(), State(userId, result)))
        }
    }

    fun forget() { file.delete() }
}
