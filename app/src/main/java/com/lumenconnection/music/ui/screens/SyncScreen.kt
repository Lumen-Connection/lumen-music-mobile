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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.ui.LumenCard
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme

/**
 * Sincronização com o desktop — o recurso que só existe no mobile.
 *
 * A descoberta na rede, o pareamento por PIN e o motor de sync entram na fase 6,
 * depois de o servidor existir no repo lumen-music (fase 5).
 */
@Composable
fun SyncScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val paired by Graph.settings.pairedServer.collectAsStateWithLifecycle(initialValue = null)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.app)
            .verticalScroll(rememberScrollState())
            .padding(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        PageHeader(stringResource(R.string.sync_title))

        LumenCard {
            val server = paired
            if (server == null) {
                Text(stringResource(R.string.sync_no_servers), style = LumenText.body)
                Text(stringResource(R.string.sync_offline_hint), style = LumenText.bodySm)
            } else {
                Text(stringResource(R.string.sync_paired_with, server.name), style = LumenText.body)
                Text("${server.host}:${server.port}", style = LumenText.bodySm)
            }
        }

        LumenCard {
            Text(stringResource(R.string.sync_audio_selection), style = LumenText.body)
            Text(stringResource(R.string.sync_metadata_note), style = LumenText.bodySm)
        }
    }
}
