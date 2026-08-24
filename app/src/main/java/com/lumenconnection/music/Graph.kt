package com.lumenconnection.music

import android.content.Context
import com.lumenconnection.music.config.SettingsRepository
import com.lumenconnection.music.db.AppDatabase
import com.lumenconnection.music.db.LibraryRepository

/**
 * Service locator do app — mesma escolha do lumen-stream-mobile: sem framework de
 * DI, espelhando a simplicidade do desktop (que usa singletons como
 * `Database::instance()` e `ThemeManager`).
 *
 * Inicializado em [LumenApp.onCreate]; tudo o mais lê daqui.
 */
object Graph {
    lateinit var settings: SettingsRepository
        private set

    lateinit var db: AppDatabase
        private set

    lateinit var library: LibraryRepository
        private set

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        settings = SettingsRepository(app)
        db = AppDatabase.build(app)
        library = LibraryRepository(db)
        initialized = true
    }
}
