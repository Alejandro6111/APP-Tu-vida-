package co.tuvida.app.platform

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import co.tuvida.app.data.Song

object AudioFiles {
    private fun pathColumns() = if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME)
        else arrayOf(MediaStore.MediaColumns.DATA)
    private fun folder(cursor: android.database.Cursor, offset: Int): String {
        val path = cursor.getString(offset).orEmpty()
        if (path.isBlank()) return ""
        return if (Build.VERSION.SDK_INT >= 29) "${cursor.getString(offset + 1).orEmpty()}:${path.trim('/')}"
            else path.substringBeforeLast('/', "")
    }
    private fun readFolder(context: Context, uri: Uri): String {
        val mediaPath = runCatching { context.contentResolver.query(uri, pathColumns(), null, null, null)?.use { if (it.moveToFirst()) folder(it, 0) else "" } }.getOrNull().orEmpty()
        if (mediaPath.isNotBlank()) return mediaPath
        return runCatching {
            if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val volume = id.substringBefore(':').let { if (it == "primary") "external_primary" else it.lowercase(java.util.Locale.ROOT) }
                val path = id.substringAfter(':', "").substringBeforeLast('/', "")
                if (path.isNotBlank()) "$volume:$path" else ""
            } else ""
        }.getOrDefault("")
    }
    fun scan(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION) + pathColumns()
        return context.contentResolver.query(collection, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1).orEmpty().ifBlank { "Audio sin título" }.take(500)
                    add(Song(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), title,
                        cursor.getString(2).orEmpty().replace("<unknown>", "").take(500), cursor.getString(3).orEmpty().replace("<unknown>", "").take(500), cursor.getLong(4).coerceAtLeast(0), folder(cursor, 5)))
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
                meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()?.coerceAtLeast(0) ?: 0, readFolder(context, uri))
        } finally { retriever.release() }
    }
}
