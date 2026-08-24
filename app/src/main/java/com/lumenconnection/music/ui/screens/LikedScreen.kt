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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** Coleção de curtidas — equivalente do `src/pages/likedpage.cpp`. */
@Composable
fun LikedScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val scope = rememberCoroutineScope()
    val dao = Graph.db.trackDao()
    val tracks by dao.observeLiked().collectAsStateWithLifecycle(emptyList())
    // Fora do LazyColumn: o escopo dele não é @Composable.
    val label = stringResource(R.string.nav_liked)

    Column(Modifier.fillMaxSize().background(colors.app)) {
        if (tracks.isEmpty()) {
            EmptyState(stringResource(R.string.liked_empty))
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(dimens.windowMargin),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item {
                PageHeader(
                    title = label,
                    subtitle = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                )
            }
            items(tracks, key = { it.id }) { track ->
                PlayableTrackRow(track, tracks, label)
            }
        }
    }
}
