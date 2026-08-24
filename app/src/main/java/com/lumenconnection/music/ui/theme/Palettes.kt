package com.lumenconnection.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Port 1:1 de `src/design/tokens.h` (struct Colors) e `src/design/palettes.cpp`
 * do Lumen Music desktop. Os valores hex são idênticos aos do desktop — qualquer
 * mudança aqui quebra a paridade visual e deve ser feita nos dois lados.
 */
@Immutable
data class LumenColors(
    val app: Color,
    val sidebar: Color,
    val card: Color,
    val cardHover: Color,
    val input: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val vinyl: Color,
    val overlay: Color,
    val selectionBar: Color,
    val accentDim: Color,
)

enum class LumenMode { Dark, Light, HighContrast }

enum class LumenDensity { Comfortable, Compact }

@Immutable
data class PaletteDef(
    val id: String,
    val label: String,
    val dark: LumenColors,
    val light: LumenColors,
)

private fun hex(v: Long): Color = Color(0xFF000000L or v)

private fun makeDark(
    app: Long, sidebar: Long, card: Long, cardHover: Long, input: Long,
    text: Long, muted: Long, faint: Long, border: Long,
    accent: Long, danger: Long, vinyl: Long,
): LumenColors {
    val accentC = hex(accent)
    return LumenColors(
        app = hex(app),
        sidebar = hex(sidebar),
        card = hex(card),
        cardHover = hex(cardHover),
        input = hex(input),
        text = hex(text),
        muted = hex(muted),
        faint = hex(faint),
        border = hex(border),
        accent = accentC,
        onAccent = pickOnAccent(accentC),
        danger = hex(danger),
        vinyl = hex(vinyl),
        overlay = Color(0xA0000000),
        selectionBar = accentC,
        accentDim = darker(accentC, 115),
    )
}

/**
 * Modo claro: bordas/texto secundário mais fortes, superfícies tingidas (não
 * monocromáticas) e scrim preto — um overlay branco desapareceria numa UI clara.
 */
private fun makeLight(
    app: Long, sidebar: Long, card: Long, cardHover: Long, input: Long,
    text: Long, muted: Long, faint: Long, border: Long,
    accent: Long, danger: Long, vinyl: Long,
): LumenColors {
    val accentC = hex(accent)
    return LumenColors(
        app = hex(app),
        sidebar = hex(sidebar),
        card = hex(card),
        cardHover = hex(cardHover),
        input = hex(input),
        text = hex(text),
        muted = hex(muted),
        faint = hex(faint),
        border = hex(border),
        accent = accentC,
        onAccent = pickOnAccent(accentC),
        danger = hex(danger),
        vinyl = hex(vinyl),
        overlay = Color(0x780F141E),
        selectionBar = accentC,
        accentDim = darker(accentC, 112),
    )
}

/**
 * As 6 paletas do desktop. Decisão registrada lá: só a Lumen adota o #ff5722 do
 * Lumen Stream; as outras mantêm seus próprios accents.
 */
val AllPalettes: List<PaletteDef> = listOf(
    PaletteDef(
        id = "lumen", label = "Lumen",
        dark = makeDark(
            0x0a0e12, 0x070a0d, 0x121821, 0x1c2530, 0x161d27,
            0xeef3f6, 0x93a1ad, 0x5a6670, 0x263240,
            0xff5722, 0xff4d4d, 0x050506,
        ),
        // Claro: papel frio + accent coral (não um laranja lavado sobre branco)
        light = makeLight(
            0xf0f4f7, 0xdce4ea, 0xffffff, 0xd0dce6, 0xffffff,
            0x0f1720, 0x3d4d5c, 0x5c6b78, 0x9aafbd,
            0xe64a19, 0xc62828, 0x1a1a1a,
        ),
    ),
    PaletteDef(
        id = "warm", label = "Vinil Quente",
        dark = makeDark(
            0x1a1712, 0x14110e, 0x2a2620, 0x342f28, 0x221f19,
            0xf0ece4, 0xb8b0a2, 0x7a7266, 0x3a352d,
            0xe8a44a, 0xd45d5d, 0x111111,
        ),
        light = makeLight(
            0xfaf6ef, 0xf0e6d6, 0xffffff, 0xebe0ce, 0xfffdf9,
            0x1f1810, 0x5c4f3e, 0x7a6b56, 0xcbbda8,
            0xc47a1a, 0xb71c1c, 0x1a1a1a,
        ),
    ),
    PaletteDef(
        id = "ocean", label = "Oceano",
        dark = makeDark(
            0x0d1520, 0x0a1018, 0x162338, 0x1d2e47, 0x111c2d,
            0xe4f0f8, 0xa0c0d8, 0x5a7a90, 0x1e3048,
            0x4aa8e8, 0xe85d5d, 0x080d14,
        ),
        light = makeLight(
            0xeef5fb, 0xdceaf4, 0xffffff, 0xcfe0ee, 0xffffff,
            0x0c1824, 0x35566c, 0x4f738c, 0xa8c0d4,
            0x0277bd, 0xc62828, 0x1a1a1a,
        ),
    ),
    PaletteDef(
        id = "forest", label = "Floresta",
        dark = makeDark(
            0x121a12, 0x0e140e, 0x1f281f, 0x283328, 0x182018,
            0xe8f0e8, 0xa8c0a8, 0x6a8a6a, 0x2a3a2a,
            0x6bcf7f, 0xd45d5d, 0x080f08,
        ),
        light = makeLight(
            0xeef6ee, 0xdceadc, 0xffffff, 0xcfe0cf, 0xffffff,
            0x101a10, 0x3a5a3a, 0x557555, 0xa8c4a8,
            0x2e7d32, 0xc62828, 0x1a1a1a,
        ),
    ),
    PaletteDef(
        id = "purple", label = "Roxo Noturno",
        dark = makeDark(
            0x15101a, 0x100c14, 0x261c30, 0x30233c, 0x1d1525,
            0xf0e8f8, 0xc0a8d8, 0x7a6090, 0x352545,
            0xc084fc, 0xf05d7a, 0x0d0810,
        ),
        light = makeLight(
            0xf6f0fa, 0xebe0f2, 0xffffff, 0xe0d2ec, 0xffffff,
            0x16101f, 0x4a3a62, 0x6a5888, 0xc4b3d6,
            0x7b2cbf, 0xc2185b, 0x1a1a1a,
        ),
    ),
    PaletteDef(
        id = "gray", label = "Cinza Moderno",
        dark = makeDark(
            0x141414, 0x0e0e0e, 0x242424, 0x2e2e2e, 0x1c1c1c,
            0xf5f5f5, 0xbdbdbd, 0x757575, 0x333333,
            0xe0e0e0, 0xef5350, 0x0a0a0a,
        ),
        // O cinza claro era monocromático (accent cinza sobre papel cinza). Um azul
        // nítido mantém os controles interativos legíveis e distintos.
        light = makeLight(
            0xf4f5f7, 0xe8eaee, 0xffffff, 0xdfe2e8, 0xffffff,
            0x12141a, 0x3d4450, 0x5c6570, 0xb8bec8,
            0x2563eb, 0xc62828, 0x1a1a1a,
        ),
    ),
)

fun paletteById(id: String): PaletteDef =
    AllPalettes.firstOrNull { it.id == id } ?: AllPalettes.first()

val paletteIds: List<String> get() = AllPalettes.map { it.id }
