package com.lumenconnection.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlinx.coroutines.launch

/**
 * Barra de reprodução fixa — equivalente do `src/player/playerbar.cpp`, que é
 * uma view pura sobre o engine. Aqui vale o mesmo: só lê os StateFlows do
 * [PlayerController].
 */
@Composable
fun PlayerBar(onExpand: () -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val scope = rememberCoroutineScope()

    val track by PlayerController.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val position by PlayerController.positionMs.collectAsStateWithLifecycle()
    val duration by PlayerController.durationMs.collectAsStateWithLifecycle()

    val current = track ?: return

    Column(Modifier.fillMaxWidth().background(colors.card)) {
        LumenDivider()

        // Progresso: uma faixa fina no topo da barra, como no desktop.
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.input)) {
            val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxSize()
                    .background(colors.accent)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = dimens.spacing, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            SpinningVinyl(
                color1 = current.coverColor1,
                color2 = current.coverColor2,
                spinning = isPlaying,
                size = 44.dp,
            )

            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    style = LumenText.body,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    current.artist,
                    style = LumenText.bodySm,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = if (current.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(
                    if (current.liked) R.string.action_unlike else R.string.action_like
                ),
                tint = if (current.liked) colors.accent else colors.muted,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch {
                            Graph.db.trackDao()
                                .setLiked(current.id, !current.liked, System.currentTimeMillis())
                        }
                    }
                    .padding(7.dp),
            )

            TransportButton(Icons.Default.SkipPrevious, R.string.player_previous) {
                PlayerController.prev()
            }
            TransportButton(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                R.string.player_play_pause,
                tint = colors.accent,
                size = 40.dp,
            ) { PlayerController.togglePlay() }
            TransportButton(Icons.Default.SkipNext, R.string.player_next) {
                PlayerController.next()
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    descriptionRes: Int,
    tint: Color? = null,
    size: Dp = 36.dp,
    onClick: () -> Unit,
) {
    val colors = LumenTheme.colors
    Icon(
        icon,
        contentDescription = stringResource(descriptionRes),
        tint = tint ?: colors.text,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    )
}

/**
 * Vinil girando — o mesmo enfeite do `src/widgets/vinylwidget.cpp`. Respeita
 * reduce-motion: com a opção ligada o disco fica parado, como o `Motion::d()`
 * do desktop zera as animações.
 */
@Composable
fun SpinningVinyl(color1: String, color2: String, spinning: Boolean, size: Dp) {
    val colors = LumenTheme.colors
    val animate = LumenTheme.motion.shouldAnimate() && spinning

    val rotation = if (animate) {
        val transition = rememberInfiniteTransition(label = "vinyl")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = AnimRepeatMode.Restart,
            ),
            label = "vinylRotation",
        ).value
    } else {
        0f
    }

    Box(
        Modifier
            .size(size)
            .rotate(rotation)
            .clip(CircleShape)
            .background(gradientBrush(color1, color2)),
        contentAlignment = Alignment.Center,
    ) {
        // Furo central do disco, na cor "vinyl" da paleta.
        Box(
            Modifier
                .size(size * 0.28f)
                .clip(CircleShape)
                .background(colors.vinyl)
        )
    }
}
