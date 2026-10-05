package com.tamilscripture.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import com.tamilscripture.core.data.settings.Appearance
import com.tamilscripture.core.data.settings.Typeface
import com.tamilscripture.core.designsystem.theme.ScriptureFace
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class MainActivity : ComponentActivity() {
    /** Where this window starts; a reader window (FF-9) opens on its passage instead. */
    protected open fun startStack(): List<NavKey> = listOf(HomeRoute)


    private var pendingLink by mutableStateOf<NavKey?>(null)
    private var pendingUri by mutableStateOf<Uri?>(null)
    private var pendingId: String? = null

    /** The interface language, for the shortcut helper, which asks outside composition. */
    private var uiLang = UiLang.Tamil

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val services = (application as TsApplication).services
        // One DataStore read before the first frame so the theme and language never flash (NF-2).
        val initial = runBlocking { services.graph.settings.settings.first() }
        // Every link carries the id LinkActivity gave it; once opened, that id is remembered.
        // So a replay (rotation, a restore after the process was killed, a restart after a
        // force-stop) is skipped, while a new link is opened even when it is what restores the
        // activity after process death.
        val links = getSharedPreferences("links", MODE_PRIVATE)
        val id = intent?.getStringExtra(LINK_ID)
        if (id != null && id != links.getString("handled", null)) {
            intent?.data?.let { pendingUri = it; pendingId = id }
        } else if (id == null && savedInstanceState == null) {
            intent?.data?.let { pendingUri = replayed(links) }
        }
        val start = startStack()

        setContent {
            val settings by services.graph.settings.settings.collectAsStateWithLifecycle(initial)
            uiLang = settings.uiLang
            val manifest by services.graph.content.manifest.collectAsStateWithLifecycle()
            // Website links wait for the manifest (book slugs), which never blocks the main thread.
            val ready = manifest != null
            LaunchedEffect(pendingUri, ready) {
                val uri = pendingUri
                // A sign-in coming back (M6): the code is exchanged here, then the account opens.
                if (uri != null && isSignIn(uri)) {
                    pendingUri = null
                    pendingId?.let { links.edit().putString("handled", it).apply() }
                    finishSignIn(uri)
                    return@LaunchedEffect
                }
                val m = manifest
                if (uri != null && m != null) {
                    val link = parseDeepLink(uri, m, settings.version)
                    // Not awaited: a manifest refresh must not cancel the navigation half-way.
                    link?.compare?.let { code -> lifecycleScope.launch { services.graph.settings.update { it.copy(compare = code) } } }
                    pendingLink = link?.route
                    pendingId?.let { links.edit().putString("handled", it).putString("last", uri.toString()).putLong("lastAt", System.currentTimeMillis()).apply() }
                    pendingUri = null
                }
            }
            val mode = when (settings.appearance) {
                Appearance.System -> ThemeMode.System
                Appearance.Dark -> ThemeMode.Dark
                Appearance.Light -> ThemeMode.Light
            }
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            TsTheme(mode, settings.typeface.toFace()) {
                CompositionLocalProvider(LocalAppServices provides services, LocalUiLang provides settings.uiLang) {
                    AppShell(start = start, pending = pendingLink, onPendingHandled = { pendingLink = null })
                }
            }
        }
    }

    /** The system keyboard-shortcut helper (Meta + /) lists the reader's keys (FF-5, M2-5). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        val ta = uiLang == UiLang.Tamil
        fun t(tamil: String, english: String) = if (ta) tamil else english
        fun k(label: String, code: Int, mods: Int = 0) = KeyboardShortcutInfo(label, code, mods)
        data += KeyboardShortcutGroup(
            t("வாசிப்பு", "Reading"),
            listOf(
                k(t("அடுத்த வசனம்", "Next verse"), KeyEvent.KEYCODE_J),
                k(t("முந்தைய வசனம்", "Previous verse"), KeyEvent.KEYCODE_K),
                k(t("அடுத்த அதிகாரம்", "Next chapter"), KeyEvent.KEYCODE_DPAD_RIGHT),
                k(t("முந்தைய அதிகாரம்", "Previous chapter"), KeyEvent.KEYCODE_DPAD_LEFT),
                k(t("ஒலி: இயக்கு / நிறுத்து", "Audio: play / pause"), KeyEvent.KEYCODE_SPACE),
                k(t("விளக்கவுரை", "Commentary on / off"), KeyEvent.KEYCODE_C),
                k(t("விளக்கவுரை ஆதாரம் 1–5", "Commentary source 1–5"), KeyEvent.KEYCODE_1),
                k(t("மொழிபெயர்ப்பு", "Translation"), KeyEvent.KEYCODE_V),
                k(t("தேடல்", "Search"), KeyEvent.KEYCODE_SLASH),
                k(t("தேர்வை நீக்கு", "Clear the selection"), KeyEvent.KEYCODE_ESCAPE),
                k(t("புதிய சாளரம்", "New window"), KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                k(t("காட்சிப்படுத்து", "Present"), KeyEvent.KEYCODE_P),
            ),
        )
    }

    private fun isSignIn(uri: Uri) =
        (uri.scheme == "tamilscripture" && uri.host == "auth") ||
            (uri.path?.trimEnd('/') == "/auth/callback" && uri.getQueryParameter("app") == "1")

    private suspend fun finishSignIn(uri: Uri) {
        val graph = (application as TsApplication).services.graph
        val tamil = uiLang == UiLang.Tamil
        val code = uri.getQueryParameter("code")
        val ok = code != null && runCatching { graph.account.exchange(code) }.isSuccess
        Toast.makeText(
            this,
            if (ok) (if (tamil) "உள்நுழைந்தீர்கள்" else "Signed in") else (if (tamil) "உள்நுழைய முடியவில்லை. மீண்டும் முயலுங்கள்." else "Could not sign in. Please try again."),
            Toast.LENGTH_LONG,
        ).show()
        pendingLink = AccountRoute
    }

    /**
     * A link without LinkActivity's id is the system replaying an old intent: after the app is
     * updated, System UI relaunches the task with its first link (no extras, clearing the task)
     * just after a new link opened. The reader stays on the link opened last if that was
     * moments ago; otherwise the replay is ignored rather than opening a stale chapter.
     */
    private fun replayed(links: android.content.SharedPreferences): Uri? {
        val last = links.getString("last", null) ?: return null
        val at = links.getLong("lastAt", 0)
        return if (System.currentTimeMillis() - at < 10_000) Uri.parse(last) else null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = intent.getStringExtra(LINK_ID)
        if (id == null) {
            intent.data?.let { replayed(getSharedPreferences("links", MODE_PRIVATE))?.let { u -> pendingUri = u; pendingId = null } }
            return
        }
        intent.data?.let { pendingUri = it; pendingId = id }
    }

    companion object {
        /** Set by [LinkActivity]: one id per time a link is opened. */
        const val LINK_ID = "link_id"
    }
}

private fun Typeface.toFace(): ScriptureFace = when (this) {
    Typeface.MuktaMalar -> ScriptureFace.MuktaMalar
    Typeface.NotoSansTamil -> ScriptureFace.NotoSansTamil
    Typeface.NotoSerifTamil -> ScriptureFace.NotoSerifTamil
}
