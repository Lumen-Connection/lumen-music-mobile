package com.lumenconnection.music.db

import androidx.room.TypeConverter

/**
 * As listas de ids são guardadas como texto separado por vírgula, igual ao
 * desktop (`context_ids`/`user_queue_ids` em `playback_state`) — assim o mesmo
 * formato serve aos dois lados sem tradução.
 */
class Converters {

    @TypeConverter
    fun longListToString(value: List<Long>?): String =
        value?.joinToString(",") ?: ""

    @TypeConverter
    fun stringToLongList(value: String?): List<Long> =
        if (value.isNullOrBlank()) emptyList()
        else value.split(',').mapNotNull { it.trim().toLongOrNull() }

    @TypeConverter
    fun originToString(value: Origin): String = value.name

    @TypeConverter
    fun stringToOrigin(value: String): Origin =
        runCatching { Origin.valueOf(value) }.getOrDefault(Origin.LOCAL)

    @TypeConverter
    fun downloadStateToString(value: DownloadState): String = value.name

    @TypeConverter
    fun stringToDownloadState(value: String): DownloadState =
        runCatching { DownloadState.valueOf(value) }.getOrDefault(DownloadState.NONE)

    @TypeConverter
    fun sortModeToString(value: SortMode): String = value.name

    @TypeConverter
    fun stringToSortMode(value: String): SortMode =
        runCatching { SortMode.valueOf(value) }.getOrDefault(SortMode.CUSTOM)

    @TypeConverter
    fun repeatModeToInt(value: RepeatMode): Int = value.ordinal

    @TypeConverter
    fun intToRepeatMode(value: Int): RepeatMode =
        RepeatMode.entries.getOrElse(value) { RepeatMode.OFF }
}
