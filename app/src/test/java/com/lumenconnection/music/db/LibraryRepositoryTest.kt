package com.lumenconnection.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lumenconnection.music.util.PositionGap
import com.lumenconnection.music.util.TextUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cobre as operações compostas do facade: CRUD de playlist, vínculos N:N e a
 * reordenação por vãos de 1024, incluindo o caso em que o vão fecha e a
 * playlist precisa ser renormalizada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LibraryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newTrack(title: String): Long =
        db.trackDao().insert(
            TrackEntity(title = title, artist = "Artista", searchKey = TextUtils.searchKey(title, "Artista"))
        )

    @Test
    fun `criar playlist gera dirName, clientKey e chave de busca`() = runTest {
        val id = repo.createPlaylist("Rock Nacional")
        val playlist = db.playlistDao().byId(id)!!

        assertEquals("Rock Nacional", playlist.name)
        assertEquals("Rock Nacional", playlist.dirName)
        assertEquals(TextUtils.normalized("Rock Nacional"), playlist.searchName)
        // clientKey dá idempotência ao push do sync (fase 6).
        assertNotNull(playlist.clientKey)
    }

    @Test
    fun `dirName sanitiza caracteres proibidos em nome de pasta`() = runTest {
        val id = repo.createPlaylist("Rock: o melhor / 2026")
        assertEquals("Rock_ o melhor _ 2026", db.playlistDao().byId(id)!!.dirName)
    }

    @Test
    fun `renomear preserva o dirName`() = runTest {
        val id = repo.createPlaylist("Antigo")
        repo.renamePlaylist(id, "Novo Nome")

        val playlist = db.playlistDao().byId(id)!!
        assertEquals("Novo Nome", playlist.name)
        // A pasta em disco é estável, como no desktop.
        assertEquals("Antigo", playlist.dirName)
        assertEquals(TextUtils.normalized("Novo Nome"), playlist.searchName)
    }

    @Test
    fun `adicionar faixas empilha em multiplos de 1024`() = runTest {
        val pid = repo.createPlaylist("Lista")
        val a = newTrack("A"); val b = newTrack("B"); val c = newTrack("C")

        repo.addTrackToPlaylist(pid, a)
        repo.addTrackToPlaylist(pid, b)
        repo.addTrackToPlaylist(pid, c)

        val positions = db.playlistTrackDao().orderedPositions(pid)
        assertEquals(listOf(1024L, 2048L, 3072L), positions.map { it.position })
        assertEquals(listOf(a, b, c), positions.map { it.trackId })
    }

    @Test
    fun `mover para o meio usa o vao sem tocar nos vizinhos`() = runTest {
        val pid = repo.createPlaylist("Lista")
        val ids = listOf("A", "B", "C", "D").map { newTrack(it) }
        ids.forEach { repo.addTrackToPlaylist(pid, it) }

        // Move D (índice 3) para o índice 1, entre A e B.
        repo.moveTrack(pid, ids[3], 1)

        assertEquals(
            listOf(ids[0], ids[3], ids[1], ids[2]),
            db.playlistTrackDao().orderedTrackIds(pid),
        )
        val positions = db.playlistTrackDao().orderedPositions(pid).associate { it.trackId to it.position }
        // A e B ficaram onde estavam; só D mudou.
        assertEquals(1024L, positions[ids[0]])
        assertEquals(2048L, positions[ids[1]])
        assertEquals(1536L, positions[ids[3]])
    }

    @Test
    fun `mover para o inicio coloca antes do primeiro`() = runTest {
        val pid = repo.createPlaylist("Lista")
        val ids = listOf("A", "B", "C").map { newTrack(it) }
        ids.forEach { repo.addTrackToPlaylist(pid, it) }

        repo.moveTrack(pid, ids[2], 0)

        assertEquals(listOf(ids[2], ids[0], ids[1]), db.playlistTrackDao().orderedTrackIds(pid))
    }

    @Test
    fun `mover para o fim empilha depois do maior`() = runTest {
        val pid = repo.createPlaylist("Lista")
        val ids = listOf("A", "B", "C").map { newTrack(it) }
        ids.forEach { repo.addTrackToPlaylist(pid, it) }

        repo.moveTrack(pid, ids[0], 2)

        assertEquals(listOf(ids[1], ids[2], ids[0]), db.playlistTrackDao().orderedTrackIds(pid))
        assertEquals(4096L, db.playlistDao().maxPosition(pid))
    }

    @Test
    fun `vao fechado dispara renormalizacao da playlist inteira`() = runTest {
        val pid = repo.createPlaylist("Lista")
        val a = newTrack("A"); val b = newTrack("B"); val c = newTrack("C")

        // Posições coladas de propósito: não há espaço entre A e B.
        db.playlistTrackDao().insert(PlaylistTrackEntity(pid, a, 100))
        db.playlistTrackDao().insert(PlaylistTrackEntity(pid, b, 101))
        db.playlistTrackDao().insert(PlaylistTrackEntity(pid, c, 500))

        repo.moveTrack(pid, c, 1)

        assertEquals(listOf(a, c, b), db.playlistTrackDao().orderedTrackIds(pid))
        // Depois da renormalização todas voltam a ser múltiplos de 1024.
        val positions = db.playlistTrackDao().orderedPositions(pid).map { it.position }
        assertEquals(listOf(1024L, 2048L, 3072L), positions)
    }

    @Test
    fun `excluir playlist mantem as faixas como avulsas`() = runTest {
        val pid = repo.createPlaylist("Temporaria")
        val tid = newTrack("Sobrevivente")
        repo.addTrackToPlaylist(pid, tid)

        repo.deletePlaylist(pid)

        assertNotNull(db.trackDao().byId(tid))
        assertEquals(1, db.trackDao().observeStandalone().first().size)
    }

    @Test
    fun `pertencimento a playlists é consultavel e reversivel`() = runTest {
        val rock = repo.createPlaylist("Rock")
        val pop = repo.createPlaylist("Pop")
        val tid = newTrack("Compartilhada")

        repo.addTrackToPlaylist(rock, tid)
        repo.addTrackToPlaylist(pop, tid)
        assertEquals(setOf(rock, pop), repo.playlistsOf(tid))

        repo.removeTrackFromPlaylist(pop, tid)
        assertEquals(setOf(rock), repo.playlistsOf(tid))
    }

    @Test
    fun `editar faixa atualiza a chave de busca`() = runTest {
        val tid = newTrack("Titulo Antigo")
        repo.editTrack(tid, "Coração", "Anitta")

        val track = db.trackDao().byId(tid)!!
        assertEquals("Coração", track.title)
        assertEquals("Anitta", track.artist)
        // A busca sem acento tem de encontrar o título novo.
        assertTrue(track.searchKey.contains(TextUtils.normalized("coracao")))
    }

    @Test
    fun `alternar curtida marca como pendente de envio`() = runTest {
        val tid = newTrack("Faixa")
        repo.toggleLike(tid)

        val track = db.trackDao().byId(tid)!!
        assertTrue(track.liked)
        assertTrue(track.likeDirty)

        repo.toggleLike(tid)
        assertTrue(!db.trackDao().byId(tid)!!.liked)
    }
}
