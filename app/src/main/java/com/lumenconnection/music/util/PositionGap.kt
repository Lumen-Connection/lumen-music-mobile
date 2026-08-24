package com.lumenconnection.music.util

/**
 * Port de `src/database/position_gap.h` do desktop — a estratégia de ordenação
 * com vãos de 1024 usada nas playlists.
 *
 * Posições são múltiplos positivos de 1024 (1024, 2048, …). Zero nunca é válido.
 */
object PositionGap {
    const val GAP: Long = 1024

    /** Acrescenta depois do máximo atual (ou 1024 quando a playlist está vazia). */
    fun nextAfter(maxPosition: Long): Long =
        if (maxPosition <= 0) GAP else maxPosition + GAP

    /** Insere entre dois vizinhos. Devolve -1 quando o vão fechou (precisa renormalizar). */
    fun between(prev: Long, next: Long): Long =
        if (next - prev <= 1) -1 else (prev + next) / 2

    fun gapClosed(prev: Long, next: Long): Boolean = next - prev <= 1

    /** Posição 0 → 1024, posição 1 → 2048, … */
    fun fromRank(rank: Int): Long = (rank + 1).toLong() * GAP

    /** Renormalização completa de N itens: [(trackId, position), …]. */
    fun renormalise(orderedTrackIds: List<Long>): List<Pair<Long, Long>> =
        orderedTrackIds.mapIndexed { index, id -> id to fromRank(index) }
}
