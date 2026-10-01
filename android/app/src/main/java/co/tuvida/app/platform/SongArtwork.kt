package co.tuvida.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import co.tuvida.app.data.Song
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Local embedded covers, with a deterministic vector record design for untagged downloads. */
object SongArtwork {
    private const val SIZE = 192
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val bytesCache = LruCache<String, ByteArray>(64)
    private val readers = Semaphore(2)
    private fun key(song: Song) = "${song.uri}|${song.title}|${song.artist}|${song.album}"

    suspend fun load(context: Context, song: Song): Bitmap = readers.withPermit {
        val key = key(song)
        cache.get(key) ?: (embedded(context, song) ?: fallback(song)).also { cache.put(key, it) }
    }

    private fun embedded(context: Context, song: Song): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(song.uri))
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / (inSampleSize * 2) >= SIZE && bounds.outHeight / (inSampleSize * 2) >= SIZE) inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally { retriever.release() }
    }.getOrNull()

    fun fallback(song: Song): Bitmap {
        val seed = (song.artist + "|" + song.title).hashCode().toLong().let { if (it < 0) -it else it }
        val accents = intArrayOf(0xFFE9C66F.toInt(), 0xFFC99170.toInt(), 0xFFA9CFB5.toInt(), 0xFFC4A3C7.toInt())
        val grounds = intArrayOf(0xFF40351C.toInt(), 0xFF3A2923.toInt(), 0xFF253B30.toInt(), 0xFF342638.toInt())
        val index = (seed % accents.size).toInt()
        return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(grounds[index])
            paint.color = accents[index]; paint.strokeWidth = 2f
            canvas.drawLine(16f, 17f, 176f, 17f, paint)
            paint.color = Color.rgb(17, 17, 18); canvas.drawCircle(96f, 102f, 69f, paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(62, 59, 52); paint.strokeWidth = 1.5f
            listOf(60f, 51f, 42f).forEach { canvas.drawCircle(96f, 102f, it, paint) }
            paint.color = accents[index]; paint.strokeWidth = 3f
            canvas.drawArc(40f, 46f, 152f, 158f, (seed % 180).toFloat(), 54f, false, paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(96f, 102f, 27f, paint)
            paint.color = grounds[index]; canvas.drawCircle(96f, 102f, 5f, paint)
            paint.color = accents[index]
            val bars = 3 + (seed % 4).toInt()
            repeat(bars) { bar -> canvas.drawRect(17f + bar * 8, 163f - ((seed shr bar) % 13), 21f + bar * 8, 175f, paint) }
        }
    }

    /** System media controls also get a cover without network access or file reads on main. */
    @Synchronized fun fallbackBytes(song: Song): ByteArray {
        val key = key(song)
        return bytesCache.get(key) ?: ByteArrayOutputStream().use { output ->
            val bitmap = fallback(song)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
            output.toByteArray().also { bytesCache.put(key, it) }
        }
    }
}
