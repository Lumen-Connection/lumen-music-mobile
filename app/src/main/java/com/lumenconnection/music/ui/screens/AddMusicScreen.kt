package com.lumenconnection.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.download.DownloadController
import com.lumenconnection.music.importer.PlaylistImporter
import com.lumenconnection.music.media.LocalImport
import com.lumenconnection.music.ui.AccentButton
import com.lumenconnection.music.ui.GhostButton
import com.lumenconnection.music.ui.LumenCard
import com.lumenconnection.music.ui.LumenTextField
import com.lumenconnection.music.ui.PageHeader
import com.lumenconnection.music.ui.SectionHeader
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inserção de músicas — equivalente do `src/pages/addmusicpage.cpp`: arquivos do
 * aparelho, download por link do YouTube e importação de playlist do Spotify ou
 * do YouTube.
 */
@Composable
fun AddMusicScreen(nav: NavHostController) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unknownArtist = stringResource(R.string.unknown_artist)

    var importedCount by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }

    val jobs by DownloadController.jobs.collectAsStateWithLifecycle()

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

    val youtubeLinkError = stringResource(R.string.download_youtube_link_error)
    val playlistLinkError = stringResource(R.string.add_music_convert_error)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.app)
            .verticalScroll(rememberScrollState())
            .padding(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        PageHeader(stringResource(R.string.add_music_title))

        // --- Arquivos do aparelho ---
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

        // --- Download por link ---
        LumenCard {
            Text(stringResource(R.string.action_download), style = LumenText.body)
            LumenTextField(
                value = url,
                onValueChange = { url = it; urlError = null },
                placeholder = stringResource(R.string.download_url_hint),
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
            urlError?.let {
                Text(
                    it,
                    style = LumenText.bodySm.copy(color = colors.danger),
                    modifier = Modifier.padding(top = dimens.spacingSm),
                )
            }
            Row(
                Modifier.padding(top = dimens.spacing),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                AccentButton(
                    text = stringResource(R.string.action_download),
                    onClick = {
                        val trimmed = url.trim()
                        when {
                            trimmed.isBlank() -> Unit
                            PlaylistImporter.isSupported(trimmed) -> showImport = true
                            isYouTubeLink(trimmed) -> {
                                DownloadController.enqueueUrl(context, trimmed)
                                url = ""
                            }
                            else -> urlError = youtubeLinkError
                        }
                    },
                )
                GhostButton(
                    text = stringResource(R.string.import_streaming_playlist),
                    onClick = {
                        val trimmed = url.trim()
                        if (PlaylistImporter.isSupported(trimmed)) showImport = true
                        else urlError = playlistLinkError
                    },
                )
            }
            Text(
                stringResource(R.string.add_music_convert_hint),
                style = LumenText.bodySm,
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
        }

        // --- Fila de downloads ---
        if (jobs.isNotEmpty()) {
            SectionHeader(stringResource(R.string.download_queue)) {
                Text(
                    stringResource(R.string.action_clear),
                    style = LumenText.micro.copy(color = colors.accent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimens.radiusWidget))
                        .clickable { DownloadController.clearFinished() }
                        .padding(4.dp),
                )
            }
            jobs.forEach { job -> DownloadRow(job) }
        }
    }

    if (showImport) {
        ImportPlaylistDialog(
            url = url.trim(),
            onDismiss = { showImport = false },
            onDone = { showImport = false; url = "" },
        )
    }
}

private fun isYouTubeLink(url: String): Boolean =
    url.contains("youtube.com") || url.contains("youtu.be")

@Composable
private fun DownloadRow(job: DownloadController.Job) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens

    LumenCard {
        Text(job.label, style = LumenText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)

        val statusText = when (job.status) {
            DownloadController.Status.PENDING -> stringResource(R.string.download_connecting)
            DownloadController.Status.RUNNING ->
                stringResource(R.string.download_progress, (job.progress * 100).toInt())
            DownloadController.Status.DONE -> stringResource(R.string.download_done)
            DownloadController.Status.FAILED ->
                job.errorRes?.let { stringResource(it) } ?: stringResource(R.string.download_failed)
        }
        Text(
            statusText,
            style = LumenText.bodySm.copy(
                color = if (job.status == DownloadController.Status.FAILED) colors.danger else colors.muted,
            ),
        )

        if (job.status == DownloadController.Status.RUNNING) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.spacingSm)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.input),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(job.progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colors.accent)
                )
            }
        }
    }
}
