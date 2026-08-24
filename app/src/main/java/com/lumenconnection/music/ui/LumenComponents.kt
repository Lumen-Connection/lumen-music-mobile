package com.lumenconnection.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import kotlin.math.max

/** Converte a cor guardada no banco ("#e8a44a") para [Color]. */
fun parseHexColor(hex: String, fallback: Color = Color(0xFF808080)): Color =
    runCatching {
        val clean = hex.removePrefix("#")
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)

/**
 * Gradiente diagonal do canto superior esquerdo ao inferior direito — o mesmo
 * `qlineargradient(x1:0,y1:0,x2:1,y2:1)` que o desktop usa nas capas.
 */
fun gradientBrush(c1: String, c2: String): Brush =
    Brush.linearGradient(listOf(parseHexColor(c1), parseHexColor(c2)))

/**
 * Capa quadrada seguindo a hierarquia do desktop (`src/widgets/coverwidget.h`):
 * imagem > mosaico 2×2 dos gradientes das faixas (a partir de 4) > gradiente da
 * playlist.
 */
@Composable
fun PlaylistCover(
    coverImagePath: String,
    color1: String,
    color2: String,
    trackColors: List<Pair<String, String>> = emptyList(),
    size: Dp,
    radius: Dp = LumenTheme.dimens.radiusCard,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.size(size).clip(shape)) {
        when {
            coverImagePath.isNotBlank() -> Image(
                painter = rememberAsyncImagePainter(coverImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            trackColors.size >= 4 -> {
                val cellRadius = max(2f, radius.value / 2f).dp
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    repeat(2) { row ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            repeat(2) { col ->
                                val (a, b) = trackColors[row * 2 + col]
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(cellRadius))
                                        .background(gradientBrush(a, b))
                                )
                            }
                        }
                    }
                }
            }

            else -> Box(Modifier.fillMaxSize().background(gradientBrush(color1, color2)))
        }
    }
}

/** Capa de uma faixa: sempre o gradiente próprio dela, com a nota como marca d'água. */
@Composable
fun TrackCover(color1: String, color2: String, size: Dp, radius: Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(gradientBrush(color1, color2)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * Linha de faixa com as mesmas zonas de toque do `TrackRowDelegate` do desktop:
 * tocar, curtir e menu de contexto.
 */
@Composable
fun TrackRow(
    title: String,
    artist: String,
    durationMs: Long,
    color1: String,
    color2: String,
    liked: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight)
            .clip(RoundedCornerShape(dimens.radiusWidget))
            .background(if (isPlaying) colors.cardHover else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackCover(color1, color2, size = dimens.rowHeight - 14.dp)
            if (isPlaying) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = LumenText.body.copy(color = if (isPlaying) colors.accent else colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(artist, style = LumenText.bodySm, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (durationMs > 0) {
            Text(formatDuration(durationMs), style = LumenText.bodySm)
        }

        Icon(
            imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = null,
            tint = if (liked) colors.accent else colors.muted,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(dimens.radiusWidget))
                .clickable(onClick = onToggleLike)
                .padding(8.dp),
        )

        Icon(
            Icons.Default.MoreVert,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(dimens.radiusWidget))
                .clickable(onClick = onMenu)
                .padding(8.dp),
        )
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** Rótulo de seção em maiúsculas — equivalente do `lumenMicroLabel` do desktop. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(vertical = LumenTheme.dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text.uppercase(), style = LumenText.micro)
        trailing?.invoke()
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = LumenTheme.dimens.spacing)) {
        Text(title, style = LumenText.title)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = LumenText.bodySm, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    val dimens = LumenTheme.dimens
    Column(
        modifier.fillMaxWidth().padding(dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Text(title, style = LumenText.body, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = LumenText.bodySm, textAlign = TextAlign.Center)
        }
        action?.invoke()
    }
}

@Composable
fun LumenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(dimens.borderWidth, colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(dimens.spacing),
        content = content,
    )
}

/** Botão preenchido com o accent — equivalente do `lumenAccentBtn`. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Text(
        text = text,
        style = LumenText.body.copy(color = colors.onAccent),
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusWidget))
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.btnPadH, vertical = dimens.btnPadV),
    )
}

/** Botão de contorno — equivalente do `lumenGhostBtn`. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusWidget)
    Text(
        text = text,
        style = LumenText.body,
        modifier = modifier
            .clip(shape)
            .border(dimens.borderWidth, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.btnPadH, vertical = dimens.btnPadV),
    )
}

/** Divisória fina, na cor de borda do tema. */
@Composable
fun LumenDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(LumenTheme.dimens.borderWidth)
            .background(LumenTheme.colors.border)
    )
}
