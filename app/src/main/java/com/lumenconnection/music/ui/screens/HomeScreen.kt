package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.AccentButton
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.Routes
import com.lumenconnection.music.ui.SectionHeader
import com.lumenconnection.music.ui.TrackCover
import com.lumenconnection.music.ui.TrackRow
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import com.lumenconnection.music.util.Greeting
import java.util.Calendar
import kotlin.random.Random

private const val SHELF_LIMIT = 10

/**
 * Início: saudação por faixa horária, chips de playlists, "Recentes" e as duas
 * prateleiras (tocadas / adicionadas recentemente) — a mesma composição do
 * `src/pages/homepage.cpp`.
 */
@Composable
fun HomeScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db

    val playlists by db.playlistDao().observeAllByName().collectAsStateWithLifecycle(emptyList())
    val recentlyPlayed by db.trackDao().observeRecentlyPlayed(SHELF_LIMIT)
        .collectAsStateWithLifecycle(emptyList())
    val recentlyAdded by db.trackDao().observeRecentlyAdded(SHELF_LIMIT)
        .collectAsStateWithLifecycle(emptyList())
    val total by db.trackDao().observeCount().collectAsStateWithLifecycle(0)

    // A variante da saudação é sorteada uma vez por entrada na tela, como o
    // desktop faz ao montar a HomePage.
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val variant = remember { Random.nextInt(1000) }
    val greeting = stringResource(Greeting.resFor(hour, variant))

    // Os rótulos das prateleiras viram nome do contexto de reprodução. Ficam
    // aqui porque o escopo do LazyColumn não é @Composable.
    val playedLabel = stringResource(R.string.home_recently_played)
    val addedLabel = stringResource(R.string.home_recently_added)
    val playlistsLabel = stringResource(R.string.your_playlists_caps)
    val recentsLabel = stringResource(R.string.home_recents)

    if (total == 0) {
        Column(
            Modifier.fillMaxSize().background(colors.app),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                subtitle = stringResource(R.string.home_empty_subtitle),
            ) {
                AccentButton(
                    text = stringResource(R.string.home_add_songs),
                    onClick = { nav.navigate(Routes.ADD_MUSIC) },
                )
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(colors.app),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        item {
            Column {
                Text(greeting, style = LumenText.title)
                Text(stringResource(R.string.home_prompt), style = LumenText.bodySm)
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                SectionHeader(playlistsLabel)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Column(
                            Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(dimens.radiusCard))
                                .clickable { nav.navigate(Routes.folder(playlist.id)) }
                                .padding(dimens.spacingSm),
                        ) {
                            PlaylistCover(
                                coverImagePath = playlist.coverImagePath,
                                color1 = playlist.coverColor1,
                                color2 = playlist.coverColor2,
                                size = 108.dp,
                            )
                            Text(
                                playlist.name,
                                style = LumenText.bodySm.copy(color = colors.text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = dimens.spacingSm),
                            )
                        }
                    }
                }
            }
        }

        // Tira "Recentes": atalhos compactos para o que acabou de tocar, como a
        // faixa horizontal da HomePage do desktop.
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(recentsLabel)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                    items(recentlyPlayed.take(6), key = { "chip-${it.id}" }) { track ->
                        RecentChip(track) {
                            PlayerController.playTrack(
                                track, recentlyPlayed.map { it.id }, recentsLabel,
                            )
                        }
                    }
                }
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeader(playedLabel) }
            items(recentlyPlayed, key = { "played-${it.id}" }) { track ->
                PlayableTrackRow(track, recentlyPlayed, playedLabel)
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader(addedLabel) }
            items(recentlyAdded, key = { "added-${it.id}" }) { track ->
                PlayableTrackRow(track, recentlyAdded, addedLabel)
            }
        }
    }
}

/** Faixa horizontal compacta usada na tira "Recentes". */
@Composable
fun RecentChip(track: TrackEntity, onClick: () -> Unit) {
    val dimens = LumenTheme.dimens
    Row(
        Modifier
            .clip(RoundedCornerShape(dimens.radiusWidget))
            .background(LumenTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(end = dimens.spacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        TrackCover(track.coverColor1, track.coverColor2, size = 44.dp, radius = dimens.radiusWidget)
        Text(
            track.title,
            style = LumenText.bodySm.copy(color = LumenTheme.colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
    }
}
