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
    fun request(input: String) = request(listOf(input))
    fun request(inputs: List<String>) = YoutubeDLRequest(inputs).apply {
        addOption("--ignore-config"); addOption("--no-playlist")
        addOption("--socket-timeout", 20); addOption("--retries", 2)
        addOption("--no-warnings")
    }
    fun downloadRequest(inputs: List<String>, staging: File) = request(inputs).apply {
        addOption("-f", "bestaudio/best"); addOption("--extract-audio")
        // AAC/Opus extraction copies the source audio without re-encoding.
        addOption("--audio-format", "best")
        addOption("--embed-metadata"); addOption("--no-mtime")
        addOption("--max-filesize", "500M"); addOption("--match-filter", "!is_live")
        addOption("--newline"); addOption("-o", File(staging, "audio.%(ext)s").absolutePath)
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
    fun file(context: Context, id: String, extension: String = "mp3"): File {
        require(OnlineMusic.validId(id)) { "Identificador de canción inválido." }
        require(extension in OnlineMusic.audioExtensions) { "Formato de audio inválido." }
        return File(directory(context), "$id.$extension")
    }
    fun existingFile(context: Context, id: String): File? = OnlineMusic.audioExtensions
        .asSequence().map { file(context, id, it) }.firstOrNull { it.isFile && it.length() > 0 }
    fun uri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.musicfiles", file)
    fun localSongs(context: Context) = directory(context).listFiles().orEmpty().filter { it.isFile && it.extension in OnlineMusic.audioExtensions }.mapNotNull {
        runCatching { AudioFiles.read(context, uri(context, it)) }.getOrNull()
    }
}
