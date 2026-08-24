package com.lumenconnection.music

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.config.DensityMode
import com.lumenconnection.music.config.LocaleOverride
import com.lumenconnection.music.config.ThemeMode
import com.lumenconnection.music.ui.AppRoot
import com.lumenconnection.music.ui.theme.LumenDensity
import com.lumenconnection.music.ui.theme.LumenMode
import com.lumenconnection.music.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleOverride.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ThemedApp() }
    }
}

/**
 * Liga as preferências de aparência ao tema. Como no desktop, trocar paleta,
 * modo, densidade ou reduce-motion vale na hora, sem reiniciar.
 */
@Composable
private fun ThemedApp() {
    val settings = Graph.settings
    val palette by settings.palette.collectAsStateWithLifecycle(initialValue = "lumen")
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val density by settings.density.collectAsStateWithLifecycle(initialValue = DensityMode.Comfortable)
    val reduceMotion by settings.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val hcFromLight by settings.hcFromLight.collectAsStateWithLifecycle(initialValue = false)

    LumenTheme(
        paletteId = palette,
        mode = when (mode) {
            ThemeMode.System -> null
            ThemeMode.Dark -> LumenMode.Dark
            ThemeMode.Light -> LumenMode.Light
            ThemeMode.HighContrast -> LumenMode.HighContrast
        },
        density = when (density) {
            DensityMode.Comfortable -> LumenDensity.Comfortable
            DensityMode.Compact -> LumenDensity.Compact
        },
        reduceMotion = reduceMotion,
        hcFromLight = hcFromLight,
    ) {
        AppRoot()
    }
}
