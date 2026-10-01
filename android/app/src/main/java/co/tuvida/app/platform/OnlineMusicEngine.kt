package co.tuvida.app.platform

import android.content.Context
import androidx.core.content.FileProvider
import co.tuvida.app.domain.OnlineMusic
import co.tuvida.app.domain.MusicResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File

object OnlineMusicEngine {
    const val FOLDER = "Tu Vida/Descargas"
    @Synchronized fun init(context: Context) { YoutubeDL.init(context); FFmpeg.init(context) }
    fun request(input: String) = YoutubeDLRequest(input).apply {
        addOption("--ignore-config"); addOption("--no-playlist")
        addOption("--socket-timeout", 20); addOption("--retries", 2)
        addOption("--no-warnings")
    }
    fun search(context: Context, query: String, processId: String): List<MusicResult> {
        val input = OnlineMusic.input(query)
        init(context)
        val request = request(input).apply {
            addOption("--flat-playlist"); addOption("--dump-json"); addOption("--skip-download")
        }
        return OnlineMusic.results(YoutubeDL.execute(request, processId, null).out)
    }
    fun directory(context: Context) = File(context.filesDir, "music").apply { mkdirs() }
    fun file(context: Context, id: String): File {
        require(OnlineMusic.validId(id)) { "Identificador de canción inválido." }
        return File(directory(context), "$id.mp3")
    }
    fun uri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.musicfiles", file)
    fun localSongs(context: Context) = directory(context).listFiles().orEmpty().filter { it.extension == "mp3" }.mapNotNull {
        runCatching { AudioFiles.read(context, uri(context, it)) }.getOrNull()
    }
}
