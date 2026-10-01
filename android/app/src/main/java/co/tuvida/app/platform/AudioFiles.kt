package co.tuvida.app.platform

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import co.tuvida.app.data.Song

object AudioFiles {
    fun scan(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        return context.contentResolver.query(collection, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1).orEmpty().ifBlank { "Audio sin título" }.take(500)
                    add(Song(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), title,
                        cursor.getString(2).orEmpty().replace("<unknown>", "").take(500), cursor.getString(3).orEmpty().replace("<unknown>", "").take(500), cursor.getLong(4).coerceAtLeast(0)))
                }
            }
        } ?: emptyList()
    }
    fun read(context: Context, uri: Uri): Song {
        require(uri.scheme == "content") { "Elige un archivo de audio desde el selector de Android." }
        val fallback = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Audio sin título"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key).orEmpty().take(500)
            return Song(uri.toString(), meta(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fallback.take(500) },
                meta(MediaMetadataRetriever.METADATA_KEY_ARTIST), meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()?.coerceAtLeast(0) ?: 0)
        } finally { retriever.release() }
    }
}
