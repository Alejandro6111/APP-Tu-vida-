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
    @Test fun foldersDistinguishFullPathsAndStorageVolumes() {
        val songs = listOf(a.copy(folder = "external_primary:Music/Rock"), b.copy(folder = "1234-abcd:Music/Rock"), a.copy(uri = "content://media/external/audio/media/3", folder = "external_primary:Download/Rock"), b.copy(uri = "content://media/external/audio/media/4"))
        val folders = Music.folders(songs)
        assertEquals(4, folders.size)
        assertEquals("", folders.last().path)
        assertEquals("Rock", Music.folderLabel(folders.first().path))
        assertEquals("Carpeta no disponible", Music.folderLabel(""))
    }
    @Test fun exclusionRemovesWholeFolderAndCleansReferencesWithoutTouchingOthers() {
        val whatsapp = "external_primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"
        val voice = a.copy(folder = whatsapp); val music = b.copy(folder = "external_primary:Music")
        val m = MusicLibrary(listOf(voice, music), setOf(voice.uri, music.uri), listOf(MusicPlaylist(name = "Mezcla", songs = listOf(voice.uri, music.uri))), listOf(voice.uri, music.uri), voice.uri, 5000)
        val excluded = Music.excludeFolder(m, whatsapp)
        assertEquals(listOf(music), excluded.songs); assertEquals(setOf(music.uri), excluded.favorites)
        assertEquals(listOf(music.uri), excluded.playlists.single().songs); assertEquals(listOf(music.uri), excluded.queue)
        assertEquals("", excluded.current); assertEquals(0L, excluded.position)
        assertEquals(setOf(whatsapp), excluded.excludedFolders); Music.validate(excluded)
        assertEquals(excluded, Music.merge(excluded, listOf(voice, music)))
        assertEquals(2, Music.merge(excluded.copy(excludedFolders = emptySet()), listOf(voice, music)).songs.size)
    }
    @Test fun repeatedScanCannotBypassExclusionWhenOldReferencesGainFolderMetadata() {
        val excluded = MusicLibrary(songs = listOf(a), favorites = setOf(a.uri), queue = listOf(a.uri), current = a.uri, position = 123, excludedFolders = setOf("external_primary:Voice"))
        val merged = Music.merge(excluded, listOf(a.copy(folder = "external_primary:Voice")))
        assertTrue(merged.songs.isEmpty()); assertTrue(merged.queue.isEmpty()); assertTrue(merged.favorites.isEmpty())
        Music.validate(merged)
    }
    @Test fun oldMusicBackupsDefaultFoldersAndNewCopiesPreserveExclusions() {
        val m = MusicLibrary(songs = listOf(a.copy(folder = "external_primary:Music")), excludedFolders = setOf("external_primary:Voice"))
        assertEquals(m, Backup.decode(Backup.encode(AppData(music = m))).music)
        val root = JsonParser.parseString(Backup.encode(AppData(music = MusicLibrary(songs = listOf(a))))).asJsonObject
        root.getAsJsonObject("music").remove("excludedFolders")
        root.getAsJsonObject("music").getAsJsonArray("songs")[0].asJsonObject.remove("folder")
        val restored = Backup.decode(root.toString()).music
        assertEquals("", restored.songs.single().folder); assertTrue(restored.excludedFolders.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsExcludingUnknownLocations() { Music.excludeFolder(MusicLibrary(songs = listOf(a)), "") }
    @Test(expected = IllegalArgumentException::class) fun rejectsLibraryContainingExcludedSongs() { Music.validate(MusicLibrary(songs = listOf(a.copy(folder = "external_primary:Voice")), excludedFolders = setOf("external_primary:Voice"))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFolderMetadata() { Music.validate(MusicLibrary(songs = listOf(a.copy(folder = "x".repeat(2001))))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsRemoteTracks() { Music.validate(MusicLibrary(songs = listOf(a.copy(uri = "https://example.com/song.mp3")))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDanglingPlaylistReferences() { Music.validate(MusicLibrary(playlists = listOf(MusicPlaylist(name = "Lista", songs = listOf(a.uri))))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidModes() { Music.validate(MusicLibrary(repeat = 3)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateQueue() { Music.validate(MusicLibrary(songs = listOf(a), queue = listOf(a.uri, a.uri))) }
    @Test(expected = NullPointerException::class) fun rejectsExplicitNullMusicWithoutOverwritingData() { Backup.decode(Backup.encode(AppData()).replace("\"music\":{", "\"music\":null,\"discardedMusic\":{")) }
}
