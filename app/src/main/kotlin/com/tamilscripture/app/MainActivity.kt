package com.tamilscripture.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.tamilscripture.core.data.settings.Appearance
import com.tamilscripture.core.data.settings.Typeface
import com.tamilscripture.core.designsystem.theme.ScriptureFace
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private var pendingLink by mutableStateOf<Passage?>(null)
    private var pendingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val services = (application as TsApplication).services
        // One DataStore read before the first frame so the theme and language never flash (NF-2).
        val initial = runBlocking { services.graph.settings.settings.first() }
        if (savedInstanceState == null) pendingUri = intent?.data

        setContent {
            val settings by services.graph.settings.settings.collectAsStateWithLifecycle(initial)
            val manifest by services.graph.content.manifest.collectAsStateWithLifecycle()
            // Website links wait for the manifest (book slugs), which never blocks the main thread.
            LaunchedEffect(pendingUri, manifest) {
                val uri = pendingUri
                val m = manifest
                if (uri != null && m != null) {
                    pendingLink = parseDeepLink(uri, m.books, settings.version)
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
                    AppShell(start = listOf(HomeRoute), pending = pendingLink, onPendingHandled = { pendingLink = null })
                }
            }
        }
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
