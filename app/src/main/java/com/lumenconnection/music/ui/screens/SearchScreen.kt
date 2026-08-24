package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.Routes
import com.lumenconnection.music.ui.SectionHeader
import com.lumenconnection.music.ui.TrackRow
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import com.lumenconnection.music.util.TextUtils
import kotlinx.coroutines.flow.flowOf

/**
 * Busca global: títulos, artistas e nomes de playlist, ignorando acentos —
 * mesma semântica do `src/pages/searchpage.cpp`, que filtra pela chave
 * normalizada em NFD.
 */
@Composable
fun SearchScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db

    var query by remember { mutableStateOf("") }
    val normalized = remember(query) { TextUtils.normalized(query.trim()) }

    val tracks by remember(normalized) {
        if (normalized.isBlank()) flowOf(emptyList()) else db.trackDao().search(normalized)
    }.collectAsStateWithLifecycle(emptyList())

    val playlists by remember(normalized) {
        if (normalized.isBlank()) flowOf(emptyList()) else db.playlistDao().search(normalized)
    }.collectAsStateWithLifecycle(emptyList())

    // Fora do LazyColumn: o escopo dele não é @Composable.
    val songsLabel = stringResource(R.string.songs)
    val playlistsLabel = stringResource(R.string.nav_playlists)
    val playlistTag = stringResource(R.string.playlist_caps)
    val resultsLabel = stringResource(R.string.search_results_for, query)

    Column(Modifier.fillMaxSize().background(colors.app)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.windowMargin, vertical = dimens.spacingSm)
                .clip(RoundedCornerShape(dimens.radiusWidget))
                .background(colors.input)
                .padding(horizontal = dimens.spacing, vertical = dimens.btnPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = LumenText.body,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = LumenText.bodySm)
                    }
                    inner()
                },
            )
        }

        when {
            query.isBlank() -> EmptyState(stringResource(R.string.search_empty_prompt))

            tracks.isEmpty() && playlists.isEmpty() ->
                EmptyState(stringResource(R.string.search_no_results, query))

            else -> LazyColumn(
                contentPadding = PaddingValues(dimens.windowMargin),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                if (playlists.isNotEmpty()) {
                    item { SectionHeader(playlistsLabel) }
                    items(playlists, key = { "pl-${it.id}" }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(dimens.radiusWidget))
                                .clickable { nav.navigate(Routes.folder(playlist.id)) }
                                .padding(dimens.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                        ) {
                            PlaylistCover(
                                coverImagePath = playlist.coverImagePath,
                                color1 = playlist.coverColor1,
                                color2 = playlist.coverColor2,
                                size = 44.dp,
                                radius = dimens.radiusWidget,
                            )
                            Column {
                                Text(
                                    playlist.name,
                                    style = LumenText.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(playlistTag, style = LumenText.micro)
                            }
                        }
                    }
                }

                if (tracks.isNotEmpty()) {
                    item { SectionHeader(songsLabel) }
                    items(tracks, key = { "tr-${it.id}" }) { track ->
                        PlayableTrackRow(track, tracks, resultsLabel)
                    }
                }
            }
        }
    }
}
