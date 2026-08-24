package com.lumenconnection.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

/**
 * Tema do Lumen Music Mobile. Os tokens vêm do port do design system do desktop
 * (ver [buildTokens]); o esquema Material 3 é derivado deles apenas para que os
 * componentes prontos do M3 herdem as cores certas — a UI própria lê `LumenTheme.colors`.
 */
val LocalLumenTokens = staticCompositionLocalOf {
    buildTokens("lumen", LumenMode.Dark, LumenDensity.Comfortable)
}

object LumenTheme {
    val tokens: LumenTokens
        @Composable get() = LocalLumenTokens.current
    val colors: LumenColors
        @Composable get() = LocalLumenTokens.current.color
    val dimens: LumenDimens
        @Composable get() = LocalLumenTokens.current.dimens
    val type: LumenTypeScale
        @Composable get() = LocalLumenTokens.current.type
    val motion: LumenMotion
        @Composable get() = LocalLumenTokens.current.motion
}

/** Estilos de texto derivados da escala do desktop, para uso direto nos composables. */
object LumenText {
    val display: TextStyle
        @Composable get() = LumenTheme.type.let {
            TextStyle(fontSize = it.displaySize, fontWeight = it.displayWeight, color = LumenTheme.colors.text)
        }
    val title: TextStyle
        @Composable get() = LumenTheme.type.let {
            TextStyle(fontSize = it.titleSize, fontWeight = it.titleWeight, color = LumenTheme.colors.text)
        }
    val body: TextStyle
        @Composable get() = LumenTheme.type.let {
            TextStyle(fontSize = it.bodySize, fontWeight = it.bodyWeight, color = LumenTheme.colors.text)
        }
    val bodySm: TextStyle
        @Composable get() = LumenTheme.type.let {
            TextStyle(fontSize = it.bodySmSize, fontWeight = it.bodyWeight, color = LumenTheme.colors.muted)
        }
    val micro: TextStyle
        @Composable get() = LumenTheme.type.let {
            TextStyle(fontSize = it.microSize, fontWeight = it.microWeight, color = LumenTheme.colors.faint)
        }
}

private fun materialSchemeFrom(t: LumenTokens) =
    if (t.mode == LumenMode.Light) {
        lightColorScheme(
            primary = t.color.accent,
            onPrimary = t.color.onAccent,
            secondary = t.color.accentDim,
            onSecondary = t.color.onAccent,
            background = t.color.app,
            onBackground = t.color.text,
            surface = t.color.card,
            onSurface = t.color.text,
            surfaceVariant = t.color.cardHover,
            onSurfaceVariant = t.color.muted,
            outline = t.color.border,
            error = t.color.danger,
            onError = t.color.onAccent,
        )
    } else {
        darkColorScheme(
            primary = t.color.accent,
            onPrimary = t.color.onAccent,
            secondary = t.color.accentDim,
            onSecondary = t.color.onAccent,
            background = t.color.app,
            onBackground = t.color.text,
            surface = t.color.card,
            onSurface = t.color.text,
            surfaceVariant = t.color.cardHover,
            onSurfaceVariant = t.color.muted,
            outline = t.color.border,
            error = t.color.danger,
            onError = t.color.onAccent,
        )
    }

/**
 * @param mode `null` significa "seguir o sistema" — o desktop não tem esse estado
 *   (lá o usuário escolhe Dark/Light/HC explicitamente), mas no Android seguir o
 *   tema do aparelho é o padrão esperado.
 */
@Composable
fun LumenTheme(
    paletteId: String = "lumen",
    mode: LumenMode? = null,
    density: LumenDensity = LumenDensity.Comfortable,
    reduceMotion: Boolean = false,
    hcFromLight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveMode = mode ?: if (systemDark) LumenMode.Dark else LumenMode.Light
    val tokens = buildTokens(paletteId, effectiveMode, density, hcFromLight)
        .let { it.copy(motion = it.motion.copy(reduced = reduceMotion)) }

    // Os ícones das barras de sistema seguem o tema escolhido no app, não o do
    // aparelho: com tema claro sobre sistema escuro eles sumiriam no fundo.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val lightSurface = relativeLuminance(tokens.color.app) > 0.5
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
    }

    CompositionLocalProvider(LocalLumenTokens provides tokens) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(tokens),
            content = content,
        )
    }
}
