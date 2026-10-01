package co.tuvida.app

import co.tuvida.app.domain.OnlineMusic
import co.tuvida.app.domain.Music
import co.tuvida.app.data.*
import org.junit.Assert.*
import org.junit.Test

class OnlineMusicTest {
    @Test fun identifiesPrivateDownloadsAcrossOriginalAndLegacyFormats() {
        listOf("opus", "m4a", "ogg", "flac", "wav", "aac", "mp3").forEach { extension ->
            assertEquals("abcdefghijk", OnlineMusic.downloadId("content://co.tuvida.app.musicfiles/downloads/abcdefghijk.$extension"))
        }
        listOf("content://media/downloads/abcdefghijk.mp3", "https://co.tuvida.app.musicfiles/downloads/abcdefghijk.mp3",
            "content://co.tuvida.app.musicfiles/other/abcdefghijk.mp3", "content://co.tuvida.app.musicfiles/downloads/abcdefghijk.mp3.part",
            "content://co.tuvida.app.musicfiles/downloads/../abcdefghijk.mp3", "not a uri").forEach { assertNull(it, OnlineMusic.downloadId(it)) }
    }
    @Test fun recoveryInANewFormatPreservesFavoritesPlaylistsQueueAndPosition() {
        val old = Song("content://co.tuvida.app.musicfiles/downloads/abcdefghijk.mp3", "Canción")
        val other = Song("content://media/external/audio/media/1", "Otra")
        val original = MusicLibrary(songs = listOf(old, other), favorites = setOf(old.uri),
            playlists = listOf(MusicPlaylist(name = "Viaje", songs = listOf(other.uri, old.uri))),
            queue = listOf(old.uri, other.uri), current = old.uri, position = 12000, repeat = 1, shuffle = true)
        val recovered = old.copy(uri = old.uri.replace(".mp3", ".opus"), duration = 180000)
        val merged = OnlineMusic.mergeDownload(original, "abcdefghijk", recovered)
        assertEquals(listOf(recovered, other), merged.songs)
        assertEquals(setOf(recovered.uri), merged.favorites)
        assertEquals(listOf(other.uri, recovered.uri), merged.playlists.single().songs)
        assertEquals(listOf(recovered.uri, other.uri), merged.queue)
        assertEquals(recovered.uri, merged.current); assertEquals(12000L, merged.position)
        assertEquals(1, merged.repeat); assertTrue(merged.shuffle); Music.validate(merged)
        assertEquals(merged, OnlineMusic.mergeDownload(merged, "abcdefghijk", recovered))
    }
    @Test fun recoveryCollapsesOldAndNewReferencesWithoutDuplicates() {
        val old = Song("content://co.tuvida.app.musicfiles/downloads/abcdefghijk.mp3", "Vieja")
        val new = old.copy(uri = old.uri.replace(".mp3", ".m4a"), title = "Actualizada")
        val original = MusicLibrary(songs = listOf(old, new), favorites = setOf(old.uri, new.uri),
            playlists = listOf(MusicPlaylist(name = "Lista", songs = listOf(old.uri, new.uri))),
            queue = listOf(old.uri, new.uri), current = new.uri)
        val merged = OnlineMusic.mergeDownload(original, "abcdefghijk", new)
        assertEquals(listOf(new), merged.songs); assertEquals(listOf(new.uri), merged.queue)
        assertEquals(listOf(new.uri), merged.playlists.single().songs); assertEquals(setOf(new.uri), merged.favorites)
        Music.validate(merged)
    }
    @Test fun searchPreservesUserTextAsOneArgument() {
        assertEquals("ytsearch20:Música café --exec algo", OnlineMusic.input("  Música café --exec algo  "))
        listOf("", "a".repeat(201), "a\nb").forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { OnlineMusic.input(invalid) } }
    }
    @Test fun linksBecomeCanonicalSingleVideos() {
        listOf("https://youtu.be/abcdefghijk?t=3", "https://www.youtube.com/watch?v=abcdefghijk&list=example", "https://music.youtube.com/watch?v=abcdefghijk", "https://youtube.com/shorts/abcdefghijk").forEach {
            assertEquals("https://www.youtube.com/watch?v=abcdefghijk", OnlineMusic.input(it))
        }
    }
    @Test fun rejectsRemoteHostsPlaylistsCredentialsAndFilenameTraversal() {
        listOf("http://youtube.com/watch?v=abcdefghijk", "https://youtube.com.evil.test/watch?v=abcdefghijk", "https://youtube.com/playlist?list=abc", "https://evil.test/a", "https://user@youtube.com/watch?v=abcdefghijk", "https://youtube.com:443/watch?v=abcdefghijk", "https://youtu.be/../../abcd", "https://youtube.com/watch?v=abc").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { OnlineMusic.input(it) }
        }
        assertFalse(OnlineMusic.validId("../abcdefgh"))
    }
    @Test fun parsesFlatResultsAndSkipsMalformedLiveAndDuplicateEntries() {
        val output = """
            {"id":"abcdefghijk","title":"Café","uploader":"Canal","duration":12.5}
            {"id":"abcdefghijk","title":"Duplicado"}
            {"id":"ABCDEFGHIJK","title":"Directo","is_live":true}
            {"id":"12345678901","title":"Directo 2","live_status":"is_live"}
            {"id":"bad","title":"Inválido"}
            no es json
            {"id":"lmnopqrstuv","title":"Sin duración","artist":"Autor","duration":null}
        """.trimIndent()
        val results = OnlineMusic.results(output)
        assertEquals(2, results.size); assertEquals(12500L, results.first().duration)
        assertEquals("Canal", results.first().artist); assertEquals(0L, results.last().duration)
        assertEquals("Autor", results.last().artist)
    }
    @Test fun errorsDoNotExposeRawProviderOutputOrLocalPaths() {
        val raw = "ERROR internal /data/user/0/private.json token=secret"
        val message = OnlineMusic.error(Exception(raw))
        assertFalse(message.contains("secret")); assertFalse(message.contains("private.json"))
        assertTrue(OnlineMusic.error(Exception("Unable to resolve network")).contains("Internet"))
        assertTrue(OnlineMusic.error(Exception("No space left on device")).contains("espacio"))
    }
}
