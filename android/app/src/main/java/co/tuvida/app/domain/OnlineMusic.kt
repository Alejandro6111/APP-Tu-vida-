package co.tuvida.app.domain

import com.google.gson.JsonParser
import co.tuvida.app.data.MusicLibrary
import co.tuvida.app.data.Song
import java.net.URI

data class MusicResult(val id: String, val title: String, val artist: String, val duration: Long) {
    val url get() = "https://www.youtube.com/watch?v=$id"
}

/** Only canonical video IDs reach download requests or filenames; queries remain single arguments. */
object OnlineMusic {
    val audioExtensions = setOf("opus", "m4a", "ogg", "flac", "wav", "aac", "mp3")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    fun validId(id: String) = videoId.matches(id)
    fun downloadId(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "content" || uri.authority != "co.tuvida.app.musicfiles" || uri.query != null || uri.fragment != null) return null
        val name = uri.path?.removePrefix("/downloads/") ?: return null
        val id = name.substringBeforeLast('.', "")
        return id.takeIf { validId(it) && name.substringAfterLast('.') in audioExtensions && uri.path == "/downloads/$name" }
    }
    /** A recovered download may have a new extension; keep all library references together. */
    fun mergeDownload(library: MusicLibrary, video: String, song: Song): MusicLibrary {
        require(validId(video) && downloadId(song.uri) == video)
        val previous = library.songs.filter { downloadId(it.uri) == video }.map { it.uri }.toSet()
        fun reference(uri: String) = if (uri in previous) song.uri else uri
        return Music.merge(library.copy(
            songs = library.songs.map { if (it.uri in previous) song else it }.distinctBy { it.uri },
            favorites = library.favorites.map(::reference).toSet(),
            playlists = library.playlists.map { it.copy(songs = it.songs.map(::reference).distinct()) },
            queue = library.queue.map(::reference).distinct(),
            current = reference(library.current)
        ), listOf(song))
    }
    fun input(value: String): String {
        val text = value.trim()
        require(text.isNotBlank() && text.length <= 200 && text.none { it.isISOControl() }) { "Escribe una canción, artista o enlace de YouTube (hasta 200 caracteres)." }
        if (!text.contains("://")) return "ytsearch20:$text"
        val uri = runCatching { URI(text) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.userInfo == null && uri.port == -1) { "Usa un enlace HTTPS de YouTube." }
        val id = when (uri.host?.lowercase()) {
            "youtu.be" -> uri.path.trim('/').takeIf { !it.contains('/') }
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/") -> uri.path.substringAfterLast('/')
                else -> null
            }
            else -> null
        }
        require(id != null && validId(id)) { "Pega el enlace de una canción o video de YouTube, no una lista." }
        return "https://www.youtube.com/watch?v=$id"
    }
    fun results(output: String): List<MusicResult> = output.lineSequence().mapNotNull { line ->
        runCatching {
            val item = JsonParser.parseString(line).asJsonObject
            fun string(key: String) = item.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val id = string("id")
            if (!validId(id) || string("title").isBlank() || string("live_status") == "is_live" || string("is_live") == "true") return@runCatching null
            MusicResult(id, string("title").take(500), string("artist").ifBlank { string("uploader").ifBlank { string("channel") } }.take(500),
                (item.get("duration")?.takeUnless { it.isJsonNull }?.asDouble?.times(1000)?.toLong() ?: 0).coerceAtLeast(0))
        }.getOrNull()
    }.distinctBy { it.id }.take(20).toList()

    fun error(error: Throwable): String {
        val detail = error.message.orEmpty().lowercase()
        return when {
            error is IllegalArgumentException -> error.message ?: "Revisa el texto de búsqueda."
            "space" in detail || "enospc" in detail -> "No hay espacio suficiente en el teléfono. Libera espacio y vuelve a descargar."
            "sign in" in detail || "private video" in detail || "not available" in detail || "unavailable" in detail -> "YouTube no permite acceder a este audio. Prueba otro resultado."
            "network" in detail || "resolve" in detail || "timed out" in detail || "connection" in detail -> "No se pudo conectar. Revisa Internet y vuelve a intentarlo."
            else -> "No se pudo obtener el audio. Prueba de nuevo o pulsa Actualizar motor; YouTube puede cambiar o restringir el acceso."
        }
    }
}
