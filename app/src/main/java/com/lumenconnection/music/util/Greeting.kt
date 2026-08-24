package com.lumenconnection.music.util

import androidx.annotation.StringRes
import com.lumenconnection.music.R

/**
 * Port de `src/widgets/greeting.h` do desktop: 5 faixas horárias × 3 variantes.
 *
 * Faixas: madrugada 1–4, manhã 5–11, tarde 12–17, noite 18–21,
 * noite alta 22–0 (22, 23 e 0).
 *
 * A parte pura (qual faixa, qual variante) fica aqui; o texto vem dos recursos,
 * então a tradução acompanha o idioma do aparelho — no desktop o chamador é que
 * traduzia.
 */
object Greeting {

    private val BANDS: List<List<Int>> = listOf(
        listOf(R.string.greeting_late_1, R.string.greeting_late_2, R.string.greeting_late_3),
        listOf(R.string.greeting_morning_1, R.string.greeting_morning_2, R.string.greeting_morning_3),
        listOf(R.string.greeting_afternoon_1, R.string.greeting_afternoon_2, R.string.greeting_afternoon_3),
        listOf(R.string.greeting_evening_1, R.string.greeting_evening_2, R.string.greeting_evening_3),
        listOf(R.string.greeting_night_1, R.string.greeting_night_2, R.string.greeting_night_3),
    )

    fun band(hour: Int): Int {
        val h = ((hour % 24) + 24) % 24
        return when {
            h in 1..4 -> 0
            h in 5..11 -> 1
            h in 12..17 -> 2
            h in 18..21 -> 3
            else -> 4 // 22, 23, 0
        }
    }

    fun variantCount(hour: Int): Int = BANDS[band(hour)].size

    @StringRes
    fun resFor(hour: Int, variantIndex: Int): Int {
        val variants = BANDS[band(hour)]
        val n = variants.size
        return variants[((variantIndex % n) + n) % n]
    }
}
