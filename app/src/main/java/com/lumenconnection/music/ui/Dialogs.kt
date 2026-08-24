package com.lumenconnection.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumenconnection.music.R
import com.lumenconnection.music.media.LocalImport
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme

/** Campo de texto no estilo do desktop (fundo `input`, borda do tema). */
@Composable
fun LumenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusWidget))
            .background(colors.input)
            .border(dimens.borderWidth, colors.border, RoundedCornerShape(dimens.radiusWidget))
            .padding(horizontal = dimens.spacing, vertical = dimens.btnPadV),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LumenText.body,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = LumenText.bodySm)
                inner()
            },
        )
    }
}

/** Confirmação destrutiva, com o texto explicativo que o desktop mostra. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    note: String? = null,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LumenTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(title, style = LumenText.body) },
        text = {
            Column {
                Text(message, style = LumenText.body)
                if (!note.isNullOrBlank()) {
                    Text(note, style = LumenText.bodySm, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = LumenText.body.copy(color = colors.danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = LumenText.body)
            }
        },
    )
}

/** Editar título e artista — grava só no banco, como o desktop (não mexe no arquivo). */
@Composable
fun EditTrackDialog(
    initialTitle: String,
    initialArtist: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LumenTheme.colors
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        title = { Text(stringResource(R.string.song_edit_title), style = LumenText.body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LumenTextField(title, { title = it }, stringResource(R.string.song_title_hint))
                LumenTextField(artist, { artist = it }, stringResource(R.string.song_artist_hint))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), artist.trim()) },
            ) {
                Text(stringResource(R.string.action_save), style = LumenText.body.copy(color = colors.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = LumenText.body)
            }
        },
    )
}

/**
 * Criar ou renomear playlist, com escolha do gradiente da capa — as mesmas duas
 * cores do desktop (`cover_color1`/`cover_color2`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistDialog(
    titleText: String,
    initialName: String = "",
    initialColor1: String? = null,
    initialColor2: String? = null,
    confirmLabel: String,
    onConfirm: (name: String, color1: String, color2: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val gradients = remember {
        listOf(
            "#e8a44a" to "#d45d5d",
            "#4aa8e8" to "#5d7fd4",
            "#6bcf7f" to "#3fa16a",
            "#c084fc" to "#7b5cd6",
            "#ff5722" to "#c1440e",
            "#f06292" to "#d4145d",
            "#4dd0e1" to "#0097a7",
            "#ffb74d" to "#f57c00",
        )
    }

    var name by remember { mutableStateOf(initialName) }
    var selected by remember {
        mutableStateOf(
            if (initialColor1 != null && initialColor2 != null) initialColor1 to initialColor2
            else LocalImport.randomGradient()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        title = { Text(titleText, style = LumenText.body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing)) {
                LumenTextField(name, { name = it }, stringResource(R.string.playlist_name_example))

                Text(stringResource(R.string.playlist_cover_colors), style = LumenText.micro)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    gradients.forEach { pair ->
                        val isSelected = pair == selected
                        Box(
                            Modifier
                                .size(if (isSelected) 44.dp else 38.dp)
                                .clip(CircleShape)
                                .background(gradientBrush(pair.first, pair.second))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) colors.text else colors.border,
                                    shape = CircleShape,
                                )
                                .clickable { selected = pair },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaylistCover(
                        coverImagePath = "",
                        color1 = selected.first,
                        color2 = selected.second,
                        size = 64.dp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selected.first, selected.second) },
            ) {
                Text(confirmLabel, style = LumenText.body.copy(color = colors.accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = LumenText.body)
            }
        },
    )
}
