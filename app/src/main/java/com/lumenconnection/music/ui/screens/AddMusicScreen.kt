package com.lumenconnection.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.media.LocalImport
import com.lumenconnection.music.ui.AccentButton
import com.lumenconnection.music.ui.LumenCard
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inserção de músicas — equivalente do `src/pages/addmusicpage.cpp`.
 *
 * O arrastar-e-soltar do desktop vira o seletor do SAF (mesma função, gesto
 * diferente). Baixar do YouTube e importar playlists do Spotify/YouTube entram
 * na fase 4.
 */
@Composable
fun AddMusicScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unknownArtist = stringResource(R.string.unknown_artist)

    var importedCount by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                val dao = Graph.db.trackDao()
                uris.count { uri ->
                    val track = LocalImport.buildTrack(context, uri, unknownArtist)
                        ?: return@count false
                    // O índice único em filePath evita duplicar o mesmo arquivo.
                    runCatching { dao.insert(track) }.isSuccess
                }
            }
            importedCount = added
        }
    }

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
            AccentButton(
                text = stringResource(R.string.add_music_browse),
                onClick = { picker.launch(LocalImport.MIME_FILTER) },
                modifier = Modifier.padding(top = dimens.spacing),
            )
            if (importedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.file_count_selected, importedCount, importedCount),
                    style = LumenText.bodySm,
                    modifier = Modifier.padding(top = dimens.spacingSm),
                )
            }
        }

        LumenCard {
            Text(stringResource(R.string.add_music_convert_playlist), style = LumenText.body)
            Text(stringResource(R.string.add_music_convert_hint), style = LumenText.bodySm)
        }
    }
}
