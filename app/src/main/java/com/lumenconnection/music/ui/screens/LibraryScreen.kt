package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.TrackRow
import com.lumenconnection.music.ui.theme.LumenTheme

/** Biblioteca completa, sem limite — equivalente do `src/pages/librarypage.cpp`. */
@Composable
fun LibraryScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val tracks by Graph.db.trackDao().observeAll().collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().background(colors.app)) {
        if (tracks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                subtitle = stringResource(R.string.home_empty_subtitle),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(dimens.windowMargin),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item {
                PageHeader(
                    title = stringResource(R.string.home_full_library),
                    subtitle = pluralStringResource(
                        R.plurals.track_count_in_library, tracks.size, tracks.size,
                    ),
                )
            }
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
