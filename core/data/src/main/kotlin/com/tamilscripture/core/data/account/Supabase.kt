package com.tamilscripture.core.data.account

import com.tamilscripture.core.data.net.HttpException
import com.tamilscripture.core.data.net.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The few Supabase REST calls the app makes (auth and PostgREST under RLS), on plain
 * HttpURLConnection like the rest of the app (NF-1): no SDK is shipped.
 */
class Supabase {
    /** Set by tests to prove the read path makes no calls (NF-6). */
    @Volatile var guard: ((String) -> Unit)? = null

    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        prefer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = SupabaseConfig.URL + path
        guard?.invoke(url)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${token ?: SupabaseConfig.ANON_KEY}")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "TamilScriptureAndroid")
            if (prefer != null) conn.setRequestProperty("Prefer", prefer)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, "HTTP $code for $path: ${text.take(300)}")
            text
        } finally {
            conn.disconnect()
        }
    }
}
