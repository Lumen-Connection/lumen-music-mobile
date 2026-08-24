package com.lumenconnection.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Port de `src/design/tokens.cpp` (struct Metrics). Os números são os mesmos do
 * desktop; a única leitura diferente é `narrowBreakpoint`, que lá é largura de
 * janela em px e aqui é o breakpoint em dp para o layout responsivo.
 */
@Immutable
data class LumenDimens(
    val spacing: Dp,
    val spacingSm: Dp,
    val spacingLg: Dp,
    val windowMargin: Dp,
    val btnPadH: Dp,
    val btnPadV: Dp,
    val radiusCard: Dp,
    val radiusWidget: Dp,
    val rowHeight: Dp,
    val navItemHeight: Dp,
    val narrowBreakpoint: Dp,
    val borderWidth: Dp,
)

/**
 * Port do struct Motion. `d()` é o ponto único de estrangulamento das animações:
 * devolve 0 quando reduce-motion está ligado, como no desktop.
 */
@Immutable
data class LumenMotion(
    val fast: Int = 120,
    val normal: Int = 160,
    val slow: Int = 200,
    val reduced: Boolean = false,
) {
    fun d(ms: Int): Int = if (reduced) 0 else ms
    fun shouldAnimate(): Boolean = !reduced
}

/** Port do struct Typography. O desktop usa Segoe UI; aqui vale a fonte do sistema. */
@Immutable
data class LumenTypeScale(
    val displaySize: TextUnit,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val bodySmSize: TextUnit,
    val microSize: TextUnit,
    val monoSize: TextUnit,
    val displayWeight: FontWeight = FontWeight.Black,
    val titleWeight: FontWeight = FontWeight.Black,
    val bodyWeight: FontWeight = FontWeight.Medium,
    val microWeight: FontWeight = FontWeight.SemiBold,
)

@Immutable
data class LumenTokens(
    val paletteId: String,
    val mode: LumenMode,
    val density: LumenDensity,
    val color: LumenColors,
    val dimens: LumenDimens,
    val type: LumenTypeScale,
    val motion: LumenMotion,
)

private fun buildDimens(density: LumenDensity, mode: LumenMode): LumenDimens {
    val compact = density == LumenDensity.Compact
    return LumenDimens(
        spacing = if (compact) 7.dp else 10.dp,
        spacingSm = if (compact) 4.dp else 6.dp,
        spacingLg = if (compact) 12.dp else 18.dp,
        windowMargin = if (compact) 12.dp else 18.dp,
        btnPadH = if (compact) 10.dp else 14.dp,
        btnPadV = if (compact) 5.dp else 8.dp,
        radiusCard = 10.dp,
        radiusWidget = 8.dp,
        // Título + artista com um respiro modesto; linhas ficam compactas entre faixas.
        rowHeight = if (compact) 44.dp else 54.dp,
        navItemHeight = if (compact) 38.dp else 46.dp,
        narrowBreakpoint = 470.dp,
        borderWidth = if (mode == LumenMode.HighContrast) 2.dp else 1.dp,
    )
}

private fun buildTypeScale(density: LumenDensity): LumenTypeScale {
    val compact = density == LumenDensity.Compact
    val titleSz = if (compact) 22 else 28
    val bodySz = if (compact) 12 else 14
    val smSz = if (compact) 11 else 12
    val microSz = if (compact) 9 else 10
    return LumenTypeScale(
        displaySize = (titleSz + 4).sp,
        titleSize = titleSz.sp,
        bodySize = bodySz.sp,
        bodySmSize = smSz.sp,
        microSize = microSz.sp,
        monoSize = (if (compact) 10 else 11).sp,
    )
}

/**
 * Deriva o alto-contraste a partir de uma base clara ou escura, forçando
 * superfícies puras e endurecendo texto/borda/accent para AA. Port de
 * `deriveHighContrast()` do desktop.
 */
fun deriveHighContrast(base: LumenColors): LumenColors {
    val dark = relativeLuminance(base.app) < 0.5
    val app = if (dark) Color.Black else Color.White
    val text = if (dark) Color.White else Color.Black
    val accent = ensureContrast(base.accent, app, 4.5)
    return base.copy(
        app = app,
        sidebar = app,
        card = app,
        input = app,
        cardHover = if (dark) Color(0xFF303030) else Color(0xFFD2D2D2),
        overlay = if (dark) Color(0xDC000000) else Color(0xB4000000),
        vinyl = if (dark) Color.Black else Color.White,
        text = text,
        border = text,
        muted = ensureContrast(base.muted, app, 4.5),
        faint = ensureContrast(base.faint, app, 4.5),
        accent = accent,
        danger = ensureContrast(base.danger, app, 4.5),
        onAccent = ensureContrast(if (dark) Color.White else Color.Black, accent, 4.5),
        selectionBar = accent,
        accentDim = accent,
    )
}

/**
 * Monta o conjunto de tokens para uma combinação paleta × modo × densidade.
 *
 * @param hcFromLight quando o modo é HighContrast, deriva da base clara (true) ou
 *   escura (false) — o modo em que o usuário estava antes de ligar o HC.
 */
fun buildTokens(
    paletteId: String,
    mode: LumenMode,
    density: LumenDensity,
    hcFromLight: Boolean = false,
): LumenTokens {
    val pal = paletteById(paletteId)
    val color: LumenColors = when (mode) {
        // O endurecimento AA abaixo é pulado de propósito: deriveHighContrast já
        // força superfícies puras e texto/borda/accent em AA.
        LumenMode.HighContrast -> deriveHighContrast(if (hcFromLight) pal.light else pal.dark)

        LumenMode.Light -> {
            val b = pal.light
            var border = ensureContrast(b.border, b.card, 2.0)
            if (contrastRatio(border, b.card) < 2.0) border = darker(border, 125)
            var cardHover = b.cardHover
            if (contrastRatio(cardHover, b.card) < 1.15) cardHover = darker(b.card, 108)
            val accent = ensureContrast(b.accent, b.card, 3.0)
            b.copy(
                text = ensureContrast(b.text, b.card, 4.5),
                muted = ensureContrast(b.muted, b.card, 4.5),
                faint = ensureContrast(b.faint, b.card, 3.0),
                accent = accent,
                onAccent = ensureContrast(b.onAccent, accent, 4.5),
                border = border,
                cardHover = cardHover,
            )
        }

        LumenMode.Dark -> pal.dark
    }

    return LumenTokens(
        paletteId = pal.id,
        mode = mode,
        density = density,
        color = color,
        dimens = buildDimens(density, mode),
        type = buildTypeScale(density),
        motion = LumenMotion(),
    )
}
