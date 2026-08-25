package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.config.PairedServer
import com.lumenconnection.music.sync.DiscoveredServer
import com.lumenconnection.music.sync.DiscoveryClient
import com.lumenconnection.music.sync.SYNC_DEFAULT_PORT
import com.lumenconnection.music.sync.SyncApi
import com.lumenconnection.music.sync.SyncEngine
import com.lumenconnection.music.sync.SyncService
import com.lumenconnection.music.ui.AccentButton
import com.lumenconnection.music.ui.GhostButton
import com.lumenconnection.music.ui.LumenCard
import com.lumenconnection.music.ui.LumenTextField
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.SectionHeader
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Sincronização com o desktop — o recurso que só existe no mobile.
 *
 * Fluxo: descobrir na rede (ou digitar o IP), parear com o PIN mostrado no PC e
 * daí em diante sincronizar. Metadados descem sempre inteiros; o áudio só das
 * playlists escolhidas.
 */
@Composable
fun SyncScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val paired by Graph.settings.pairedServer.collectAsStateWithLifecycle(initialValue = null)
    val lastSyncAt by Graph.settings.lastSyncAt.collectAsStateWithLifecycle(initialValue = 0L)
    val syncAll by Graph.settings.syncAllPlaylists.collectAsStateWithLifecycle(initialValue = false)
    val syncState by SyncEngine.state.collectAsStateWithLifecycle()
    val playlists by Graph.db.playlistDao().observeAllByName()
        .collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.app)
            .verticalScroll(rememberScrollState())
            .padding(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        PageHeader(stringResource(R.string.sync_title))

        val server = paired
        if (server == null) {
            PairingSection(onPaired = { /* o Flow atualiza sozinho */ })
        } else {
            // --- Já pareado ---
            LumenCard {
                Text(stringResource(R.string.sync_paired_with, server.name), style = LumenText.body)
                Text("${server.host}:${server.port}", style = LumenText.bodySm)
                Text(
                    if (lastSyncAt > 0) {
                        stringResource(
                            R.string.sync_last_at,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(lastSyncAt)),
                        )
                    } else {
                        stringResource(R.string.sync_never)
                    },
                    style = LumenText.bodySm,
                    modifier = Modifier.padding(top = dimens.spacingSm),
                )

                Row(
                    Modifier.padding(top = dimens.spacing),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    AccentButton(
                        text = stringResource(R.string.sync_now),
                        onClick = { SyncService.start(context) },
                    )
                    GhostButton(
                        text = stringResource(R.string.sync_unpair),
                        onClick = { scope.launch { Graph.settings.clearPairedServer() } },
                    )
                }
            }

            SyncStatusCard(syncState)
            ContinueFromDesktopCard()

            // --- Seleção de áudio ---
            SectionHeader(stringResource(R.string.sync_audio_selection))
            LumenCard {
                Text(stringResource(R.string.sync_metadata_note), style = LumenText.bodySm)

                Row(
                    Modifier.fillMaxWidth().padding(top = dimens.spacing),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sync_all_playlists), style = LumenText.body)
                    Switch(
                        checked = syncAll,
                        onCheckedChange = {
                            scope.launch { Graph.settings.setSyncAllPlaylists(it) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent,
                        ),
                    )
                }

                if (!syncAll) {
                    playlists.forEach { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        Graph.db.playlistDao()
                                            .setSyncFiles(playlist.id, !playlist.syncFiles)
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = playlist.syncFiles,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        Graph.db.playlistDao().setSyncFiles(playlist.id, checked)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    checkmarkColor = colors.onAccent,
                                ),
                            )
                            Text(
                                playlist.name,
                                style = LumenText.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(state: SyncEngine.State) {
    val colors = LumenTheme.colors
    val text = when (state) {
        SyncEngine.State.Idle -> null
        SyncEngine.State.Pushing, SyncEngine.State.Pulling ->
            stringResource(R.string.sync_in_progress)
        is SyncEngine.State.Downloading ->
            stringResource(R.string.sync_downloading_files, state.done, state.total)
        is SyncEngine.State.Done -> stringResource(R.string.sync_done)
        is SyncEngine.State.Failed -> when (state.reason) {
            SyncEngine.Reason.UNAUTHORIZED -> stringResource(R.string.sync_revoked)
            SyncEngine.Reason.PROTOCOL -> stringResource(R.string.sync_proto_mismatch)
            else -> stringResource(R.string.sync_offline_hint)
        }
    } ?: return

    val isError = state is SyncEngine.State.Failed
    LumenCard {
        Text(
            text,
            style = LumenText.body.copy(color = if (isError) colors.danger else colors.text),
        )
    }
}

/**
 * "Continuar de onde parou no PC": o snapshot traz o estado de reprodução do
 * desktop, e se a faixa já estiver baixada aqui dá para retomar na mesma
 * posição. Só aparece quando é acionável — sem arquivo local não haveria o que
 * tocar.
 */
@Composable
private fun ContinueFromDesktopCard() {
    val scope = rememberCoroutineScope()
    val desktop by Graph.settings.desktopPlayback.collectAsStateWithLifecycle(initialValue = null)

    var track by remember { mutableStateOf<com.lumenconnection.music.db.TrackEntity?>(null) }

    LaunchedEffect(desktop) {
        val remoteId = desktop?.trackRemoteId
        track = if (remoteId == null) null
        else Graph.db.trackDao().byRemoteId(remoteId)?.takeIf { !it.filePath.isNullOrBlank() }
    }

    val playback = desktop ?: return
    val available = track ?: return

    LumenCard {
        Text(stringResource(R.string.sync_continue_title), style = LumenText.body)
        Text(
            "${available.artist} — ${available.title}",
            style = LumenText.bodySm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AccentButton(
            text = stringResource(R.string.sync_continue_action),
            onClick = {
                scope.launch {
                    val contextIds = Graph.db.trackDao().observeAll().first().map { it.id }
                    com.lumenconnection.music.player.PlayerController.playTrackAt(
                        track = available,
                        context = contextIds,
                        contextName = playback.contextName,
                        positionMs = playback.positionMs,
                    )
                    // Consome: retomar duas vezes seguidas no mesmo ponto não faz sentido.
                    Graph.settings.saveDesktopPlayback(null)
                }
            },
            modifier = Modifier.padding(top = LumenTheme.dimens.spacingSm),
        )
    }
}

/** Descoberta na rede, entrada manual de endereço e pareamento por PIN. */
@Composable
private fun PairingSection(onPaired: () -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(true) }
    var servers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var selected by remember { mutableStateOf<DiscoveredServer?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(SYNC_DEFAULT_PORT.toString()) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val pairFailed = stringResource(R.string.sync_pair_failed)
    val offlineHint = stringResource(R.string.sync_offline_hint)
    val unreachable = stringResource(R.string.sync_unreachable)

    LaunchedEffect(Unit) {
        searching = true
        servers = DiscoveryClient.discover()
        selected = servers.firstOrNull()
        searching = false
    }

    LumenCard {
        when {
            searching -> Text(stringResource(R.string.sync_searching), style = LumenText.body)
            servers.isEmpty() -> {
                Text(stringResource(R.string.sync_no_servers), style = LumenText.body)
                Text(stringResource(R.string.sync_offline_hint), style = LumenText.bodySm)
            }
            else -> {
                Text(stringResource(R.string.sync_searching), style = LumenText.micro)
                servers.forEach { candidate ->
                    val isSelected = candidate.serverId == selected?.serverId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimens.radiusWidget))
                            .background(if (isSelected) colors.cardHover else colors.card)
                            .clickable { selected = candidate }
                            .padding(dimens.spacingSm),
                    ) {
                        Column {
                            Text(
                                candidate.name,
                                style = LumenText.body.copy(
                                    color = if (isSelected) colors.accent else colors.text,
                                ),
                            )
                            Text("${candidate.host}:${candidate.port}", style = LumenText.bodySm)
                        }
                    }
                }
            }
        }

        GhostButton(
            text = stringResource(R.string.sync_searching),
            onClick = {
                scope.launch {
                    searching = true
                    servers = DiscoveryClient.discover()
                    selected = servers.firstOrNull()
                    searching = false
                }
            },
            modifier = Modifier.padding(top = dimens.spacingSm),
        )
    }

    // Endereço manual: caminho de primeira classe, porque redes com isolamento
    // de clientes bloqueiam o broadcast da descoberta.
    LumenCard {
        Text(stringResource(R.string.sync_manual_address), style = LumenText.micro)
        Row(
            Modifier.padding(top = dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Column(Modifier.weight(2f)) {
                LumenTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    placeholder = stringResource(R.string.sync_host_hint),
                )
            }
            Column(Modifier.weight(1f)) {
                LumenTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it.filter(Char::isDigit) },
                    placeholder = stringResource(R.string.sync_port_hint),
                )
            }
        }
    }

    LumenCard {
        Text(stringResource(R.string.sync_pin_prompt), style = LumenText.body)
        LumenTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null },
            placeholder = "000000",
            modifier = Modifier.padding(top = dimens.spacingSm),
        )
        error?.let {
            Text(
                it,
                style = LumenText.bodySm.copy(color = colors.danger),
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
        }
        AccentButton(
            text = stringResource(R.string.sync_pair),
            onClick = {
                val host = manualHost.trim().ifBlank { selected?.host.orEmpty() }
                val port = manualPort.toIntOrNull()
                    ?: selected?.port
                    ?: SYNC_DEFAULT_PORT
                if (host.isBlank() || pin.length != 6) {
                    error = offlineHint
                    return@AccentButton
                }
                scope.launch {
                    val deviceId = Graph.settings.deviceId()
                    val deviceName = android.os.Build.MODEL ?: "Android"
                    val result = runCatching {
                        SyncApi(host, port, null).pair(pin, deviceId, deviceName)
                    }
                    result.onSuccess { response ->
                        Graph.settings.savePairedServer(
                            PairedServer(
                                serverId = response.serverId,
                                name = response.serverName.ifBlank { host },
                                host = host,
                                port = port,
                                token = response.token,
                            )
                        )
                        onPaired()
                        // Primeiro sync logo após parear: é o que o usuário espera.
                        SyncService.start(context)
                    }.onFailure { failure ->
                        android.util.Log.w("SyncPairing", "falha ao parear com $host:$port", failure)
                        // Não confundir os dois: "PIN errado" manda o usuário
                        // conferir o número, quando o problema pode ser que o
                        // aparelho nem alcançou o PC (firewall, rede errada).
                        error = if (failure.message == "bad_pin") pairFailed else unreachable
                    }
                }
            },
            modifier = Modifier.padding(top = dimens.spacing),
        )
    }
}
