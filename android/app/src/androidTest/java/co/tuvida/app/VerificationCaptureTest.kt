package co.tuvida.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.tuvida.app.data.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*

/** Representative data belongs only to the verification emulator, never to a new install. */
@RunWith(AndroidJUnit4::class)
class VerificationCaptureTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        ui.waitForIdle()
        val test = InstrumentationRegistry.getInstrumentation()
        test.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/tuvida-$name.png").let { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
    }
    @Test fun nativeScreensAndThemes() {
        val today = LocalDate.now(); val monthStart = YearMonth.from(today).atDay(1).toString()
        val app = ui.activity.application as TuVidaApplication
        ui.runOnIdle { app.store.restore(AppData(
            movements = listOf(Movement(name = "Ingreso de ejemplo", amount = 3200000, type = "income", kind = "income", startDate = monthStart, category = "salario"), Movement(name = "Crédito de ejemplo", amount = 240000, kind = "credit", startDate = monthStart, day = 12, category = "deudas"), Movement(name = "Servicios de ejemplo", amount = 180000, startDate = monthStart, day = 20, category = "servicios")),
            savings = Savings(300000, "monthly", monthStart.take(7)),
            spending = listOf(Spending(date = today.toString(), amount = 18000, category = "mercado", note = "Ejemplo de almuerzo")),
            goals = listOf(Goal(name = "Viaje de ejemplo", target = 2000000, saved = 450000, monthly = 150000)),
            tasks = listOf(Task(title = "Ejemplo: repasar inglés", due = System.currentTimeMillis() + 3600000, kind = "study")),
            events = listOf(AgendaEvent("sample-match", "Ejemplo de partido", System.currentTimeMillis() + 86400000, System.currentTimeMillis() + 90000000, "barcelona")),
            health = listOf(HealthDay(date = today.toString(), breakfast = "Ejemplo: avena y fruta", lunch = "Ejemplo: arroz y verduras", exercise = true, minutes = 40, activity = "Caminata de ejemplo"), HealthDay(date = today.minusDays(1).toString(), exercise = false)),
            preferences = Preferences(theme = "light", football = emptySet(), notifications = false)
        )) }
        capture("phone-home")
        ui.onNodeWithText("Dinero").performClick(); ui.onNodeWithText("Dinero disponible del mes").assertExists(); capture("phone-finance")
        ui.onNodeWithText("Agenda").performClick(); ui.onNodeWithText("Ejemplo: repasar inglés").assertExists(); capture("phone-agenda")
        ui.onNodeWithText("Enfoque").performClick(); ui.onNodeWithText("Un momento de enfoque").assertExists(); capture("phone-focus")
        ui.onNodeWithText("Salud").performClick(); ui.onNodeWithText("Pequeñas acciones, día a día").assertExists(); capture("phone-health")
        ui.runOnIdle { app.store.update { it.copy(preferences = it.preferences.copy(theme = "dark")) } }; ui.waitForIdle(); capture("phone-health-dark")
        ui.onNodeWithText("Dinero").performClick(); capture("phone-finance-dark")
        ui.onNodeWithContentDescription("Ajustes").performClick(); capture("phone-settings-dark")
    }
}
