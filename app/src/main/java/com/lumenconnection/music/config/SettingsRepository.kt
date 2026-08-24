package com.lumenconnection.music.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumen_music_settings")

/** Modo de tema escolhido pelo usuário. `System` não existe no desktop — é o padrão do Android. */
enum class ThemeMode { System, Dark, Light, HighContrast }

enum class DensityMode { Comfortable, Compact }

enum class LanguageMode { System, PtBr, En }

/** Servidor desktop pareado (fase 6). Nulo enquanto não houver pareamento. */
data class PairedServer(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
)

/**
 * Equivalente do `QSettings` do desktop (`HKCU\Software\VinilPlayer\Vinil Player`).
 * Guarda preferências de aparência, idioma e o servidor de sync pareado.
 *
 * Volume/mudo NÃO ficam aqui: como no desktop desde a 2.0.0, o estado de
 * reprodução é do banco (`playback_state`), que é a fonte da verdade.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val palette = stringPreferencesKey("palette")
        val mode = stringPreferencesKey("mode")
        val density = stringPreferencesKey("density")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val language = stringPreferencesKey("language")
        // Lembra o modo anterior ao HC para derivar o alto-contraste do lado certo,
        // como o parâmetro hcFromLight do desktop.
        val hcFromLight = booleanPreferencesKey("hc_from_light")

        val syncServerId = stringPreferencesKey("sync_server_id")
        val syncServerName = stringPreferencesKey("sync_server_name")
        val syncHost = stringPreferencesKey("sync_host")
        val syncPort = intPreferencesKey("sync_port")
        val syncToken = stringPreferencesKey("sync_token")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val syncAllPlaylists = booleanPreferencesKey("sync_all_playlists")
        val deviceId = stringPreferencesKey("device_id")
    }

    private val prefs: Flow<Preferences> = context.dataStore.data

    val palette: Flow<String> = prefs.map { it[Keys.palette] ?: "lumen" }
    val mode: Flow<ThemeMode> = prefs.map { p ->
        runCatching { ThemeMode.valueOf(p[Keys.mode] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }
    val density: Flow<DensityMode> = prefs.map { p ->
        runCatching { DensityMode.valueOf(p[Keys.density] ?: DensityMode.Comfortable.name) }
            .getOrDefault(DensityMode.Comfortable)
    }
    val reduceMotion: Flow<Boolean> = prefs.map { it[Keys.reduceMotion] ?: false }
    val hcFromLight: Flow<Boolean> = prefs.map { it[Keys.hcFromLight] ?: false }
    val language: Flow<LanguageMode> = prefs.map { p ->
        runCatching { LanguageMode.valueOf(p[Keys.language] ?: LanguageMode.System.name) }
            .getOrDefault(LanguageMode.System)
    }

    val pairedServer: Flow<PairedServer?> = prefs.map { p ->
        val id = p[Keys.syncServerId]
        val token = p[Keys.syncToken]
        val host = p[Keys.syncHost]
        if (id.isNullOrBlank() || token.isNullOrBlank() || host.isNullOrBlank()) null
        else PairedServer(
            serverId = id,
            name = p[Keys.syncServerName] ?: host,
            host = host,
            port = p[Keys.syncPort] ?: 45150,
            token = token,
        )
    }
    val lastSyncAt: Flow<Long> = prefs.map { it[Keys.lastSyncAt] ?: 0L }
    val syncAllPlaylists: Flow<Boolean> = prefs.map { it[Keys.syncAllPlaylists] ?: false }

    suspend fun setPalette(id: String) = edit { it[Keys.palette] = id }
    suspend fun setMode(m: ThemeMode) = edit { it[Keys.mode] = m.name }
    suspend fun setDensity(d: DensityMode) = edit { it[Keys.density] = d.name }
    suspend fun setReduceMotion(v: Boolean) = edit { it[Keys.reduceMotion] = v }
    suspend fun setHcFromLight(v: Boolean) = edit { it[Keys.hcFromLight] = v }
    suspend fun setLanguage(l: LanguageMode) {
        // Espelha no SharedPreferences para o attachBaseContext conseguir ler o
        // idioma de forma síncrona antes de a Activity subir.
        LocaleOverride.persist(context, l)
        edit { it[Keys.language] = l.name }
    }
    suspend fun setSyncAllPlaylists(v: Boolean) = edit { it[Keys.syncAllPlaylists] = v }
    suspend fun setLastSyncAt(ts: Long) = edit { it[Keys.lastSyncAt] = ts }

    suspend fun savePairedServer(server: PairedServer) = edit {
        it[Keys.syncServerId] = server.serverId
        it[Keys.syncServerName] = server.name
        it[Keys.syncHost] = server.host
        it[Keys.syncPort] = server.port
        it[Keys.syncToken] = server.token
    }

    /** Chamado quando o desktop devolve 401 (token revogado) ou o usuário desfaz o pareamento. */
    suspend fun clearPairedServer() = edit {
        it.remove(Keys.syncServerId)
        it.remove(Keys.syncServerName)
        it.remove(Keys.syncHost)
        it.remove(Keys.syncPort)
        it.remove(Keys.syncToken)
        it.remove(Keys.lastSyncAt)
    }

    /** Atualiza só o endereço: o IP do desktop muda, mas o pareamento continua valendo. */
    suspend fun updateServerAddress(host: String, port: Int) = edit {
        it[Keys.syncHost] = host
        it[Keys.syncPort] = port
    }

    /** Identidade estável deste aparelho perante o desktop; criada na primeira leitura. */
    suspend fun deviceId(): String {
        val existing = prefs.first()[Keys.deviceId]
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        edit { it[Keys.deviceId] = generated }
        return generated
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
