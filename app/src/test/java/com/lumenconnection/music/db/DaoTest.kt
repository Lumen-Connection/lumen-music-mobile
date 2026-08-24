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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var tracks: TrackDao
    private lateinit var playlists: PlaylistDao
    private lateinit var links: PlaylistTrackDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracks = db.trackDao()
        playlists = db.playlistDao()
        links = db.playlistTrackDao()
    }

    @After
    fun tearDown() = db.close()

    private fun track(title: String, artist: String = "Artista", added: Long = 0) =
        TrackEntity(
            title = title,
            artist = artist,
            addedAt = added,
            searchKey = TextUtils.searchKey(title, artist),
        )

    @Test
    fun `insere e observa faixas`() = runTest {
        tracks.insert(track("Uma Canção"))
        val all = tracks.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Uma Canção", all[0].title)
    }

    @Test
    fun `busca ignora acentos`() = runTest {
        tracks.insert(track("Coração Selvagem", "Belchior"))
        tracks.insert(track("Outra Coisa", "Fulano"))

        val hits = tracks.search(TextUtils.normalized("coracao")).first()
        assertEquals(1, hits.size)
        assertEquals("Coração Selvagem", hits[0].title)
    }

    @Test
    fun `curtir marca o registro como sujo para o sync`() = runTest {
        val id = tracks.insert(track("Faixa"))
        tracks.setLiked(id, liked = true, at = 1_700_000_000_000)

        val liked = tracks.observeLiked().first()
        assertEquals(1, liked.size)
        assertTrue(liked[0].likeDirty)

        assertEquals(1, tracks.dirtyLikes().size)
        tracks.clearLikeDirty(listOf(id))
        assertEquals(0, tracks.dirtyLikes().size)
    }

    @Test
    fun `registrar reproducao soma no total e no delta pendente`() = runTest {
        val id = tracks.insert(track("Faixa"))
        tracks.registerPlay(id, at = 1_700_000_000_000)
        tracks.registerPlay(id, at = 1_700_000_100_000)

        val t = tracks.byId(id)!!
        assertEquals(2, t.playCount)
        assertEquals(2, t.pendingPlayDelta)
        assertEquals(1_700_000_100_000, t.lastPlayedAt)

        tracks.clearPendingPlays(listOf(id))
        // O total é histórico e permanece; só o delta a enviar zera.
        assertEquals(2, tracks.byId(id)!!.playCount)
        assertEquals(0, tracks.byId(id)!!.pendingPlayDelta)
    }

    @Test
    fun `playlist mantem a ordem manual pelos vaos de 1024`() = runTest {
        val pid = playlists.insert(PlaylistEntity(name = "Rock", searchName = TextUtils.normalized("Rock")))
        val a = tracks.insert(track("A"))
        val b = tracks.insert(track("B"))
        val c = tracks.insert(track("C"))

        listOf(a, b, c).forEachIndexed { i, tid ->
            links.insert(PlaylistTrackEntity(pid, tid, PositionGap.fromRank(i)))
        }

        assertEquals(listOf(a, b, c), links.orderedTrackIds(pid))
        assertEquals(3072L, playlists.maxPosition(pid))

        // Move C para o meio, entre A e B — sem tocar nos vizinhos.
        val mid = PositionGap.between(PositionGap.fromRank(0), PositionGap.fromRank(1))
        links.setPosition(pid, c, mid)
        assertEquals(listOf(a, c, b), links.orderedTrackIds(pid))
    }

    @Test
    fun `renormalise redistribui as posicoes preservando a ordem`() = runTest {
        val pid = playlists.insert(PlaylistEntity(name = "Lista", searchName = "lista"))
        val ids = (1..4).map { tracks.insert(track("Faixa $it")) }
        ids.forEach { links.insert(PlaylistTrackEntity(pid, it, 1)) }

        val desired = ids.reversed()
        links.renormalise(pid, desired)

        assertEquals(desired, links.orderedTrackIds(pid))
        assertEquals(4096L, playlists.maxPosition(pid))
    }

    @Test
    fun `apagar playlist nao apaga as faixas`() = runTest {
        val pid = playlists.insert(PlaylistEntity(name = "Temp", searchName = "temp"))
        val tid = tracks.insert(track("Sobrevivente"))
        links.insert(PlaylistTrackEntity(pid, tid, PositionGap.GAP))

        playlists.deleteById(pid)

        // A faixa continua na biblioteca, agora como avulsa — regra do desktop.
        assertNotNull(tracks.byId(tid))
        assertEquals(emptyList<Long>(), links.orderedTrackIds(pid))
        assertEquals(1, tracks.observeStandalone().first().size)
    }

    @Test
    fun `apagar faixa remove o vinculo com a playlist`() = runTest {
        val pid = playlists.insert(PlaylistEntity(name = "Lista", searchName = "lista"))
        val tid = tracks.insert(track("Efêmera"))
        links.insert(PlaylistTrackEntity(pid, tid, PositionGap.GAP))

        tracks.deleteById(tid)

        assertNull(tracks.byId(tid))
        assertEquals(emptyList<Long>(), links.orderedTrackIds(pid))
    }

    @Test
    fun `uma faixa vive em varias playlists sem duplicar`() = runTest {
        val rock = playlists.insert(PlaylistEntity(name = "Rock", searchName = "rock"))
        val favs = playlists.insert(PlaylistEntity(name = "Favoritas", searchName = "favoritas"))
        val tid = tracks.insert(track("Compartilhada"))

        links.insert(PlaylistTrackEntity(rock, tid, PositionGap.GAP))
        links.insert(PlaylistTrackEntity(favs, tid, PositionGap.GAP))

        assertEquals(setOf(rock, favs), links.playlistsOf(tid).toSet())
        assertEquals(1, tracks.observeAll().first().size)
    }

    @Test
    fun `estado de reproducao guarda as duas filas`() = runTest {
        val dao = db.playbackStateDao()
        dao.save(
            PlaybackStateEntity(
                currentTrackId = 7,
                positionMs = 63_000,
                shuffle = true,
                repeatMode = RepeatMode.ALL,
                contextIds = listOf(7, 8, 9),
                userQueueIds = listOf(42),
                contextIndex = 0,
                contextName = "Rock",
            )
        )

        val restored = dao.get()!!
        assertEquals(7L, restored.currentTrackId)
        assertEquals(63_000L, restored.positionMs)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertEquals(listOf(7L, 8L, 9L), restored.contextIds)
        assertEquals(listOf(42L), restored.userQueueIds)
        assertEquals("Rock", restored.contextName)
    }

    @Test
    fun `faixas sincronizadas sao distinguiveis das locais`() = runTest {
        tracks.insert(track("Local"))
        tracks.insert(track("Do PC").copy(remoteId = 41, origin = Origin.SYNC))

        assertEquals(1, tracks.allSynced().size)
        assertEquals("Do PC", tracks.allSynced()[0].title)
        assertNotNull(tracks.byRemoteId(41))
    }
}
