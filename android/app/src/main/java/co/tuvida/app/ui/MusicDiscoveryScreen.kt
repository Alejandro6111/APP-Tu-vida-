package co.tuvida.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.MusicLibrary
import co.tuvida.app.domain.Music
import co.tuvida.app.domain.MusicResult
import co.tuvida.app.domain.OnlineMusic

@Composable fun MusicDiscoveryScreen(data: MusicLibrary, vm: MusicViewModel, query: String, change: (String) -> Unit, downloadSong: (MusicResult) -> Unit) {
    val state by vm.discovery.collectAsState()
    val download by vm.download.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val search = { keyboard?.hide(); vm.searchOnline(query) }
    LazyColumn(Modifier.testTag("music-discovery"), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Encuentra tu próxima canción", style = MaterialTheme.typography.headlineSmall)
            Text("Busca en YouTube y guarda el audio para escucharlo sin conexión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(query, { change(it.take(200)) }, Modifier.fillMaxWidth().testTag("music-online-query"),
                label = { Text("Canción, artista o enlace de YouTube") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (!state.searching && !state.updating && query.isNotBlank()) search() }))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(search, enabled = query.isNotBlank() && !state.searching && !state.updating) { Text("Buscar música") }
                if (state.searching) TextButton(vm::cancelSearch) { Text("Cancelar búsqueda") }
            }
        }
        if (state.searching || state.updating) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.updating) "Actualizando motor…" else "Buscando canciones…", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.error.isNotBlank()) item { Info("No se pudo completar", state.error) }
        if (state.notice.isNotBlank()) item { Text(state.notice, color = MaterialTheme.colorScheme.primary) }
        if (download.busy || download.cancelled || download.error.isNotBlank() || data.songs.any { it.uri == download.uri }) item { MusicDownloadCard(data, vm, download) }
        if (!state.searching && state.results.isEmpty()) item {
            Info(if (state.searched) "No hay resultados" else "Tu música empieza aquí",
                if (state.searched) "Prueba otro título o artista, o pega el enlace de una canción." else "Escribe el nombre de una canción o de un artista. Las descargas aparecen en Biblioteca y en la carpeta Descargas.")
        }
        if (state.results.isNotEmpty()) item { Section("Resultados de YouTube", "${state.results.size} canciones · Máxima calidad disponible") }
        items(state.results, key = { it.id }) { result ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Text("${result.artist.ifBlank { "Artista desconocido" }} · ${if (result.duration > 0) Music.time(result.duration) else "Duración no disponible"}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val local = data.songs.find { OnlineMusic.downloadId(it.uri) == result.id }
                    if (local != null) FilledTonalButton({ vm.play(listOf(local)) }) { Text("Escuchar descargada") }
                    FilledTonalButton({ downloadSong(result) }, enabled = download.ready && !download.busy && !state.updating,
                        modifier = Modifier.testTag("download-${result.id}")) {
                        Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text(if (local == null) "Descargar audio" else "Recuperar descarga")
                    }
                }
            }
        }
        item {
            TextButton(vm::updateMusicEngine, enabled = download.ready && !download.busy && !state.searching && !state.updating) { Text("Actualizar motor") }
            Text("Los audios quedan dentro de Tu Vida. Si desinstalas la app o borras sus datos, también se eliminan las descargas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun MusicDownloadCard(data: MusicLibrary, vm: MusicViewModel, download: MusicDownload) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when { download.busy -> "Descargando audio"; download.error.isNotBlank() -> "Descarga pendiente de reintento"; download.cancelled -> "Descarga cancelada"; else -> "Lista en tu biblioteca" }, style = MaterialTheme.typography.titleMedium)
            if (download.title.isNotBlank()) Text(download.title)
            when {
                download.busy -> {
                    if (download.waiting || download.percent == 0 || download.percent == 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { download.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(when { download.waiting -> "Esperando conexión o turno de Android…"; download.percent == 100 -> "Preparando audio…"; download.percent == 0 -> "Preparando descarga…"; else -> "${download.percent}%" })
                    TextButton(vm::cancelDownload) { Text("Cancelar descarga") }
                }
                download.error.isNotBlank() -> Text(download.error)
                download.uri.isNotBlank() -> data.songs.find { it.uri == download.uri }?.let { song ->
                    FilledTonalButton({ vm.play(listOf(song)) }) { Text("Escuchar ahora") }
                }
            }
        }
    }
}
