package com.lumenconnection.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.R
import com.lumenconnection.music.db.RepeatMode
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme

/**
 * "Tocando agora" + fila, no lugar do painel lateral do desktop
 * (`src/pages/queuepage.cpp`) — num celular a fila cabe melhor numa folha
 * deslizante do que numa coluna fixa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(onDismiss: () -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val track by PlayerController.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val position by PlayerController.positionMs.collectAsStateWithLifecycle()
    val duration by PlayerController.durationMs.collectAsStateWithLifecycle()
    val shuffle by PlayerController.shuffle.collectAsStateWithLifecycle()
    val repeat by PlayerController.repeatMode.collectAsStateWithLifecycle()
    val userQueue by PlayerController.userQueue.collectAsStateWithLifecycle()
    val upcoming by PlayerController.upcoming.collectAsStateWithLifecycle()
    val contextName by PlayerController.contextName.collectAsStateWithLifecycle()

    val current = track ?: return

    // Enquanto o usuário arrasta, a barra segue o dedo em vez do player.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.app,
        contentColor = colors.text,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = dimens.windowMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    stringResource(R.string.queue_now_playing_caps),
                    style = LumenText.micro,
                    modifier = Modifier.padding(bottom = dimens.spacing),
                )

                SpinningVinyl(
                    color1 = current.coverColor1,
                    color2 = current.coverColor2,
                    spinning = isPlaying,
                    size = 220.dp,
                )

                Text(
                    current.title,
                    style = LumenText.title,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.spacingLg),
                )
                Text(current.artist, style = LumenText.bodySm)

                val fraction = when {
                    scrubbing -> scrubValue
                    duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
                    else -> 0f
                }
                Slider(
                    value = fraction,
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) PlayerController.seek((scrubValue * duration).toLong())
                        scrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.input,
                    ),
                    modifier = Modifier.padding(top = dimens.spacing),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(if (scrubbing) (scrubValue * duration).toLong() else position), style = LumenText.micro)
                    Text(formatDuration(duration), style = LumenText.micro)
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = dimens.spacingLg),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(
                        icon = Icons.Default.Shuffle,
                        descriptionRes = R.string.player_shuffle,
                        active = shuffle,
                    ) { PlayerController.setShuffle(!shuffle) }

                    ControlIcon(Icons.Default.SkipPrevious, R.string.player_previous, size = 44.dp) {
                        PlayerController.prev()
                    }

                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .clickable { PlayerController.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_play_pause),
                            tint = colors.onAccent,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    ControlIcon(Icons.Default.SkipNext, R.string.player_next, size = 44.dp) {
                        PlayerController.next()
                    }

                    ControlIcon(
                        icon = if (repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        descriptionRes = when (repeat) {
                            RepeatMode.OFF -> R.string.player_repeat
                            RepeatMode.ALL -> R.string.player_repeat_all
                            RepeatMode.ONE -> R.string.player_repeat_one
                        },
                        active = repeat != RepeatMode.OFF,
                    ) { PlayerController.cycleRepeatMode() }
                }
            }

            if (userQueue.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.queue_up_next_caps)) {
                        Text(
                            stringResource(R.string.action_clear),
                            style = LumenText.micro.copy(color = colors.accent),
                            modifier = Modifier.clickable { PlayerController.clearUserQueue() },
                        )
                    }
                }
                itemsIndexed(userQueue, key = { _, t -> "q-${t.id}" }) { index, t ->
                    QueueRow(
                        title = t.title,
                        artist = t.artist,
                        color1 = t.coverColor1,
                        color2 = t.coverColor2,
                        onRemove = { PlayerController.removeFromQueue(index) },
                    )
                }
            }

            if (upcoming.isNotEmpty()) {
                item {
                    SectionHeader(
                        if (contextName.isBlank()) stringResource(R.string.queue_next_caps)
                        else stringResource(R.string.queue_up_next_named_caps, contextName)
                    )
                }
                items(upcoming, key = { "u-${it.id}" }) { t ->
                    QueueRow(
                        title = t.title,
                        artist = t.artist,
                        color1 = t.coverColor1,
                        color2 = t.coverColor2,
                        onRemove = null,
                    )
                }
            }

            item { Box(Modifier.size(dimens.spacingLg)) }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    descriptionRes: Int,
    active: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit,
) {
    val colors = LumenTheme.colors
    Icon(
        icon,
        contentDescription = stringResource(descriptionRes),
        tint = if (active) colors.accent else colors.muted,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(7.dp),
    )
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    color1: String,
    color2: String,
    onRemove: (() -> Unit)?,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight)
            .padding(vertical = dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        TrackCover(color1, color2, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = LumenText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = LumenText.bodySm, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.queue_remove_from),
                tint = colors.muted,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(8.dp),
            )
        }
    }
}
