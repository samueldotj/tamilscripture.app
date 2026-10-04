package com.tamilscripture.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** "தோற்றம்" in the study settings sheet: இருள் / ஒளி / தானியங்கி. Default follows the system. */
enum class ThemeMode { System, Dark, Light }

@Composable
fun TsTheme(
    mode: ThemeMode = ThemeMode.System,
    scriptureFace: ScriptureFace = ScriptureFace.MuktaMalar,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val colors = if (dark) DarkTsColors else LightTsColors
    val type = remember(scriptureFace) { TsType(scripture = scriptureFace.family) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.amber, background = colors.bg, onBackground = colors.ink,
            surface = colors.bg, onSurface = colors.ink, surfaceVariant = colors.surface,
            onSurfaceVariant = colors.ink2, surfaceContainer = colors.surface2,
            surfaceContainerHigh = colors.surface, surfaceContainerLow = colors.surface2,
            outline = colors.line2, outlineVariant = colors.line, scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.amber, background = colors.bg, onBackground = colors.ink,
            surface = colors.bg, onSurface = colors.ink, surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2, surfaceContainer = colors.surface2,
            surfaceContainerHigh = colors.surface, surfaceContainerLow = colors.surface2,
            outline = colors.line2, outlineVariant = colors.line, scrim = colors.scrim,
        )
    }
    CompositionLocalProvider(LocalTsColors provides colors, LocalTsType provides type) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = type.body, bodyMedium = type.body, labelLarge = type.label,
                labelMedium = type.labelSmall, titleMedium = type.rowTitle,
            ),
            content = content,
        )
    }
}

object Ts {
    val colors: TsColors
        @Composable @ReadOnlyComposable get() = LocalTsColors.current
    val type: TsType
        @Composable @ReadOnlyComposable get() = LocalTsType.current
}
