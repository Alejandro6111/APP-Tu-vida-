@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package co.tuvida.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.tuvida.app.data.*
import co.tuvida.app.domain.Music

@Composable fun MusicMiniPlayer(data: MusicLibrary, vm: MusicViewModel, open: () -> Unit) {
    val playback by vm.playback.collectAsState()
    val song = data.songs.find { it.uri == playback.current } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClickLabel = "Abrir reproductor", onClick = open).heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(song.artist.ifBlank { "Artista desconocido" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(vm::toggle) { Icon(if (playback.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playback.playing) "Pausar música" else "Reanudar música") }
            IconButton(vm::next) { Icon(Icons.Outlined.SkipNext, "Siguiente canción") }
        }
    }
}

@Composable fun MusicScreen(data: MusicLibrary, vm: MusicViewModel) {
    val context = LocalContext.current
    val playback by vm.playback.collectAsState(); val busy by vm.busy.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }; var sort by rememberSaveable { mutableStateOf("title") }
    var selected by rememberSaveable { mutableStateOf("") }
    var editName by rememberSaveable { mutableStateOf(false) }; var name by rememberSaveable { mutableStateOf("") }
    var addSong by remember { mutableStateOf<Song?>(null) }
    var delete by remember { mutableStateOf<MusicPlaylist?>(null) }
    var remove by remember { mutableStateOf<Song?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::import)
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.scan() else vm.message.value = "Puedes elegir archivos con Añadir canciones, sin dar acceso a toda tu música. Para detectar audios, autoriza el permiso en los ajustes de Android."
    }
    val playlist = data.playlists.find { it.id == selected }
    val byId = remember(data.songs) { data.songs.associateBy { it.uri } }
    val source = when (tab) {
        1 -> data.songs.filter { it.uri in data.favorites }
        2 -> playlist?.songs?.mapNotNull { byId[it] } ?: emptyList()
        3 -> playback.queue.mapNotNull { byId[it] }
        else -> data.songs
    }
    val songs = remember(source, query, sort, tab) {
        if (tab >= 2) Music.search(source, query, "title").let { matches -> source.filter { it in matches } } else Music.search(source, query, sort)
    }
    BackHandler(enabled = selected.isNotEmpty() || editName) { selected = ""; editName = false }
    Column {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            listOf("Biblioteca", "Favoritos", "Listas", "Cola").forEachIndexed { index, title ->
                Tab(tab == index, { tab = index; selected = ""; editName = false; query = "" }, text = { Text(title) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Tu música, a tu ritmo", style = MaterialTheme.typography.headlineSmall)
                Text("Canciones descargadas · Sin anuncios", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton({ picker.launch(arrayOf("audio/*")) }, enabled = !busy) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir canciones") }
                    OutlinedButton({ if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan() else request.launch(permission) }, enabled = !busy) { Text("Detectar audios") }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)); Text("Leyendo canciones…", style = MaterialTheme.typography.bodySmall) }
            }
            if (!playback.connected) item { Info("Conectando reproductor", "Los controles estarán disponibles en un momento.") }
            byId[playback.current]?.let { song -> item { MusicPlayer(song, data, playback, vm) } }
            if (playback.error.isNotBlank()) item { Info("Archivo no disponible", playback.error, Icons.Outlined.ErrorOutline) }
            if (tab == 2 && playlist == null) {
                item { Section("Tus listas", action = { TextButton({ editName = !editName; name = "" }) { Text("Crear lista") } }) }
                if (editName) item { PlaylistName(name, { name = it }, "Crear", { vm.createPlaylist(name); editName = false; name = "" }, { editName = false }) }
                if (data.playlists.isEmpty()) item { Info("Haz espacio para tus favoritas", "Crea una lista y añade canciones desde el menú de cada audio.", Icons.Outlined.PlaylistAdd) }
                items(data.playlists, key = { it.id }) { p ->
                    ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.songs.size} canciones") }, leadingContent = { Icon(Icons.Outlined.QueueMusic, null) },
                        modifier = Modifier.clickable { selected = p.id; editName = false; query = "" },
                        trailingContent = { IconButton({ vm.play(p.songs.mapNotNull { byId[it] }) }, enabled = p.songs.isNotEmpty() && playback.connected) { Icon(Icons.Outlined.PlayArrow, "Reproducir lista ${p.name}") } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
                }
            } else {
                item {
                    Section(if (tab == 3) "Cola de reproducción" else playlist?.name ?: if (tab == 1) "Tus favoritos" else "Biblioteca", "${source.size} canciones")
                    if (playlist != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ selected = ""; editName = false; query = "" }) { Text("Todas las listas") }
                            TextButton({ name = playlist.name; editName = !editName }) { Text("Renombrar") }
                            TextButton({ delete = playlist }) { Text("Eliminar lista") }
                        }
                    }
                    if (songs.isNotEmpty()) FilledTonalButton({ if (tab == 3) vm.queuePlay(songs.first().uri) else vm.play(songs) }, enabled = playback.connected) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Reproducir todo") }
                }
                if (editName && playlist != null) item { PlaylistName(name, { name = it }, "Guardar nombre", { vm.renamePlaylist(playlist.id, name); editName = false }, { editName = false }) }
                if (source.isNotEmpty()) {
                    item { Field("Buscar título, artista o álbum", query, { query = it }) }
                    if (tab < 2) item { Choice("Ordenar", sort, linkedMapOf("title" to "Título", "artist" to "Artista", "duration" to "Duración", "added" to "Añadidas recientemente"), { sort = it }) }
                }
                if (songs.isEmpty()) item {
                    Info(when { query.isNotBlank() -> "No hay coincidencias"; tab == 1 -> "Tus favoritos van aquí"; tab == 3 -> "La cola está vacía"; playlist != null -> "Añade canciones a esta lista"; else -> "Empieza con tus canciones" },
                        when { query.isNotBlank() -> "Prueba otro título, artista o álbum."; tab == 1 -> "Toca el corazón junto a una canción para guardarla aquí."; tab == 3 -> "Reproduce una canción o usa Añadir a la cola desde su menú."; playlist != null -> "Abre Biblioteca y elige Añadir a lista en el menú de una canción."; else -> "Pulsa Detectar audios para encontrar los del teléfono, o Añadir canciones para elegir archivos." }, Icons.Outlined.MusicNote)
                }
                items(songs, key = { it.uri }) { song ->
                    MusicSongRow(song, song.uri in data.favorites, song.uri == playback.current,
                        play = { if (tab == 3) vm.queuePlay(song.uri) else vm.play(songs, song.uri) }, favoriteAction = { vm.favorite(song.uri) }) { close ->
                        DropdownMenuItem(text = { Text("Reproducir después") }, onClick = { close(); vm.enqueue(song, true) })
                        DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { close(); vm.enqueue(song, false) })
                        DropdownMenuItem(text = { Text("Añadir a lista") }, onClick = { close(); addSong = song })
                        if (tab == 3 || playlist != null) {
                            DropdownMenuItem(text = { Text("Subir en ${if (tab == 3) "cola" else "lista"}") }, onClick = { close(); if (tab == 3) vm.queueMove(song.uri, -1) else vm.playlistMove(selected, song.uri, -1) })
                            DropdownMenuItem(text = { Text("Bajar en ${if (tab == 3) "cola" else "lista"}") }, onClick = { close(); if (tab == 3) vm.queueMove(song.uri, 1) else vm.playlistMove(selected, song.uri, 1) })
                            DropdownMenuItem(text = { Text("Quitar de ${if (tab == 3) "la cola" else "esta lista"}") }, onClick = { close(); if (tab == 3) vm.queueRemove(song.uri) else vm.removeFromPlaylist(selected, song.uri) })
                        }
                        DropdownMenuItem(text = { Text("Quitar de biblioteca") }, onClick = { close(); remove = song })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    addSong?.let { song ->
        AlertDialog(onDismissRequest = { addSong = null }, title = { Text("Añadir a una lista") },
            text = { Column {
                Text(song.title, style = MaterialTheme.typography.titleSmall)
                if (data.playlists.isEmpty()) Text("Crea una lista en la pestaña Listas y vuelve a añadir esta canción.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) { items(data.playlists, key = { it.id }) { p -> TextButton({ vm.addToPlaylist(p.id, song.uri); addSong = null }, Modifier.fillMaxWidth()) { Text(p.name) } } }
            } }, confirmButton = { TextButton({ addSong = null }) { Text("Cerrar") } })
    }
    delete?.let { p -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Eliminar ${p.name}") }, text = { Text("Se eliminará la lista. Las canciones seguirán en tu biblioteca y en el teléfono.") }, confirmButton = { TextButton({ vm.deletePlaylist(p.id); selected = ""; delete = null }) { Text("Eliminar lista") } }, dismissButton = { TextButton({ delete = null }) { Text("Cancelar") } }) }
    remove?.let { song -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Quitar ${song.title}") }, text = { Text("Se quitará de la biblioteca, favoritos, listas y cola. El archivo del teléfono se conserva.") }, confirmButton = { TextButton({ vm.removeSong(song.uri); remove = null }) { Text("Quitar canción") } }, dismissButton = { TextButton({ remove = null }) { Text("Cancelar") } }) }
}

@Composable private fun PlaylistName(name: String, change: (String) -> Unit, label: String, save: () -> Unit, cancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Nombre de la lista", name, { change(it.take(100)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(save, enabled = name.isNotBlank()) { Text(label) }; TextButton(cancel) { Text("Cancelar") } }
    }
}

@Composable private fun MusicSongRow(song: Song, favorite: Boolean, current: Boolean, play: () -> Unit, favoriteAction: () -> Unit, menu: @Composable (close: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${song.artist.ifBlank { "Artista desconocido" }}${if (song.album.isNotBlank()) " · ${song.album}" else ""} · ${Music.time(song.duration)}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(if (current) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote, if (current) "Canción actual" else null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClickLabel = "Reproducir ${song.title}", onClick = play),
        trailingContent = { Row {
            IconButton(favoriteAction) { Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "${if (favorite) "Quitar favorito" else "Marcar favorito"}: ${song.title}", tint = MaterialTheme.colorScheme.primary) }
            Box {
                IconButton({ expanded = true }) { Icon(Icons.Outlined.MoreVert, "Opciones de ${song.title}") }
                DropdownMenu(expanded, { expanded = false }) { menu { expanded = false } }
            }
        } }, colors = ListItemDefaults.colors(containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background))
}

@Composable private fun MusicPlayer(song: Song, data: MusicLibrary, p: MusicPlayback, vm: MusicViewModel) {
    var dragging by remember(song.uri) { mutableStateOf<Float?>(null) }
    var sleepMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(song.artist.ifBlank { "Artista desconocido" }, style = MaterialTheme.typography.bodyMedium)
                }
                val favorite = song.uri in data.favorites
                IconToggleButton(checked = favorite, onCheckedChange = { vm.favorite(song.uri) }) { Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Quitar canción actual de favoritos" else "Marcar canción actual como favorita") }
            }
            val duration = p.duration.coerceAtLeast(1).toFloat()
            Slider(value = dragging ?: p.position.toFloat().coerceIn(0f, duration), onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null }, valueRange = 0f..duration,
                enabled = p.duration > 0, modifier = Modifier.semantics { contentDescription = "Posición de la canción" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Music.time(dragging?.toLong() ?: p.position)); Text(Music.time(p.duration)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(vm::previous) { Icon(Icons.Outlined.SkipPrevious, "Canción anterior") }
                FilledIconButton(vm::toggle, Modifier.size(64.dp)) { Icon(if (p.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (p.playing) "Pausar" else "Reproducir", Modifier.size(32.dp)) }
                IconButton(vm::next) { Icon(Icons.Outlined.SkipNext, "Canción siguiente") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(p.shuffle, vm::shuffle, label = { Text("Aleatorio") }, leadingIcon = { Icon(Icons.Outlined.Shuffle, null) })
                FilterChip(p.repeat != 0, vm::repeat, label = { Text(when (p.repeat) { 1 -> "Repetir una"; 2 -> "Repetir todas"; else -> "Sin repetir" }) }, leadingIcon = { Icon(if (p.repeat == 1) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, null) })
                Box {
                    AssistChip(onClick = { sleepMenu = true }, label = { Text(if (p.sleepRemaining > 0) "Dormir: ${Music.time(p.sleepRemaining)}" else "Temporizador") }, leadingIcon = { Icon(Icons.Outlined.Bedtime, null) })
                    DropdownMenu(sleepMenu, { sleepMenu = false }) { listOf(0, 5, 15, 30, 60, 90).forEach { min -> DropdownMenuItem(text = { Text(if (min == 0) "Desactivar temporizador" else "Pausar en $min minutos") }, onClick = { vm.sleep(min); sleepMenu = false }) } }
                }
            }
        }
    }
}
