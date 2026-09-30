package co.tuvida.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.tuvida.app.data.*
import org.junit.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var app: TuVidaApplication
    @Before fun setup() { app = ui.activity.application as TuVidaApplication; ui.runOnIdle { app.store.restore(AppData(preferences = Preferences(football = emptySet(), notifications = false))) }; ui.waitForIdle() }
    @Test fun expenseSavedFromHomeReducesFinancialBudget() {
        ui.onNodeWithText("Anotar gasto").performClick()
        ui.onNodeWithText("Valor en pesos").performTextInput("12500")
        ui.onNodeWithText("Nota").performTextInput("Almuerzo de prueba")
        ui.onNodeWithText("Guardar").performScrollTo().performClick()
        try { ui.waitUntil(10000) { app.store.current.spending.any { it.note == "Almuerzo de prueba" && it.amount == 12500L } } } catch (e: Exception) { Assert.fail("Gasto de prueba guardado: ${app.store.current.spending}") }
        ui.onNodeWithText("Dinero").performClick(); ui.onNodeWithText("Diario").performClick()
        ui.onNodeWithText("${LocalDate.now()} · Otros gastos\nAlmuerzo de prueba").assertExists()
    }
    @Test fun healthEntryStoresMealsAndExercise() {
        ui.onNodeWithText("Salud").performClick()
        ui.onNodeWithContentDescription("Registrar hoy").performClick()
        ui.onNodeWithText("Desayuno").performTextInput("Avena y fruta")
        ui.onNodeWithContentDescription("Hice ejercicio").performScrollTo().performClick()
        ui.onNodeWithText("Tiempo en minutos").performScrollTo().performTextClearance()
        ui.onNodeWithText("Tiempo en minutos").performTextInput("30")
        ui.onNodeWithText("Actividad").performScrollTo().performTextInput("Caminar")
        ui.onNodeWithText("Guardar").performScrollTo().performClick()
        try { ui.waitUntil(10000) { app.store.current.health.any { it.exercise && it.minutes == 30 && it.breakfast == "Avena y fruta" } } } catch (e: Exception) { Assert.fail("Registro de prueba guardado: ${app.store.current.health}") }
        ui.onNodeWithText("Ejercicio · 30 min").assertExists()
    }
    @Test fun taskCreationAndCompletionPersist() {
        ui.onNodeWithText("Agenda").performClick(); ui.onNodeWithContentDescription("Añadir tarea o sesión").performClick()
        ui.onNodeWithText("Qué necesitas hacer").performTextInput("Leer capítulo")
        ui.onNodeWithText("Guardar").performScrollTo().performClick()
        ui.waitUntil(10000) { app.store.current.tasks.any { it.title == "Leer capítulo" } }
        ui.onNodeWithText("Tareas").performClick()
        ui.onNodeWithContentDescription("Completar Leer capítulo").performClick()
        ui.waitUntil(10000) { app.store.current.tasks.single().done }
    }
    @Test fun stopwatchLapCreatesSingleLapAndSurvivesPause() {
        ui.onNodeWithText("Enfoque").performClick(); ui.onNodeWithText("Cronómetro").performClick(); ui.onNodeWithText("Empezar").performClick()
        ui.waitUntil(10000) { app.store.current.focus.running }
        ui.onNodeWithText("Registrar vuelta").performClick()
        ui.waitUntil(10000) { app.store.current.focus.laps.size == 1 }
        ui.onNodeWithText("Pausar").performClick(); ui.waitUntil(10000) { !app.store.current.focus.running }
        Assert.assertEquals(1, app.store.current.focus.laps.size)
    }
}
