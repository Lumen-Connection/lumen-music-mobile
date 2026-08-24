package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.db.SortMode
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.TrackRow
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.flow.flowOf

/**
 * Detalhe da playlist — equivalente do `src/pages/folderdetailpage.cpp`.
 *
 * Os 6 modos de ordenação são consultas separadas no DAO (o Room exige SQL
 * estático); a escolha fica persistida em `playlists.sortMode`, como no desktop.
 * Arrastar para reordenar, filtro e menu de contexto entram na fase 3.
 */
@Composable
fun FolderDetailScreen(nav: NavHostController, folderId: Long) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db

    val playlist by db.playlistDao().observeById(folderId).collectAsStateWithLifecycle(null)
    val sortMode = playlist?.sortMode ?: SortMode.CUSTOM

    val tracks by remember(folderId, sortMode) {
        val dao = db.playlistTrackDao()
        when (sortMode) {
            SortMode.CUSTOM -> dao.observeCustom(folderId)
            SortMode.TITLE -> dao.observeByTitle(folderId)
            SortMode.ARTIST -> dao.observeByArtist(folderId)
            SortMode.RECENT -> dao.observeByRecent(folderId)
            SortMode.OLDEST -> dao.observeByOldest(folderId)
            SortMode.DURATION -> dao.observeByDuration(folderId)
        }
    }.collectAsStateWithLifecycle(emptyList())

    val mosaic by remember(folderId) {
        db.playlistTrackDao().observeMosaicColors(folderId)
    }.collectAsStateWithLifecycle(emptyList())

    val current = playlist ?: run {
        Column(Modifier.fillMaxSize().background(colors.app)) {}
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(colors.app),
        contentPadding = PaddingValues(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = dimens.spacing),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(
                    coverImagePath = current.coverImagePath,
                    color1 = current.coverColor1,
                    color2 = current.coverColor2,
                    trackColors = mosaic.map { it.coverColor1 to it.coverColor2 },
                    size = 108.dp,
                )
                Column {
                    Text(stringResource(R.string.playlist_caps), style = LumenText.micro)
                    Text(current.name, style = LumenText.title)
                    Text(
                        pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                        style = LumenText.bodySm,
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            item { EmptyState(stringResource(R.string.home_start_listening)) }
        } else {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    color1 = track.coverColor1,
                    color2 = track.coverColor2,
                    liked = track.liked,
                    onClick = {},
                    onToggleLike = {},
                    onMenu = {},
                )
            }
        }
    }
}
