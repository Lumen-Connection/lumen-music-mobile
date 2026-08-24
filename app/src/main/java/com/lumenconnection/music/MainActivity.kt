package com.lumenconnection.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lumenconnection.music.player.PlayerController
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

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent { ThemedApp() }
    }

    /**
     * A partir do Android 13 a notificação de mídia precisa de permissão. Sem
     * ela a reprodução funciona, mas some da tela de bloqueio — daí o pedido no
     * primeiro uso, sem bloquear nada se for negada.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStop() {
        super.onStop()
        // Grava a posição ao sair de vista; o autosave de 30 s cobre o resto,
        // como o timer do desktop.
        PlayerController.persistState()
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
