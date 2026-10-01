package co.tuvida.app

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import co.tuvida.app.data.*
import co.tuvida.app.domain.MusicResult
import co.tuvida.app.platform.*
import co.tuvida.app.ui.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicDiscoveryAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var app: TuVidaApplication
    private lateinit var vm: MusicViewModel
    private val testId = "TuVidaTest1"
    @Before fun setup() {
        app = ui.activity.application as TuVidaApplication
        WorkManager.getInstance(app).cancelUniqueWork(MusicDownloadWorker.NAME).result.get(10, TimeUnit.SECONDS)
        ui.runOnIdle {
            app.store.restore(AppData(preferences = Preferences(theme = "dark", football = emptySet(), notifications = false)))
            vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        }
        ui.waitUntil(10000) { vm.download.value.ready && !vm.download.value.busy }
    }
    @After fun cleanup() {
        WorkManager.getInstance(app).cancelUniqueWork(MusicDownloadWorker.NAME).result.get(10, TimeUnit.SECONDS)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        OnlineMusicEngine.file(app, testId).delete()
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(500) // Wait for SurfaceFlinger, not just Compose's semantics tree.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/tuvida-$name.png").let {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
    }
    @Test fun bundledEngineAndPrivateAudioDownload() {
        OnlineMusicEngine.init(app)
        val version = YoutubeDL.execute(YoutubeDLRequest(emptyList()).apply { addOption("--version") }, "test-version", null)
        assertEquals(0, version.exitCode); assertTrue(version.out.trim().isNotBlank())
        assertTrue(File(app.applicationInfo.nativeLibraryDir, "libqjs.so").exists())
        val destination = OnlineMusicEngine.file(app, testId)
        val process = ProcessBuilder(File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath,
            "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=2", "-codec:a", "libmp3lame",
            "-metadata", "title=Audio de prueba", "-metadata", "artist=Tu Vida", destination.absolutePath).redirectErrorStream(true)
        process.environment()["LD_LIBRARY_PATH"] = File(app.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath + ":" +
            File(app.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib").absolutePath
        val running = process.start(); val output = running.inputStream.bufferedReader().use { it.readText() }
        assertTrue(running.waitFor(20, TimeUnit.SECONDS)); assertEquals(output, 0, running.exitValue())
        val song = AudioFiles.read(app, OnlineMusicEngine.uri(app, destination))
        assertTrue(song.duration > 0); assertEquals(OnlineMusicEngine.FOLDER, song.folder)
        assertEquals("Audio de prueba", song.title)
        ui.runOnIdle { vm.downloadSong(MusicResult(testId, song.title, "Tu Vida", song.duration)) }
        ui.waitUntil(20000) { !vm.download.value.busy && app.store.current.music.songs.any { it.uri == song.uri } }
        assertTrue(vm.download.value.error, vm.download.value.error.isBlank())
        assertEquals(song.uri, vm.download.value.uri)
        assertTrue(OnlineMusicEngine.localSongs(app).any { it.uri == song.uri })
        // A second request reuses the completed file and does not duplicate the library.
        ui.runOnIdle { vm.downloadSong(MusicResult(testId, song.title, "Tu Vida", song.duration)) }
        ui.waitUntil(20000) { !vm.download.value.busy }
        assertEquals(1, app.store.current.music.songs.count { it.uri == song.uri })
    }
    @Test fun discoveryStatesThemesAndLargeText() {
        ui.onNodeWithContentDescription("Música").performClick()
        ui.onNodeWithText("Buscar y descargar").performClick()
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("Tu música empieza aquí"))
        ui.onNodeWithText("Tu música empieza aquí").assertExists()
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasTestTag("music-online-query"))
        ui.onNodeWithText("Buscar música").assertIsNotEnabled()
        ui.onNodeWithTag("music-online-query").performTextInput("Canción de ejemplo")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        ui.runOnIdle { vm.discovery.value = MusicDiscovery(searched = true, results = listOf(
            MusicResult("abcdefghijk", "Una canción de ejemplo con título largo para comprobar la lectura", "Artista de ejemplo", 210000),
            MusicResult("lmnopqrstuv", "Otra canción de ejemplo", "Otro artista", 180000))) }
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("Resultados de YouTube"))
        ui.onNodeWithText("Resultados de YouTube").assertExists()
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("Encuentra tu próxima canción"))
        capture("discovery-dark")
        ui.runOnIdle { app.store.update { it.copy(preferences = it.preferences.copy(theme = "light")) } }
        capture("discovery-light")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put system font_scale 1.3").close()
        ui.waitUntil(10000) { ui.activity.resources.configuration.fontScale > 1.2f }
        Thread.sleep(800)
        ui.onNodeWithText("Encuentra tu próxima canción").performScrollTo(); capture("discovery-large")
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasTestTag("download-abcdefghijk"))
        ui.onNodeWithTag("download-abcdefghijk").assertIsDisplayed()
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("Actualizar motor"))
        ui.onNodeWithText("Actualizar motor").assertIsDisplayed()
        ui.runOnIdle { vm.discovery.value = MusicDiscovery(searched = true) }
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("No hay resultados"))
        ui.onNodeWithText("No hay resultados").assertExists()
        ui.runOnIdle { vm.discovery.value = MusicDiscovery(error = "No se pudo conectar. Revisa Internet y vuelve a intentarlo.") }
        ui.onNodeWithTag("music-discovery").performScrollToNode(hasText("No se pudo completar"))
        ui.onNodeWithText("No se pudo completar").assertExists()
    }
    /** Opt-in: independent provider smoke, excluded from offline/reproducible checks. */
    @Test fun realProviderSmoke() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("onlineMusicSmoke") == "true")
        val results = OnlineMusicEngine.search(app, "youtube-dl test video", "test-online-search")
        assertTrue("YouTube no devolvió resultados", results.isNotEmpty())
        val candidate = results.filter { it.duration in 1..60000 }.minByOrNull { it.duration } ?: results.first()
        val demo = OnlineMusicEngine.search(app, candidate.url, "test-online-demo").single()
        android.util.Log.i("TuVidaVerification", "Audio de prueba: ${demo.id}, ${demo.title}")
        ui.runOnIdle { vm.downloadSong(demo) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 3").close()
        ui.waitUntil(180000) { !vm.download.value.busy }
        assertTrue(vm.download.value.error, vm.download.value.error.isBlank())
        assertTrue(vm.download.value.uri.isNotBlank())
        val song = app.store.current.music.songs.single { it.uri == vm.download.value.uri }
        assertTrue(AudioFiles.read(app, android.net.Uri.parse(song.uri)).duration > 0)
        OnlineMusicEngine.file(app, demo.id).delete()
    }
}
