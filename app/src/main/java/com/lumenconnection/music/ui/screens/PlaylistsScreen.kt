package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.Routes
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme

/**
 * Grade de playlists — equivalente do `src/pages/folderspage.cpp`. A grade
 * adapta o número de colunas à largura, como o `columnCountForWidth()` do
 * desktop faz no `resizeEvent`.
 */
@Composable
fun PlaylistsScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db

    val playlists by db.playlistDao().observeAllByName().collectAsStateWithLifecycle(emptyList())
    val coverColors by db.playlistDao().observeAllCoverColors().collectAsStateWithLifecycle(emptyList())

    // Uma consulta só alimenta todos os mosaicos; agrupar aqui evita uma query
    // por card na grade.
    val mosaicByPlaylist = remember(coverColors) {
        coverColors.groupBy { it.playlistId }
            .mapValues { (_, rows) -> rows.take(4).map { it.coverColor1 to it.coverColor2 } }
    }

    Column(Modifier.fillMaxSize().background(colors.app)) {
        if (playlists.isEmpty()) {
            EmptyState(stringResource(R.string.playlist_none_yet))
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(dimens.windowMargin),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(dimens.radiusCard))
                        .background(colors.card)
                        .clickable { nav.navigate(Routes.folder(playlist.id)) }
                        .padding(dimens.spacingSm),
                ) {
                    PlaylistCover(
                        coverImagePath = playlist.coverImagePath,
                        color1 = playlist.coverColor1,
                        color2 = playlist.coverColor2,
                        trackColors = mosaicByPlaylist[playlist.id].orEmpty(),
                        size = 132.dp,
                    )
                    Text(
                        playlist.name,
                        style = LumenText.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = dimens.spacingSm),
                    )
                }
            }
        }
    }
}
