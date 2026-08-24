package com.lumenconnection.music.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Menu de contexto da faixa — port do `src/models/trackcontextmenu.cpp`, com a
 * mesma ordem de ações: tocar, adicionar à fila, separador, curtir/descurtir,
 * adicionar à playlist (submenu marcável), separador, editar, excluir.
 *
 * @param inPlaylistId quando a faixa é vista dentro de uma playlist, aparece
 *   também "remover da playlist", como no desktop.
 */
@Composable
fun TrackMenu(
    track: TrackEntity,
    expanded: Boolean,
    onDismiss: () -> Unit,
    inPlaylistId: Long? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LumenTheme.colors
    val scope = rememberCoroutineScope()
    val library = Graph.library

    val playlists by Graph.db.playlistDao().observeAllByName()
        .collectAsStateWithLifecycle(emptyList())

    var memberOf by remember { mutableStateOf(emptySet<Long>()) }
    var showPlaylists by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, expanded) {
        if (expanded) memberOf = withContext(Dispatchers.IO) { library.playlistsOf(track.id) }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showPlaylists = false
            onDismiss()
        },
    ) {
        if (!showPlaylists) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_play), style = LumenText.body) },
                onClick = {
                    PlayerController.playTrack(track, listOf(track.id), "")
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_add_to), style = LumenText.body) },
                onClick = {
                    PlayerController.enqueue(track)
                    onDismiss()
                },
            )

            HorizontalDivider(color = colors.border)

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(if (track.liked) R.string.action_unlike else R.string.action_like),
                        style = LumenText.body,
                    )
                },
                onClick = {
                    scope.launch { library.toggleLike(track.id) }
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_add_to), style = LumenText.body) },
                onClick = { showPlaylists = true },
            )

            if (inPlaylistId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_remove_from), style = LumenText.body) },
                    onClick = {
                        scope.launch { library.removeTrackFromPlaylist(inPlaylistId, track.id) }
                        onDismiss()
                    },
                )
            }

            HorizontalDivider(color = colors.border)

            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_edit), style = LumenText.body) },
                onClick = {
                    onDismiss()
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.song_delete),
                        style = LumenText.body.copy(color = colors.danger),
                    )
                },
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        } else {
            // Submenu de playlists com marcação de pertencimento, como o
            // submenu checkable do desktop.
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text(stringResource(R.string.playlist_none_available), style = LumenText.bodySm)
                    },
                    onClick = {},
                )
            }
            playlists.forEach { playlist ->
                val isMember = playlist.id in memberOf
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (isMember) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                )
                            }
                            Text(playlist.name, style = LumenText.body)
                        }
                    },
                    onClick = {
                        scope.launch {
                            if (isMember) library.removeTrackFromPlaylist(playlist.id, track.id)
                            else library.addTrackToPlaylist(playlist.id, track.id)
                            memberOf = withContext(Dispatchers.IO) { library.playlistsOf(track.id) }
                        }
                        showPlaylists = false
                        onDismiss()
                    },
                )
            }
        }
    }
}
