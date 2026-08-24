package com.lumenconnection.music.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.Graph
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.player.PlayerController
import com.lumenconnection.music.ui.TrackRow
import kotlinx.coroutines.launch

/**
 * Linha de faixa já ligada ao player: tocar define a lista visível como contexto
 * de reprodução, exatamente como o desktop faz ao clicar numa faixa dentro de
 * uma playlist, das curtidas ou da biblioteca.
 *
 * O menu de contexto (enfileirar, adicionar a playlist, editar, excluir) entra
 * na fase 3.
 */
@Composable
fun PlayableTrackRow(
    track: TrackEntity,
    context: List<TrackEntity>,
    contextName: String,
) {
    val scope = rememberCoroutineScope()
    val current by PlayerController.currentTrack.collectAsStateWithLifecycle()

    TrackRow(
        title = track.title,
        artist = track.artist,
        durationMs = track.durationMs,
        color1 = track.coverColor1,
        color2 = track.coverColor2,
        liked = track.liked,
        isPlaying = current?.id == track.id,
        onClick = {
            PlayerController.playTrack(track, context.map { it.id }, contextName)
        },
        onToggleLike = {
            scope.launch {
                Graph.db.trackDao().setLiked(track.id, !track.liked, System.currentTimeMillis())
            }
        },
        onMenu = {
            // Provisório até a fase 3 trazer o menu completo: enfileirar é a
            // ação mais usada do menu do desktop.
            PlayerController.enqueue(track)
        },
    )
}
