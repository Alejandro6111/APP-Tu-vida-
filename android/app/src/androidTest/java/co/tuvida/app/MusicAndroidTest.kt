package co.tuvida.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.tuvida.app.data.*
import co.tuvida.app.platform.*
import org.junit.*
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var app: TuVidaApplication
    private val uris = mutableListOf<android.net.Uri>()
    @Before fun setup() {
        app = ui.activity.application as TuVidaApplication
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        ui.runOnIdle { app.store.restore(AppData(preferences = Preferences(football = emptySet(), notifications = false))) }
    }
    private fun track(title: String): Song {
        val rate = 8000; val samples = rate * 60; val pcmBytes = samples * 2
        val bytes = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmBytes); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcmBytes)
            repeat(samples) { putShort((kotlin.math.sin(it * 2 * Math.PI * 220 / rate) * 200).toInt().toShort()) }
        }.array()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "tuvida-test-${System.nanoTime()}.wav")
            put(MediaStore.Audio.Media.TITLE, title); put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (android.os.Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TuVidaTests"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
        }
        val uri = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        uris += uri
        app.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        if (android.os.Build.VERSION.SDK_INT >= 29) app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return AudioFiles.read(app, uri).copy(title = title, artist = "Audio de prueba", album = "Verificación local")
    }
    private fun controller(): MediaController = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync().get(10, TimeUnit.SECONDS)
    @After fun clean() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        val p = controller(); ui.runOnIdle { p.stop(); p.clearMediaItems(); p.release() }
        uris.forEach { app.contentResolver.delete(it, null, null) }
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/tuvida-$name.png").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
    }
    @Test fun favoritesPlaylistsAndNativeScreens() {
        val a = track("Brisa de ejemplo"); val b = track("Camino de ejemplo")
        ui.runOnIdle { app.store.update { it.copy(music = MusicLibrary(songs = listOf(a, b))) } }
        ui.onNodeWithContentDescription("Música").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Conectando reproductor").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription("Marcar favorito: Brisa de ejemplo").performScrollTo().performClick()
        ui.waitUntil(10000) { a.uri in app.store.current.music.favorites }
        ui.onNodeWithText("Listas").performClick(); ui.onNodeWithText("Crear lista").performScrollTo().performClick()
        ui.onNodeWithText("Nombre de la lista").performTextInput("Para caminar")
        ui.onNodeWithText("Crear", substring = false).performScrollTo().performClick()
        ui.waitUntil(10000) { app.store.current.music.playlists.any { it.name == "Para caminar" } }
        ui.onNodeWithText("Biblioteca", substring = false).performClick()
        ui.onNodeWithContentDescription("Opciones de Brisa de ejemplo").performScrollTo().performClick()
        ui.onNodeWithText("Añadir a lista").performClick(); ui.onNodeWithText("Para caminar").performClick()
        ui.waitUntil(10000) { app.store.current.music.playlists.single().songs == listOf(a.uri) }
        ui.onNodeWithText("Reproducir todo").performScrollTo().performClick()
        val p = controller()
        ui.waitUntil(15000) { var playing = false; ui.runOnIdle { playing = p.isPlaying }; playing }
        ui.onNodeWithText("Tu música, a tu ritmo").performScrollTo(); capture("music-light")
        ui.runOnIdle { app.store.update { it.copy(preferences = it.preferences.copy(theme = "dark")) } }; capture("music-dark")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.3").close()
        ui.waitUntil(10000) { ui.activity.resources.configuration.fontScale > 1.2f }
        Thread.sleep(800)
        ui.waitForIdle(); capture("music-font-large")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        ui.waitUntil(10000) { ui.activity.resources.configuration.fontScale < 1.1f }
        Thread.sleep(800)
        ui.onNodeWithText("Listas").performClick(); ui.onNodeWithText("Para caminar").performScrollTo().performClick(); capture("music-playlist")
        ui.onNodeWithText("Cola", substring = false).performClick(); ui.onNodeWithText("Brisa de ejemplo", substring = false).performScrollTo(); capture("music-queue")
        ui.onNodeWithText("Hoy").performClick(); capture("music-mini")
        ui.runOnIdle { p.pause(); p.release() }
    }
    @Test fun playbackSurvivesBackgroundAndKeepsModesQueueAndSleep() {
        val a = track("Prueba de audio uno"); val b = track("Prueba de audio dos")
        Assert.assertTrue(AudioFiles.scan(app).map { it.uri }.containsAll(listOf(a.uri, b.uri)))
        ui.runOnIdle { app.store.update { it.copy(music = MusicLibrary(songs = listOf(a, b))) } }
        val p = controller()
        ui.runOnIdle { p.setMediaItems(listOf(a.mediaItem(), b.mediaItem())); p.prepare(); p.play() }
        ui.waitUntil(15000) { var playing = false; ui.runOnIdle { playing = p.isPlaying && p.currentPosition > 300 }; playing }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(600); capture("music-notification")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        ui.runOnIdle { p.repeatMode = Player.REPEAT_MODE_ONE; p.shuffleModeEnabled = true; p.seekTo(15000) }
        ui.waitUntil(10000) { app.store.current.music.repeat == 1 && app.store.current.music.shuffle }
        var sleepFuture: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
        ui.runOnIdle { sleepFuture = p.sendCustomCommand(SessionCommand(MusicService.SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", 5) }) }
        Assert.assertEquals(SessionResult.RESULT_SUCCESS, sleepFuture!!.get(5, TimeUnit.SECONDS).resultCode)
        val device = InstrumentationRegistry.getInstrumentation().uiAutomation
        device.executeShellCommand("input keyevent KEYCODE_HOME").close()
        // A real elapsed interval proves the service continues advancing outside the Activity.
        Thread.sleep(1500)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { Assert.assertTrue(p.isPlaying); Assert.assertTrue(p.currentPosition > 15000) }
        var status: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { status = p.sendCustomCommand(SessionCommand(MusicService.SLEEP_STATUS, Bundle.EMPTY), Bundle.EMPTY); p.pause() }
        Assert.assertTrue(status!!.get(5, TimeUnit.SECONDS).extras.getLong("deadline") > android.os.SystemClock.elapsedRealtime())
        ui.waitUntil(10000) { app.store.current.music.position >= 15000 }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { p.repeatMode = Player.REPEAT_MODE_ALL; p.shuffleModeEnabled = false; p.seekToDefaultPosition(1); p.moveMediaItem(1, 0) }
        ui.waitUntil(10000) { app.store.current.music.queue.firstOrNull() == b.uri && app.store.current.music.repeat == 2 }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { p.release() }
    }
}
