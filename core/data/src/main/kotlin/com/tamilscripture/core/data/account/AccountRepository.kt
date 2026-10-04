package com.tamilscripture.core.data.account

import android.util.Base64
import com.tamilscripture.core.data.net.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** The signed-in account (A-7, one account with the website). */
@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    /** Seconds since the epoch. */
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("user_id") val userId: String,
    val email: String? = null,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: TokenUser,
)

@Serializable
private data class TokenUser(val id: String, val email: String? = null)

/**
 * Sign-in through the website's own Supabase project (design §13.1), so the app and the
 * website share one account. PKCE: the app keeps the verifier; the sign-in (an email link
 * or Google, both in the browser) returns to the website's /auth/callback?app=1, which hands
 * the code to tamilscripture://auth, and the app exchanges it here. No OAuth client of the
 * app's own, no App Links needed.
 */
class AccountRepository(private val dir: File, private val api: Supabase, private val json: Json) {
    private val sessionFile = File(dir, "session.json")
    private val verifierFile = File(dir, "pkce")
    private val lock = Mutex()
    private val state = MutableStateFlow(read())

    /** Null when signed out. */
    val session: StateFlow<Session?> = state

    private fun read(): Session? = runCatching { json.decodeFromString<Session>(sessionFile.readText()) }.getOrNull()

    private fun write(s: Session?) {
        dir.mkdirs()
        if (s == null) sessionFile.delete() else File(dir, "session.tmp").apply { writeText(json.encodeToString(Session.serializer(), s)) }.renameTo(sessionFile.also { it.delete() })
        state.value = s
    }

    /** Where the website's callback hands the code back to the app. */
    private val redirect = "https://www.tamilscripture.com/auth/callback?app=1"

    private fun newChallenge(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        dir.mkdirs()
        // Kept on disk: the reader may leave for their mail app and the process may die.
        verifierFile.writeText(verifier)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Sends a sign-in link to [email]; it opens the website, which returns to the app. */
    suspend fun sendEmailLink(email: String) {
        val challenge = withContext(Dispatchers.IO) { newChallenge() }
        val body = buildJsonObject {
            put("email", email.trim())
            put("create_user", true)
            put("code_challenge", challenge)
            put("code_challenge_method", "s256")
        }
        api.request("POST", "/auth/v1/otp?redirect_to=" + URLEncoder.encode(redirect, "UTF-8"), body.toString())
    }

    /** The browser page that starts Google sign-in (the website's existing Google provider). */
    suspend fun googleUrl(): String {
        val challenge = withContext(Dispatchers.IO) { newChallenge() }
        return SupabaseConfig.URL + "/auth/v1/authorize?provider=google" +
            "&redirect_to=" + URLEncoder.encode(redirect, "UTF-8") +
            "&code_challenge=" + challenge + "&code_challenge_method=s256"
    }

    /** Completes a sign-in with the code from tamilscripture://auth. */
    suspend fun exchange(code: String): Session = lock.withLock {
        val verifier = withContext(Dispatchers.IO) { runCatching { verifierFile.readText() }.getOrNull() }
            ?: error("no sign-in in progress")
        val body = buildJsonObject {
            put("auth_code", code)
            put("code_verifier", verifier)
        }
        val res = json.decodeFromString<TokenResponse>(api.request("POST", "/auth/v1/token?grant_type=pkce", body.toString()))
        withContext(Dispatchers.IO) { verifierFile.delete() }
        val s = res.toSession()
        withContext(Dispatchers.IO) { write(s) }
        s
    }

    /** A valid access token, refreshed when it is within a minute of expiry; null when signed out. */
    suspend fun accessToken(): String? = lock.withLock {
        val s = state.value ?: return@withLock null
        if (s.expiresAt - System.currentTimeMillis() / 1000 > 60) return@withLock s.accessToken
        val body = buildJsonObject { put("refresh_token", s.refreshToken) }
        val text = try {
            api.request("POST", "/auth/v1/token?grant_type=refresh_token", body.toString())
        } catch (e: com.tamilscripture.core.data.net.HttpException) {
            // The session was ended elsewhere (signed out on the website, or expired): signed out here too.
            if (e.code in 400..401) withContext(Dispatchers.IO) { write(null) }
            throw e
        }
        val res = json.decodeFromString<TokenResponse>(text)
        val next = res.toSession()
        withContext(Dispatchers.IO) { write(next) }
        next.accessToken
    }

    /** Forgets the session on this device (the server session ends with it). */
    suspend fun signOut() {
        val token = state.value?.accessToken
        if (token != null) runCatching { api.request("POST", "/auth/v1/logout", "{}", token) }
        withContext(Dispatchers.IO) { write(null) }
    }

    private fun TokenResponse.toSession() = Session(
        accessToken, refreshToken,
        expiresAt ?: (System.currentTimeMillis() / 1000 + expiresIn),
        user.id, user.email,
    )
}
