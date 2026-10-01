package co.tuvida.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.*
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.data.*
import co.tuvida.app.domain.Music
import co.tuvida.app.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class MusicPlayback(
    val connected: Boolean = false, val current: String = "", val playing: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val queue: List<String> = emptyList(),
    val repeat: Int = 0, val shuffle: Boolean = false, val sleepRemaining: Long = 0, val error: String = ""
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TuVidaApplication
    val playback = MutableStateFlow(MusicPlayback())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    private var controller: MediaController? = null
    private var sleepDeadline = 0L
    private val executor = ContextCompat.getMainExecutor(app)
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { player ->
                controller = player
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { snapshot() }
                })
                sleepCommand(MusicService.SLEEP_STATUS)
                snapshot()
            }.onFailure { message.value = "No se pudo conectar el reproductor. Cierra y vuelve a abrir Tu Vida." }
        }, executor)
        viewModelScope.launch { while (isActive) { snapshot(); delay(500) } }
    }
    private fun snapshot() {
        val p = controller ?: return
        playback.value = MusicPlayback(true, p.currentMediaItem?.mediaId.orEmpty(), p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playerError == null,
            p.currentPosition.coerceAtLeast(0), p.duration.coerceAtLeast(0),
            (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }, p.repeatMode, p.shuffleModeEnabled,
            if (sleepDeadline == 0L) 0 else (sleepDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0),
            if (p.playerError != null) "No se pudo reproducir este archivo. Si lo moviste, eliminaste o restauraste una copia, vuelve a añadirlo; también puede tener un formato incompatible." else "")
    }
    private fun edit(action: (MusicLibrary) -> MusicLibrary) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.store.update { it.copy(music = action(it.music)) } }.onFailure { message.value = it.message ?: "No se pudo guardar tu biblioteca." }
        }
    }
    fun scan() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = AudioFiles.scan(app)
                app.store.update { it.copy(music = Music.merge(it.music, songs)) }
                message.value = if (songs.isEmpty()) "Android no encontró audios. Puedes elegir tus archivos con Añadir canciones." else "Se encontraron ${songs.size} audios en el teléfono."
            } catch (_: Exception) { message.value = "No se pudieron leer los audios. Revisa el permiso de música o usa Añadir canciones." }
            finally { busy.value = false }
        }
    }
    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var failed = 0
            val songs = uris.mapNotNull { uri ->
                runCatching {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    AudioFiles.read(app, uri)
                }.getOrElse { failed++; null }
            }
            try {
                app.store.update { it.copy(music = Music.merge(it.music, songs)) }
                message.value = "${songs.size} canciones añadidas.${if (failed > 0) " No se pudieron abrir $failed archivos; elige audios disponibles en el teléfono." else ""}"
            } catch (e: Exception) { message.value = e.message ?: "No se pudo guardar la biblioteca." }
            finally { busy.value = false }
        }
    }
    fun play(songs: List<Song>, uri: String = songs.firstOrNull()?.uri.orEmpty()) {
        val p = controller ?: return
        if (songs.isEmpty()) return
        p.setMediaItems(songs.distinctBy { it.uri }.map { it.mediaItem() }, songs.indexOfFirst { it.uri == uri }.coerceAtLeast(0), 0)
        p.prepare(); p.play()
    }
    fun toggle() { controller?.let { if (it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playerError == null) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); it.play() } } }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }
    fun seek(position: Long) { controller?.seekTo(position) }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun enqueue(song: Song, next: Boolean) {
        controller?.let { p ->
            if ((0 until p.mediaItemCount).any { p.getMediaItemAt(it).mediaId == song.uri }) { message.value = "Esta canción ya está en la cola."; return }
            if (p.mediaItemCount >= 10000) { message.value = "La cola admite hasta 10.000 canciones."; return }
            p.addMediaItem(if (next && p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else p.mediaItemCount, song.mediaItem())
        }
    }
    fun queuePlay(uri: String) { controller?.let { p -> val index = (0 until p.mediaItemCount).indexOfFirst { p.getMediaItemAt(it).mediaId == uri }; if (index >= 0) { p.seekToDefaultPosition(index); p.prepare(); p.play() } } }
    fun queueRemove(uri: String) { controller?.let { p -> val index = (0 until p.mediaItemCount).indexOfFirst { p.getMediaItemAt(it).mediaId == uri }; if (index >= 0) p.removeMediaItem(index) } }
    fun queueMove(uri: String, offset: Int) { controller?.let { p -> val index = (0 until p.mediaItemCount).indexOfFirst { p.getMediaItemAt(it).mediaId == uri }; if (index >= 0 && index + offset in 0 until p.mediaItemCount) p.moveMediaItem(index, index + offset) } }
    fun favorite(uri: String) = edit { it.copy(favorites = if (uri in it.favorites) it.favorites - uri else it.favorites + uri) }
    fun createPlaylist(name: String) = edit { it.copy(playlists = it.playlists + MusicPlaylist(name = name.trim())) }
    fun renamePlaylist(id: String, name: String) = edit { it.copy(playlists = it.playlists.map { p -> if (p.id == id) p.copy(name = name.trim()) else p }) }
    fun deletePlaylist(id: String) = edit { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }
    fun addToPlaylist(id: String, uri: String) = edit { Music.addToPlaylist(it, id, uri) }
    fun removeFromPlaylist(id: String, uri: String) = edit { it.copy(playlists = it.playlists.map { p -> if (p.id == id) p.copy(songs = p.songs - uri) else p }) }
    fun playlistMove(id: String, uri: String, offset: Int) = edit { it.copy(playlists = it.playlists.map { p -> if (p.id == id) p.copy(songs = Music.move(p.songs, uri, offset)) else p }) }
    fun removeSong(uri: String) { queueRemove(uri); edit { Music.remove(it, uri) } }
    fun sleep(minutes: Int) = sleepCommand(MusicService.SLEEP, Bundle().apply { putInt("minutes", minutes) })
    private fun sleepCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val result = controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args) ?: return
        result.addListener({ runCatching { result.get() }.onSuccess { if (it.resultCode == SessionResult.RESULT_SUCCESS) { sleepDeadline = it.extras.getLong("deadline"); snapshot() } } }, executor)
    }
    override fun onCleared() { MediaController.releaseFuture(future); super.onCleared() }
}
