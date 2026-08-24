package com.lumenconnection.music.config

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Aplica o idioma escolhido antes de a Activity iniciar.
 *
 * O DataStore é assíncrono, mas `attachBaseContext` precisa do idioma de forma
 * síncrona — por isso a escolha é espelhada num SharedPreferences minúsculo. O
 * DataStore continua sendo a fonte que a UI observa; este espelho só existe para
 * a leitura antecipada.
 *
 * O desktop troca de idioma ao vivo (bindText/bindPlaceholder do `i18n.cpp`); no
 * Android o equivalente idiomático é `Activity.recreate()`, que é instantâneo.
 */
object LocaleOverride {
    private const val PREFS = "lumen_locale"
    private const val KEY = "language"

    fun persist(context: Context, mode: LanguageMode) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }

    fun current(context: Context): LanguageMode {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, LanguageMode.System.name)
        return runCatching { LanguageMode.valueOf(raw ?: LanguageMode.System.name) }
            .getOrDefault(LanguageMode.System)
    }

    fun wrap(base: Context): Context {
        val locale = when (current(base)) {
            LanguageMode.System -> return base
            LanguageMode.PtBr -> Locale("pt", "BR")
            LanguageMode.En -> Locale("en")
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
