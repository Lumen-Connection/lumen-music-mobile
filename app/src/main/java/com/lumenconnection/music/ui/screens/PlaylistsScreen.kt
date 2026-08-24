package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.db.PlaylistEntity
import com.lumenconnection.music.ui.AccentButton
import com.lumenconnection.music.ui.ConfirmDialog
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.PlaylistDialog
import com.lumenconnection.music.ui.Routes
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.launch

/**
 * Grade de playlists — equivalente do `src/pages/folderspage.cpp`, com criar,
 * renomear, trocar a capa e excluir. A grade adapta as colunas à largura, como o
 * `columnCountForWidth()` do desktop faz no `resizeEvent`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db
    val library = Graph.library
    val scope = rememberCoroutineScope()

    val playlists by db.playlistDao().observeAllByName().collectAsStateWithLifecycle(emptyList())
    val coverColors by db.playlistDao().observeAllCoverColors().collectAsStateWithLifecycle(emptyList())

    // Uma consulta só alimenta todos os mosaicos; uma query por card seria O(n)
    // consultas durante a rolagem.
    val mosaicByPlaylist = remember(coverColors) {
        coverColors.groupBy { it.playlistId }
            .mapValues { (_, rows) -> rows.take(4).map { it.coverColor1 to it.coverColor2 } }
    }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleting by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(Modifier.fillMaxSize().background(colors.app)) {
        Row(
            Modifier.fillMaxWidth().padding(dimens.windowMargin),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nav_playlists), style = LumenText.title)
            AccentButton(
                text = stringResource(R.string.playlist_new),
                onClick = { creating = true },
            )
        }

        if (playlists.isEmpty()) {
            EmptyState(stringResource(R.string.playlist_none_yet))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(
                    start = dimens.windowMargin,
                    end = dimens.windowMargin,
                    bottom = dimens.windowMargin,
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        mosaic = mosaicByPlaylist[playlist.id].orEmpty(),
                        onOpen = { nav.navigate(Routes.folder(playlist.id)) },
                        onEdit = { editing = playlist },
                        onDelete = { deleting = playlist },
                    )
                }
            }
        }
    }

    if (creating) {
        PlaylistDialog(
            titleText = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name, c1, c2 ->
                scope.launch { library.createPlaylist(name, c1, c2) }
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    editing?.let { playlist ->
        PlaylistDialog(
            titleText = stringResource(R.string.playlist_edit_title),
            initialName = playlist.name,
            initialColor1 = playlist.coverColor1,
            initialColor2 = playlist.coverColor2,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name, c1, c2 ->
                scope.launch {
                    // Renomear não muda dirName: a pasta em disco é estável,
                    // como no desktop.
                    library.renamePlaylist(playlist.id, name)
                    library.setPlaylistCover(playlist.id, c1, c2, playlist.coverImagePath)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { playlist ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_confirm, playlist.name),
            note = stringResource(R.string.playlist_delete_note),
            onConfirm = {
                scope.launch { library.deletePlaylist(playlist.id) }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    mosaic: List<Pair<String, String>>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    var menuOpen by remember { mutableStateOf(false) }
    val trackCount by Graph.db.playlistDao().observeTrackCount(playlist.id)
        .collectAsStateWithLifecycle(0)

    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(dimens.radiusCard))
                .background(colors.card)
                // Toque longo abre o menu — no desktop é o clique direito.
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(dimens.spacingSm),
        ) {
            PlaylistCover(
                coverImagePath = playlist.coverImagePath,
                color1 = playlist.coverColor1,
                color2 = playlist.coverColor2,
                trackColors = mosaic,
                size = 132.dp,
            )
            Text(
                playlist.name,
                style = LumenText.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
            Text(
                pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                style = LumenText.bodySm,
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_edit), style = LumenText.body) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.playlist_delete_title),
                        style = LumenText.body.copy(color = colors.danger),
                    )
                },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}
