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
import co.tuvida.app.domain.OnlineMusic
import co.tuvida.app.domain.MusicResult
import co.tuvida.app.platform.*
import androidx.work.*
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class MusicPlayback(
    val connected: Boolean = false, val current: String = "", val playing: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val queue: List<String> = emptyList(),
    val repeat: Int = 0, val shuffle: Boolean = false, val sleepRemaining: Long = 0, val error: String = ""
)

data class MusicDiscovery(val searching: Boolean = false, val updating: Boolean = false,
    val searched: Boolean = false, val results: List<MusicResult> = emptyList(), val error: String = "", val notice: String = "")
data class MusicDownload(val ready: Boolean = false, val busy: Boolean = false, val title: String = "", val percent: Int = 0,
    val waiting: Boolean = false, val uri: String = "", val error: String = "", val cancelled: Boolean = false)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TuVidaApplication
    val playback = MutableStateFlow(MusicPlayback())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val discovery = MutableStateFlow(MusicDiscovery())
    val download = MutableStateFlow(MusicDownload())
    private val work = WorkManager.getInstance(app)
    private var searchJob: Job? = null
    private var searchId = ""
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
        viewModelScope.launch {
            work.getWorkInfosForUniqueWorkFlow(MusicDownloadWorker.NAME).collect { history ->
                val latest = history.maxByOrNull { info -> info.tags.firstOrNull { it.startsWith("created:") }?.substringAfter(':')?.toLongOrNull() ?: 0 }
                download.value = if (latest == null) MusicDownload(ready = true) else MusicDownload(
                    ready = true, busy = !latest.state.isFinished,
                    title = latest.tags.firstOrNull { it.startsWith("title:") }?.substringAfter(':').orEmpty(),
                    percent = latest.progress.getInt("percent", 0),
                    waiting = latest.state == WorkInfo.State.ENQUEUED || latest.state == WorkInfo.State.BLOCKED,
                    uri = latest.outputData.getString("uri").orEmpty(),
                    error = latest.outputData.getString("error").orEmpty().ifBlank { if (latest.state == WorkInfo.State.FAILED) "Android no pudo completar la descarga. Vuelve a intentarlo." else "" },
                    cancelled = latest.state == WorkInfo.State.CANCELLED)
            }
        }
    }
    fun searchOnline(query: String) {
        if (discovery.value.searching || discovery.value.updating) return
        discovery.value = MusicDiscovery(searching = true)
        searchId = "search-${java.util.UUID.randomUUID()}"
        val process = searchId
        searchJob = viewModelScope.launch {
            try {
                val results = runInterruptible(Dispatchers.IO) { OnlineMusicEngine.search(app, query, process) }
                discovery.value = MusicDiscovery(searched = true, results = results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { discovery.value = MusicDiscovery(error = OnlineMusic.error(e)) }
            finally { discovery.value = discovery.value.copy(searching = false) }
        }
    }
    fun cancelSearch() { searchJob?.cancel(); YoutubeDL.destroyProcessById(searchId) }
    fun downloadSong(result: MusicResult) {
        if (!download.value.ready || download.value.busy || discovery.value.updating) return
        if (OnlineMusicEngine.FOLDER in app.store.current.music.excludedFolders) {
            message.value = "Vuelve a incluir Descargas desde Carpetas excluidas antes de descargar."; return
        }
        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workDataOf("video" to result.id, "title" to result.title, "artist" to result.artist))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("created:${System.currentTimeMillis()}").addTag("title:${result.title}").build()
        download.value = MusicDownload(ready = true, busy = true, waiting = true, title = result.title)
        viewModelScope.launch(Dispatchers.IO) {
            try { work.enqueueUniqueWork(MusicDownloadWorker.NAME, ExistingWorkPolicy.KEEP, request).result.get() }
            catch (_: Exception) { download.value = MusicDownload(ready = true, error = "No se pudo iniciar la descarga. Vuelve a intentarlo.") }
        }
    }
    fun cancelDownload() { work.cancelUniqueWork(MusicDownloadWorker.NAME) }
    fun updateMusicEngine() {
        if (discovery.value.searching || discovery.value.updating || !download.value.ready || download.value.busy) return
        discovery.value = discovery.value.copy(updating = true, error = "", notice = "")
        viewModelScope.launch {
            try {
                runInterruptible(Dispatchers.IO) { OnlineMusicEngine.init(app); YoutubeDL.updateYoutubeDL(app) }
                discovery.value = discovery.value.copy(notice = "Motor actualizado. Ya puedes buscar y descargar.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { discovery.value = discovery.value.copy(error = OnlineMusic.error(e)) }
            finally { discovery.value = discovery.value.copy(updating = false) }
        }
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
                val ignored = songs.count { it.folder in app.store.current.music.excludedFolders }
                message.value = if (songs.isEmpty()) "Android no encontró audios. Puedes elegir tus archivos con Añadir canciones." else "${songs.size - ignored} audios detectados.${if (ignored > 0) " $ignored omitidos de carpetas excluidas." else ""}"
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
                val ignored = songs.count { it.folder in app.store.current.music.excludedFolders }
                message.value = "${songs.size - ignored} canciones añadidas.${if (ignored > 0) " $ignored pertenecen a carpetas excluidas; vuelve a incluirlas desde Carpetas." else ""}${if (failed > 0) " No se pudieron abrir $failed archivos; elige audios disponibles en el teléfono." else ""}"
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
    fun excludeFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var removed = emptyList<String>()
                app.store.update {
                    removed = it.music.songs.filter { song -> song.folder == path }.map { song -> song.uri }
                    it.copy(music = Music.excludeFolder(it.music, path))
                }
                withContext(Dispatchers.Main) { removed.forEach(::queueRemove) }
                message.value = "Carpeta excluida. Sus archivos se conservan en el teléfono."
            }.onFailure { message.value = it.message ?: "No se pudo excluir esta carpeta." }
        }
    }
    fun includeFolder(path: String) = edit {
        message.value = "Carpeta incluida. Pulsa Detectar audios para cargarla de nuevo."
        it.copy(excludedFolders = it.excludedFolders - path)
    }
    fun sleep(minutes: Int) = sleepCommand(MusicService.SLEEP, Bundle().apply { putInt("minutes", minutes) })
    private fun sleepCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val result = controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args) ?: return
        result.addListener({ runCatching { result.get() }.onSuccess { if (it.resultCode == SessionResult.RESULT_SUCCESS) { sleepDeadline = it.extras.getLong("deadline"); snapshot() } } }, executor)
    }
    override fun onCleared() { cancelSearch(); MediaController.releaseFuture(future); super.onCleared() }
}
