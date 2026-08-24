package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.lumenconnection.music.R
import com.lumenconnection.music.ui.LumenCard
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme

/**
 * Inserção de músicas — equivalente do `src/pages/addmusicpage.cpp`.
 *
 * A seleção por SAF entra na fase 2 (item 2.9); o download do YouTube e a
 * importação de playlists do Spotify/YouTube entram na fase 4.
 */
@Composable
fun AddMusicScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.app)
            .verticalScroll(rememberScrollState())
            .padding(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        PageHeader(stringResource(R.string.add_music_title))

        LumenCard {
            Text(stringResource(R.string.add_music_pick_hint), style = LumenText.body)
            Text(stringResource(R.string.add_music_drop_hint), style = LumenText.bodySm)
        }

        LumenCard {
            Text(stringResource(R.string.add_music_convert_playlist), style = LumenText.body)
            Text(stringResource(R.string.add_music_convert_hint), style = LumenText.bodySm)
        }
    }
}
