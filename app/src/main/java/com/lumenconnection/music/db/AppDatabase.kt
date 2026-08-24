package com.lumenconnection.music.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Banco local do Lumen Music Mobile.
 *
 * O desktop mantém um migrador próprio com backup e `foreign_key_check` a cada
 * passo (`src/database/migrator.cpp`); aqui o Room cuida disso. Enquanto o
 * schema não sair da versão 1 não há migração a escrever — quando sair, migração
 * explícita, nunca `fallbackToDestructiveMigration`: a biblioteca do usuário
 * inclui faixas locais que não vêm de nenhum sync e não podem ser recriadas.
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lumen_music.db")
                // O desktop liga foreign_keys=ON; o Room faz o mesmo por padrão,
                // mas a cascata de playlist_tracks depende disso, então é explícito.
                .build()
    }
}
