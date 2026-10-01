package co.tuvida.app

import co.tuvida.app.data.*
import co.tuvida.app.domain.Music
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MusicTest {
    private val a = Song("content://media/external/audio/media/1", "Árbol", "Música", "Disco", 180000)
    private val b = Song("content://media/external/audio/media/2", "Brisa", "Otro", "Álbum", 240000)
    @Test fun oldBackupGetsEmptyMusicWithoutChangingOtherData() {
        val root = JsonParser.parseString(Backup.encode(AppData(tasks = listOf(Task(title = "Leer"))))).asJsonObject
        root.remove("music")
        val restored = Backup.decode(root.toString())
        assertEquals(MusicLibrary(), restored.music); assertEquals("Leer", restored.tasks.single().title)
    }
    @Test fun copyPreservesListsFavoritesAndPausedResumePosition() {
        val library = MusicLibrary(listOf(a, b), setOf(a.uri), listOf(MusicPlaylist(name = "Viaje", songs = listOf(b.uri, a.uri))), listOf(a.uri, b.uri), a.uri, 45000, 1, true)
        assertEquals(library, Backup.decode(Backup.encode(AppData(music = library))).music)
    }
    @Test fun duplicateImportUpdatesMetadataWithoutDuplicatingListReferences() {
        val original = MusicLibrary(listOf(a), setOf(a.uri), listOf(MusicPlaylist(name = "Favoritas", songs = listOf(a.uri))))
        val merged = Music.merge(original, listOf(a.copy(title = "Título actualizado"), b))
        assertEquals(2, merged.songs.size); assertEquals("Título actualizado", merged.songs.first().title)
        assertEquals(original.favorites, merged.favorites); Music.validate(merged)
    }
    @Test fun removingCurrentSongCleansReferencesAndResumePosition() {
        val m = MusicLibrary(listOf(a, b), setOf(a.uri), listOf(MusicPlaylist(name = "Lista", songs = listOf(a.uri, b.uri))), listOf(a.uri, b.uri), a.uri, 1234)
        val removed = Music.remove(m, a.uri)
        assertEquals(listOf(b), removed.songs); assertTrue(removed.favorites.isEmpty()); assertEquals(listOf(b.uri), removed.queue)
        assertEquals(listOf(b.uri), removed.playlists.single().songs); assertEquals("", removed.current); assertEquals(0, removed.position); Music.validate(removed)
    }
    @Test fun playlistAdditionIsIdempotentAndPreservesOrder() {
        val m = MusicLibrary(listOf(a, b), playlists = listOf(MusicPlaylist("p", "Mi lista", listOf(b.uri))))
        val next = Music.addToPlaylist(Music.addToPlaylist(m, "p", a.uri), "p", a.uri)
        assertEquals(listOf(b.uri, a.uri), next.playlists.single().songs)
        assertEquals(next, Music.addToPlaylist(next, "p", "content://missing/audio"))
    }
    @Test fun reorderingRespectsBoundariesAndIdentity() {
        val ids = listOf(a.uri, b.uri)
        assertEquals(ids.reversed(), Music.move(ids, b.uri, -1)); assertEquals(ids, Music.move(ids, a.uri, -1)); assertEquals(ids, Music.move(ids, "missing", 1))
    }
    @Test fun searchIgnoresAccentsAndFindsArtistOrAlbum() {
        assertEquals(listOf(a), Music.search(listOf(a, b), "arbol", "title"))
        assertEquals(listOf(a), Music.search(listOf(a, b), "musica", "title"))
        assertEquals(listOf(b), Music.search(listOf(a, b), "album", "title"))
        assertEquals(listOf(b, a), Music.search(listOf(a, b), "", "duration"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsRemoteTracks() { Music.validate(MusicLibrary(songs = listOf(a.copy(uri = "https://example.com/song.mp3")))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDanglingPlaylistReferences() { Music.validate(MusicLibrary(playlists = listOf(MusicPlaylist(name = "Lista", songs = listOf(a.uri))))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidModes() { Music.validate(MusicLibrary(repeat = 3)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateQueue() { Music.validate(MusicLibrary(songs = listOf(a), queue = listOf(a.uri, a.uri))) }
    @Test(expected = NullPointerException::class) fun rejectsExplicitNullMusicWithoutOverwritingData() { Backup.decode(Backup.encode(AppData()).replace("\"music\":{", "\"music\":null,\"discardedMusic\":{")) }
}
