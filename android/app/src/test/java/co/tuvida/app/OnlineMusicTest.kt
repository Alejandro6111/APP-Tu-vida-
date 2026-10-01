package co.tuvida.app

import co.tuvida.app.domain.OnlineMusic
import org.junit.Assert.*
import org.junit.Test

class OnlineMusicTest {
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
