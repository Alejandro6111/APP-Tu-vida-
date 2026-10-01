package co.tuvida.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import co.tuvida.app.R
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.domain.Music
import co.tuvida.app.domain.OnlineMusic
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** WorkManager owns the download, independent of Activity/ViewModel lifetime. */
class MusicDownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object { const val NAME = "music-download"; const val CHANNEL = "music-downloads" }
    override fun doWork(): Result {
        val app = applicationContext as TuVidaApplication
        val video = inputData.getString("video").orEmpty()
        val title = inputData.getString("title").orEmpty().take(500)
        val artist = inputData.getString("artist").orEmpty().take(500)
        val staging = File(app.filesDir, "music-staging/$id")
        try {
            require(OnlineMusic.validId(video) && title.isNotBlank()) { "La canción seleccionada no es válida." }
            check(app.store.recoveryError.isEmpty()) { app.store.recoveryError }
            require(OnlineMusicEngine.FOLDER !in app.store.current.music.excludedFolders) { "Vuelve a incluir Descargas desde Carpetas excluidas antes de descargar." }
            require(app.store.current.music.songs.size < 10000) { "Tu biblioteca ya tiene 10.000 canciones. Quita alguna antes de descargar." }
            if (isStopped) return Result.failure()
            setForegroundAsync(notification(title)).get()
            val destination = OnlineMusicEngine.file(app, video)
            if (!destination.exists()) {
                OnlineMusicEngine.init(app)
                if (isStopped) return Result.failure()
                staging.mkdirs()
                val request = OnlineMusicEngine.request("https://www.youtube.com/watch?v=$video").apply {
                    addOption("-f", "bestaudio/best"); addOption("--extract-audio")
                    addOption("--audio-format", "mp3"); addOption("--audio-quality", "0")
                    addOption("--embed-metadata"); addOption("--no-mtime")
                    addOption("--max-filesize", "500M")
                    addOption("--match-filter", "!is_live")
                    addOption("--newline"); addOption("-o", File(staging, "audio.%(ext)s").absolutePath)
                }
                var lastProgress = -1
                YoutubeDL.execute(request, id.toString()) { percent, _, _ ->
                    if (isStopped) YoutubeDL.destroyProcessById(id.toString())
                    val progress = percent.toInt().coerceIn(0, 100)
                    if (progress != lastProgress) {
                        lastProgress = progress
                        setProgressAsync(workDataOf("percent" to progress))
                    }
                }
                if (isStopped) return Result.failure()
                val audio = File(staging, "audio.mp3")
                check(audio.exists() && audio.length() > 0) { "El proveedor no entregó un archivo de audio." }
                // Validate the converted file before making it part of the library.
                android.media.MediaMetadataRetriever().let { reader ->
                    try { reader.setDataSource(audio.absolutePath); check(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it > 0 } == true) }
                    finally { reader.release() }
                }
                check(audio.renameTo(destination)) { "No se pudo guardar la descarga." }
            }
            if (isStopped) return Result.failure()
            val song = AudioFiles.read(app, OnlineMusicEngine.uri(app, destination)).copy(title = title, artist = artist)
            app.store.update {
                require(OnlineMusicEngine.FOLDER !in it.music.excludedFolders) { "Descargas está excluida. Vuelve a incluir la carpeta." }
                it.copy(music = Music.merge(it.music, listOf(song)))
            }
            return Result.success(workDataOf("uri" to song.uri, "title" to title))
        } catch (e: Exception) {
            return Result.failure(workDataOf("error" to OnlineMusic.error(e), "title" to title))
        } finally { staging.deleteRecursively() }
    }
    override fun onStopped() { YoutubeDL.destroyProcessById(id.toString()); super.onStopped() }
    private fun notification(title: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de música", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_app).setContentTitle("Descargando música").setContentText(title)
            .setOngoing(true).setSilent(true).setProgress(0, 0, true)
            .addAction(0, "Cancelar", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(7201, notification)
    }
}
