package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.download.DownloadController
import com.lumenconnection.music.importer.PlaylistImporter
import com.lumenconnection.music.ui.LumenTextField
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.launch

/**
 * Assistente de importação — port do `src/pages/importplaylistdialog.cpp`.
 *
 * O ponto central é a **aprovação antes do download**: o desktop nunca baixa uma
 * playlist inteira sem o usuário revisar a lista, e as correspondências
 * duvidosas aparecem destacadas para conferência.
 */
@Composable
fun ImportPlaylistDialog(url: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<PlaylistImporter.Candidate>>(emptyList()) }

    val genericError = stringResource(R.string.import_youtube_error)

    LaunchedEffect(url) {
        loading = true
        error = null
        runCatching { PlaylistImporter.resolve(url) }
            .onSuccess { result ->
                name = result.suggestedName
                candidates = result.candidates
                if (result.candidates.isEmpty()) {
                    error = context.getString(R.string.import_no_songs)
                }
            }
            .onFailure { error = it.message ?: genericError }
        loading = false
    }

    val approvedCount = candidates.count { it.approved }
    val doubtfulCount = candidates.count { !it.confident }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        title = { Text(stringResource(R.string.import_playlist), style = LumenText.body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                when {
                    loading -> Text(stringResource(R.string.import_fetching), style = LumenText.body)

                    error != null -> Text(
                        error!!,
                        style = LumenText.body.copy(color = colors.danger),
                    )

                    else -> {
                        LumenTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = stringResource(R.string.import_name_in_lumen),
                        )

                        Text(
                            if (doubtfulCount > 0) {
                                pluralStringResource(R.plurals.import_review, doubtfulCount, doubtfulCount)
                            } else {
                                stringResource(R.string.import_all_set)
                            },
                            style = LumenText.bodySm,
                        )

                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            itemsIndexed(candidates, key = { i, c -> "$i-${c.label}" }) { index, candidate ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            candidates = candidates.toMutableList().also {
                                                it[index] = candidate.copy(approved = !candidate.approved)
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = candidate.approved,
                                        onCheckedChange = { checked ->
                                            candidates = candidates.toMutableList().also {
                                                it[index] = candidate.copy(approved = checked)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colors.accent,
                                            checkmarkColor = colors.onAccent,
                                        ),
                                    )
                                    Text(
                                        candidate.label,
                                        style = LumenText.bodySm.copy(
                                            // Correspondência duvidosa em destaque, como
                                            // o laranja do checklist do desktop.
                                            color = if (candidate.confident) colors.text else colors.accent,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && error == null && approvedCount > 0 && name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val playlistId = Graph.library.createPlaylist(name.trim())
                        DownloadController.enqueue(
                            context,
                            candidates.filter { it.approved }.map { candidate ->
                                DownloadController.Job(
                                    label = candidate.label,
                                    target = candidate.target,
                                    playlistId = playlistId,
                                )
                            },
                        )
                        onDone()
                    }
                },
            ) {
                Text(
                    stringResource(R.string.action_import),
                    style = LumenText.body.copy(color = colors.accent),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = LumenText.body)
            }
        },
    )
}
