package co.tuvida.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.Song
import co.tuvida.app.platform.SongArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SongCover(song: Song, size: Dp = 56.dp) {
    val context = LocalContext.current.applicationContext
    val placeholder = remember(song.title, song.artist) { SongArtwork.fallback(song).asImageBitmap() }
    var cover by remember(song) { mutableStateOf(placeholder) }
    LaunchedEffect(song) { cover = withContext(Dispatchers.IO) { SongArtwork.load(context, song).asImageBitmap() } }
    Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)))
}
