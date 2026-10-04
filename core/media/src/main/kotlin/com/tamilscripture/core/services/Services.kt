package com.tamilscripture.core.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.tamilscripture.core.data.AppGraph
import com.tamilscripture.core.media.AudioController
import com.tamilscripture.core.model.UiLang

/** Everything a screen may use, provided once by MainActivity. */
class AppServices(val graph: AppGraph, val audio: AudioController)

val LocalAppServices = staticCompositionLocalOf<AppServices> { error("AppServices not provided") }

/** Interface language for in-code Tamil/English strings (same pattern as the website). */
val LocalUiLang = staticCompositionLocalOf { UiLang.Tamil }

/** Picks the Tamil or English string for the current interface language. */
@Composable
@ReadOnlyComposable
fun tr(ta: String, en: String): String = if (LocalUiLang.current == UiLang.Tamil) ta else en
