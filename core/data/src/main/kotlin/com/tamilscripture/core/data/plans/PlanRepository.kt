package com.tamilscripture.core.data.plans

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.SupabaseConfig
import com.tamilscripture.core.model.CommunityPlan
import com.tamilscripture.core.model.PlanProgress
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.plansStore: DataStore<Preferences> by preferencesDataStore("plans")

@Serializable
private data class StoredProgress(val plan: String, val start: String, val done: List<String>)

@Serializable
private data class ProgressRow(
    val plan: String,
    @kotlinx.serialization.SerialName("start_date") val startDate: String,
    val done: List<String> = emptyList(),
)

/**
 * Reading-plan progress, kept on the device (signed-out behaviour of the website, A-8.1).
 * Signed in, [sync] keeps it in the account's `plan_progress` (M7-4): the first sync after
 * signing in joins what this device has with what the account has, as the website does
 * on its first sign-in; after that the account's rows are the truth.
 */
class PlanRepository(private val context: Context, private val http: Http) {
    private val json = Json { ignoreUnknownKeys = true }
    private val communityFile = File(context.filesDir, "plans/community.json")
    private val communityState = MutableStateFlow(readCommunity())

    /** Published community plans (M7-3), kept on the device so they open offline. */
    val community: StateFlow<List<CommunityPlan>> = communityState

    private fun readCommunity(): List<CommunityPlan> =
        runCatching { json.decodeFromString<List<CommunityPlan>>(communityFile.readText()) }.getOrDefault(emptyList())

    /** The website's published plans, read the way the website does: anon PostgREST under RLS. */
    suspend fun refreshCommunity() {
        val url = "${SupabaseConfig.URL}/rest/v1/reading_plans?select=id,title_ta,title_en,blurb,days,tracks" +
            "&status=eq.published&order=published_at.asc&apikey=${SupabaseConfig.ANON_KEY}"
        val bytes = runCatching { http.getAbsolute(url) }.getOrNull() ?: return
        val rows = runCatching { json.decodeFromString<List<CommunityPlan>>(bytes.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            communityFile.parentFile?.mkdirs()
            File(communityFile.path + ".tmp").apply { writeBytes(bytes) }.renameTo(communityFile.also { it.delete() })
        }
        communityState.value = rows
    }
    private val progressKey = stringPreferencesKey("progress")
    private val activeKey = stringPreferencesKey("active")
    /** Plans changed or left on this device since the last sync, and the account they belong to. */
    private val dirtyKey = stringPreferencesKey("dirty")
    private val leftKey = stringPreferencesKey("left")
    private val ownerKey = stringPreferencesKey("owner")

    private fun set(raw: String?): Set<String> = raw?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    private fun Preferences.dirty(add: String) = (set(this[dirtyKey]) + add).joinToString(",")

    val progress: Flow<Map<String, PlanProgress>> = context.plansStore.data.map { p ->
        decode(p[progressKey])
    }

    val activePlan: Flow<String?> = context.plansStore.data.map { it[activeKey] }

    private fun decode(raw: String?): Map<String, PlanProgress> =
        raw?.let { runCatching { json.decodeFromString<List<StoredProgress>>(it) }.getOrNull() }
            .orEmpty()
            .associate { it.plan to PlanProgress(LocalDate.parse(it.start), it.done.toSet()) }

    private fun encode(m: Map<String, PlanProgress>): String =
        json.encodeToString(m.map { (k, v) -> StoredProgress(k, v.start.toString(), v.done.sorted()) })

    suspend fun join(planId: String, start: LocalDate = LocalDate.now()) {
        context.plansStore.edit { p ->
            val m = decode(p[progressKey]).toMutableMap()
            if (planId !in m) m[planId] = PlanProgress(start, emptySet())
            p[progressKey] = encode(m)
            p[activeKey] = planId
            p[dirtyKey] = p.dirty(planId)
            p[leftKey] = (set(p[leftKey]) - planId).joinToString(",")
        }
    }

    suspend fun leave(planId: String) {
        context.plansStore.edit { p ->
            val m = decode(p[progressKey]) - planId
            p[progressKey] = encode(m)
            p[leftKey] = (set(p[leftKey]) + planId).joinToString(",")
            p[dirtyKey] = (set(p[dirtyKey]) - planId).joinToString(",")
            if (p[activeKey] == planId) {
                val next = m.keys.firstOrNull()
                if (next != null) p[activeKey] = next else p.remove(activeKey)
            }
        }
    }

    suspend fun setActive(planId: String) {
        context.plansStore.edit { it[activeKey] = planId }
    }

    suspend fun toggle(planId: String, key: String, read: Boolean) {
        context.plansStore.edit { p ->
            val m = decode(p[progressKey]).toMutableMap()
            val cur = m[planId] ?: return@edit
            m[planId] = cur.copy(done = if (read) cur.done + key else cur.done - key)
            p[progressKey] = encode(m)
            p[dirtyKey] = p.dirty(planId)
        }
    }

    /** Whether anything waits to reach the account. */
    val pending: Flow<Boolean> = context.plansStore.data.map { set(it[dirtyKey]).isNotEmpty() || set(it[leftKey]).isNotEmpty() }

    suspend fun sync(api: com.tamilscripture.core.data.account.Supabase, token: String, userId: String) {
        val prefs = context.plansStore.data.first()
        if (prefs[ownerKey] != null && prefs[ownerKey] != userId) clear()
        val local = decode(prefs[progressKey])
        val firstSync = prefs[ownerKey] != userId
        val remote = json.decodeFromString<List<ProgressRow>>(api.request("GET", "/rest/v1/plan_progress?select=plan,start_date,done", null, token))
            .associate { it.plan to PlanProgress(LocalDate.parse(it.startDate), it.done.toSet()) }
        val push = if (firstSync) {
            // Both sides kept: the earlier start, every reading marked on either.
            local.mapValues { (k, v) -> remote[k]?.let { r -> PlanProgress(minOf(r.start, v.start), r.done + v.done) } ?: v }
        } else local.filterKeys { it in set(prefs[dirtyKey]) }
        if (push.isNotEmpty()) {
            val rows = push.entries.joinToString(",", "[", "]") { (k, v) ->
                kotlinx.serialization.json.buildJsonObject {
                    put("user_id", kotlinx.serialization.json.JsonPrimitive(userId))
                    put("plan", kotlinx.serialization.json.JsonPrimitive(k))
                    put("start_date", kotlinx.serialization.json.JsonPrimitive(v.start.toString()))
                    put("done", kotlinx.serialization.json.JsonArray(v.done.sorted().map { kotlinx.serialization.json.JsonPrimitive(it) }))
                }.toString()
            }
            api.request("POST", "/rest/v1/plan_progress?on_conflict=user_id,plan", rows, token, "resolution=merge-duplicates,return=minimal")
        }
        val left = if (firstSync) emptySet() else set(prefs[leftKey])
        for (plan in left) api.request("DELETE", "/rest/v1/plan_progress?plan=eq.$plan", null, token)
        val merged = (remote - left) + push
        context.plansStore.edit { p ->
            // Changes made while this ran stay for the next sync.
            val now = decode(p[progressKey])
            val newer = set(p[dirtyKey]) - set(prefs[dirtyKey])
            p[progressKey] = encode(merged + now.filterKeys { it in newer })
            p[dirtyKey] = newer.joinToString(",")
            p[leftKey] = (set(p[leftKey]) - left).joinToString(",")
            p[ownerKey] = userId
            if (p[activeKey] == null || p[activeKey] !in (merged.keys + newer)) {
                merged.keys.firstOrNull()?.let { p[activeKey] = it } ?: p.remove(activeKey)
            }
        }
    }

    /** Signing out removes the account's plans from this device. */
    suspend fun clear() {
        context.plansStore.edit { it.clear() }
    }
}
