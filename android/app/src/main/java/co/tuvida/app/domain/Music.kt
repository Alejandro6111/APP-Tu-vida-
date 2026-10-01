package co.tuvida.app.domain

import co.tuvida.app.data.*
import java.net.URI
import java.text.Normalizer
import java.util.Locale

object Music {
    fun validate(m: MusicLibrary) {
        require(m.songs.size <= 10000 && m.playlists.size <= 200 && m.queue.size <= 10000)
        require(m.position >= 0 && m.repeat in 0..2)
        val ids = m.songs.map { it.uri }.toSet()
        require(ids.size == m.songs.size)
        m.songs.forEach {
            val uri = URI(it.uri)
            require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) { "Las canciones deben ser archivos locales elegidos en Android." }
            require(it.title.isNotBlank() && it.title.length <= 500 && it.artist.length <= 500 && it.album.length <= 500 && it.duration >= 0)
        }
        require(m.favorites.all { it in ids } && m.queue.all { it in ids } && m.queue.distinct().size == m.queue.size)
        require(m.current.isEmpty() || m.current in m.queue)
        require(m.playlists.map { it.id }.distinct().size == m.playlists.size)
        m.playlists.forEach {
            require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 100 && it.songs.size <= 10000)
            require(it.songs.all { uri -> uri in ids } && it.songs.distinct().size == it.songs.size)
        }
    }
    fun merge(m: MusicLibrary, songs: List<Song>): MusicLibrary = m.copy(songs = (m.songs + songs).associateBy { it.uri }.values.toList())
    fun remove(m: MusicLibrary, uri: String): MusicLibrary = m.copy(
        songs = m.songs.filterNot { it.uri == uri }, favorites = m.favorites - uri,
        playlists = m.playlists.map { it.copy(songs = it.songs - uri) }, queue = m.queue - uri,
        current = if (m.current == uri) "" else m.current, position = if (m.current == uri) 0 else m.position
    )
    fun addToPlaylist(m: MusicLibrary, id: String, uri: String): MusicLibrary = m.copy(playlists = m.playlists.map {
        if (it.id == id && m.songs.any { song -> song.uri == uri }) it.copy(songs = (it.songs + uri).distinct()) else it
    })
    fun move(ids: List<String>, uri: String, offset: Int): List<String> {
        val index = ids.indexOf(uri); val target = index + offset
        if (index < 0 || target !in ids.indices) return ids
        return ids.toMutableList().apply { add(target, removeAt(index)) }
    }
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
    fun search(songs: List<Song>, query: String, sort: String): List<Song> {
        val term = normalized(query.trim())
        val filtered = songs.filter { term in normalized("${it.title} ${it.artist} ${it.album}") }
        return when (sort) {
            "artist" -> filtered.sortedWith(compareBy({ normalized(it.artist) }, { normalized(it.title) }))
            "duration" -> filtered.sortedByDescending { it.duration }
            "added" -> filtered.asReversed()
            else -> filtered.sortedBy { normalized(it.title) }
        }
    }
    fun time(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60) }
}
