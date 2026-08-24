package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.ConfirmDialog
import com.lumenconnection.music.ui.EditTrackDialog
import com.lumenconnection.music.ui.TrackMenu
import com.lumenconnection.music.ui.TrackRow
import kotlinx.coroutines.launch

/**
 * Linha de faixa ligada ao player e ao menu de contexto.
 *
 * Tocar define a lista visível como contexto de reprodução, exatamente como o
 * desktop faz ao clicar numa faixa dentro de uma playlist, das curtidas ou da
 * biblioteca.
 */
@Composable
fun PlayableTrackRow(
    track: TrackEntity,
    context: List<TrackEntity>,
    contextName: String,
    inPlaylistId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val library = Graph.library
    val current by PlayerController.currentTrack.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Box(modifier) {
        TrackRow(
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs,
            color1 = track.coverColor1,
            color2 = track.coverColor2,
            liked = track.liked,
            isPlaying = current?.id == track.id,
            onClick = { PlayerController.playTrack(track, context.map { it.id }, contextName) },
            onToggleLike = { scope.launch { library.toggleLike(track.id) } },
            onMenu = { menuOpen = true },
        )

        TrackMenu(
            track = track,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            inPlaylistId = inPlaylistId,
            onEdit = { editing = true },
            onDelete = { confirmingDelete = true },
        )
    }

    if (editing) {
        EditTrackDialog(
            initialTitle = track.title,
            initialArtist = track.artist,
            onConfirm = { title, artist ->
                scope.launch { library.editTrack(track.id, title, artist) }
                editing = false
            },
            onDismiss = { editing = false },
        )
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.song_delete_title),
            message = stringResource(R.string.song_delete_confirm, track.title),
            note = stringResource(R.string.song_delete_note),
            onConfirm = {
                scope.launch { library.deleteTrack(track.id) }
                confirmingDelete = false
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
