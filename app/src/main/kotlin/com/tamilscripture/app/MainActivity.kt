package com.tamilscripture.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
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

    /** The interface language, for the shortcut helper, which asks outside composition. */
    private var uiLang = UiLang.Tamil

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val services = (application as TsApplication).services
        // One DataStore read before the first frame so the theme and language never flash (NF-2).
        val initial = runBlocking { services.graph.settings.settings.first() }
        // A recreated activity (rotation, or a restore after the process was killed) carries an
        // intent that was already opened; new links arrive through onNewIntent. After a
        // force-stop or reboot the system restarts the task with its first intent and no saved
        // state, so the last link opened is remembered on disk and not opened a second time.
        val links = getSharedPreferences("links", MODE_PRIVATE)
        if (savedInstanceState == null) intent?.data?.takeIf { it.toString() != links.getString("handled", null) }?.let { pendingUri = it }
        val start = startStack()

        setContent {
            val settings by services.graph.settings.settings.collectAsStateWithLifecycle(initial)
            uiLang = settings.uiLang
            val manifest by services.graph.content.manifest.collectAsStateWithLifecycle()
            // Website links wait for the manifest (book slugs), which never blocks the main thread.
            val ready = manifest != null
            LaunchedEffect(pendingUri, ready) {
                val uri = pendingUri
                val m = manifest
                if (uri != null && m != null) {
                    val link = parseDeepLink(uri, m, settings.version)
                    // Not awaited: a manifest refresh must not cancel the navigation half-way.
                    link?.compare?.let { code -> lifecycleScope.launch { services.graph.settings.update { it.copy(compare = code) } } }
                    pendingLink = link?.route
                    links.edit().putString("handled", uri.toString()).apply()
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
            ),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { pendingUri = it }
    }
}

private fun Typeface.toFace(): ScriptureFace = when (this) {
    Typeface.MuktaMalar -> ScriptureFace.MuktaMalar
    Typeface.NotoSansTamil -> ScriptureFace.NotoSansTamil
    Typeface.NotoSerifTamil -> ScriptureFace.NotoSerifTamil
}
