package com.lumenconnection.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.db.SortMode
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.ui.EmptyState
import com.lumenconnection.music.ui.LumenTextField
import com.lumenconnection.music.ui.PlaylistCover
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import com.lumenconnection.music.util.TextUtils
import kotlinx.coroutines.launch

/**
 * Detalhe da playlist — equivalente do `src/pages/folderdetailpage.cpp`: capa,
 * os 6 modos de ordenação persistidos por playlist, filtro de texto e
 * arrastar-para-reordenar com os vãos de 1024.
 */
@Composable
fun FolderDetailScreen(nav: NavHostController, folderId: Long) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val db = Graph.db
    val library = Graph.library
    val scope = rememberCoroutineScope()

    val playlist by db.playlistDao().observeById(folderId).collectAsStateWithLifecycle(null)
    val sortMode = playlist?.sortMode ?: SortMode.CUSTOM

    val allTracks by remember(folderId, sortMode) {
        val dao = db.playlistTrackDao()
        when (sortMode) {
            SortMode.CUSTOM -> dao.observeCustom(folderId)
            SortMode.TITLE -> dao.observeByTitle(folderId)
            SortMode.ARTIST -> dao.observeByArtist(folderId)
            SortMode.RECENT -> dao.observeByRecent(folderId)
            SortMode.OLDEST -> dao.observeByOldest(folderId)
            SortMode.DURATION -> dao.observeByDuration(folderId)
        }
    }.collectAsStateWithLifecycle(emptyList())

    val mosaic by remember(folderId) {
        db.playlistTrackDao().observeMosaicColors(folderId)
    }.collectAsStateWithLifecycle(emptyList())

    var filter by remember { mutableStateOf("") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Filtro sem acento, mesma regra da busca global.
    val tracks = remember(allTracks, filter) {
        if (filter.isBlank()) allTracks
        else {
            val needle = TextUtils.normalized(filter.trim())
            allTracks.filter { it.searchKey.contains(needle) }
        }
    }

    val current = playlist ?: run {
        Box(Modifier.fillMaxSize().background(colors.app))
        return
    }

    // Arrastar só faz sentido na ordenação personalizada e sem filtro ativo —
    // reordenar uma lista filtrada não teria destino definido.
    val reorderable = sortMode == SortMode.CUSTOM && filter.isBlank()
    val rowHeightPx = with(LocalDensity.current) { (dimens.rowHeight + dimens.spacingSm).toPx() }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.app),
        contentPadding = PaddingValues(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = dimens.spacing),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(
                    coverImagePath = current.coverImagePath,
                    color1 = current.coverColor1,
                    color2 = current.coverColor2,
                    trackColors = mosaic.map { it.coverColor1 to it.coverColor2 },
                    size = 108.dp,
                )
                Column {
                    Text(stringResource(R.string.playlist_caps), style = LumenText.micro)
                    Text(current.name, style = LumenText.title)
                    Text(
                        pluralStringResource(R.plurals.track_count, allTracks.size, allTracks.size),
                        style = LumenText.bodySm,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = dimens.spacingSm),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    LumenTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = stringResource(R.string.search_in_playlist),
                    )
                }
                Box {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.action_sort),
                        tint = colors.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(dimens.radiusWidget))
                            .clickable { sortMenuOpen = true }
                            .padding(8.dp),
                    )
                    SortMenu(
                        expanded = sortMenuOpen,
                        selected = sortMode,
                        onDismiss = { sortMenuOpen = false },
                        onSelect = { mode ->
                            scope.launch { db.playlistDao().setSortMode(folderId, mode) }
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            item { EmptyState(stringResource(R.string.home_start_listening)) }
        } else {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val isDragging = index == draggingIndex
                PlayableTrackRow(
                    track = track,
                    context = tracks,
                    contextName = current.name,
                    inPlaylistId = folderId,
                    modifier = Modifier
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffset
                                // Levanta o item arrastado acima dos vizinhos.
                                shadowElevation = 8f
                                alpha = 0.94f
                            }
                        }
                        .then(
                            if (!reorderable) Modifier
                            else Modifier.pointerInput(track.id, tracks.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                    onDragEnd = {
                                        val moved = Math.round(dragOffset / rowHeightPx)
                                        val target = (index + moved).coerceIn(0, tracks.size - 1)
                                        if (target != index) {
                                            scope.launch {
                                                library.moveTrack(folderId, track.id, target)
                                            }
                                        }
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            }
                        ),
                )
            }
        }
    }
}

/** Os 6 modos do desktop, na mesma ordem do menu de `folderdetailpage.cpp`. */
@Composable
private fun SortMenu(
    expanded: Boolean,
    selected: SortMode,
    onDismiss: () -> Unit,
    onSelect: (SortMode) -> Unit,
) {
    val colors = LumenTheme.colors
    val options = listOf(
        SortMode.CUSTOM to R.string.sort_custom,
        SortMode.TITLE to R.string.sort_title,
        SortMode.ARTIST to R.string.sort_artist,
        SortMode.RECENT to R.string.sort_recent,
        SortMode.OLDEST to R.string.sort_oldest,
        SortMode.DURATION to R.string.sort_duration,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { (mode, labelRes) ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(labelRes),
                        style = LumenText.body.copy(
                            color = if (mode == selected) colors.accent else colors.text,
                        ),
                    )
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}
