package com.tamilscripture.core.data.plans

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamilscripture.core.model.PlanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.plansStore: DataStore<Preferences> by preferencesDataStore("plans")

@Serializable
private data class StoredProgress(val plan: String, val start: String, val done: List<String>)

/**
 * Reading-plan progress, kept on the device (signed-out behaviour of the website, A-8.1).
 * Sync with `plan_progress` arrives with accounts (roadmap M6).
 */
class PlanRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val progressKey = stringPreferencesKey("progress")
    private val activeKey = stringPreferencesKey("active")

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
        }
    }

    suspend fun leave(planId: String) {
        context.plansStore.edit { p ->
            val m = decode(p[progressKey]) - planId
            p[progressKey] = encode(m)
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
        }
    }
}
