@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package co.tuvida.app.platform

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import co.tuvida.app.MainActivity
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.data.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist.ifBlank { "Artista desconocido" }).setAlbumTitle(album).build()).build()

class MusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Snapshot(val queue: List<String>, val current: String, val position: Long, val repeat: Int, val shuffle: Boolean)
    private val snapshots = Channel<Snapshot>(Channel.CONFLATED)
    private val handler = Handler(Looper.getMainLooper())
    private var sleepDeadline = 0L
    private val sleepAction = Runnable { sleepDeadline = 0; player.pause() }
    private val checkpoint = object : Runnable {
        override fun run() { if (player.isPlaying) save(); handler.postDelayed(this, 10000) }
    }
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (snapshot in snapshots) runCatching { (application as TuVidaApplication).store.update { data ->
                val ids = data.music.songs.map { it.uri }.toSet()
                val valid = snapshot.queue.filter { it in ids }.distinct()
                data.copy(music = data.music.copy(queue = valid, current = snapshot.current.takeIf { it in valid }.orEmpty(), position = snapshot.position, repeat = snapshot.repeat, shuffle = snapshot.shuffle))
            } }
        }.invokeOnCompletion { scope.cancel() }
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val library = (application as TuVidaApplication).store.current.music
        val byId = library.songs.associateBy { it.uri }
        val items = library.queue.mapNotNull { byId[it]?.mediaItem() }
        if (items.isNotEmpty()) player.setMediaItems(items, library.queue.indexOf(library.current).coerceAtLeast(0), library.position)
        player.repeatMode = library.repeat; player.shuffleModeEnabled = library.shuffle
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { save() }
            override fun onPlayerError(error: PlaybackException) {
                // Pause on failure so a missing file cannot loop forever in repeat-one mode.
                player.pause()
            }
        })
        val activity = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java).putExtra("route", "music"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(SLEEP, Bundle.EMPTY)).add(SessionCommand(SLEEP_STATUS, Bundle.EMPTY)).build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (command.customAction == SLEEP) {
                    val minutes = args.getInt("minutes")
                    if (minutes !in listOf(0, 5, 15, 30, 60, 90)) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    handler.removeCallbacks(sleepAction)
                    sleepDeadline = if (minutes == 0) 0 else SystemClock.elapsedRealtime() + minutes * 60000L
                    if (minutes > 0) handler.postDelayed(sleepAction, minutes * 60000L)
                } else if (command.customAction != SLEEP_STATUS) return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply { putLong("deadline", sleepDeadline) }))
            }
        }).build()
        handler.post(checkpoint)
    }
    private fun save() {
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val current = player.currentMediaItem?.mediaId.orEmpty(); val position = player.currentPosition.coerceAtLeast(0)
        val repeat = player.repeatMode; val shuffle = player.shuffleModeEnabled
        snapshots.trySend(Snapshot(queue, current, position, repeat, shuffle))
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) stopSelf() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        save(); snapshots.close()
        session?.release(); player.release(); super.onDestroy()
    }
    companion object { const val SLEEP = "co.tuvida.app.SLEEP"; const val SLEEP_STATUS = "co.tuvida.app.SLEEP_STATUS" }
}
