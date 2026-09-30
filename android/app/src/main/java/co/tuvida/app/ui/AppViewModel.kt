package co.tuvida.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import co.tuvida.app.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TuVidaApplication
    val state = app.store.state
    val message = MutableStateFlow("")
    val calendars = MutableStateFlow<List<CalendarSource>>(emptyList())
    val busy = MutableStateFlow(false)
    val importPreview = MutableStateFlow<AppData?>(null)
    private var undo: ((AppData) -> AppData)? = null
    init { message.value = app.store.recoveryError; refreshSources() }
    fun change(action: (AppData) -> AppData) {
        viewModelScope.launch(Dispatchers.IO) { try { app.store.update { old ->
            val next = action(old)
            next.copy(events = next.events.filter { e -> (e.source !in Calendars.teams || e.source in next.preferences.football) && (e.source != "device" || next.preferences.calendarIds.isNotEmpty()) })
        }; Reminders.reschedule(app); Widgets.refresh(app) } catch (e: Exception) { message.value = e.message ?: "No se pudo guardar. Inténtalo otra vez." } }
    }
    fun undoable(action: (AppData) -> AppData) {
        change { before ->
            val after = action(before)
            val removedMovements = before.movements.filter { m -> after.movements.none { it.id == m.id } }
            val removedTasks = before.tasks.filter { t -> after.tasks.none { it.id == t.id } }
            val removedSpending = before.spending.filter { s -> after.spending.none { it.id == s.id } }
            val removedGoals = before.goals.filter { g -> after.goals.none { it.id == g.id } }
            undo = { current -> current.copy(movements = current.movements + removedMovements.filter { m -> current.movements.none { it.id == m.id } }, tasks = current.tasks + removedTasks.filter { t -> current.tasks.none { it.id == t.id } }, spending = current.spending + removedSpending.filter { s -> current.spending.none { it.id == s.id } }, goals = current.goals + removedGoals.filter { g -> current.goals.none { it.id == g.id } }) }
            after
        }; message.value = "Cambio guardado. Puedes deshacerlo desde Ajustes."
    }
    fun undo() { undo?.let { restore -> change(restore); undo = null; message.value = "Cambio deshecho." } }
    fun preferences(action: (Preferences) -> Preferences) { change { it.copy(preferences = action(it.preferences)) } }
    fun refreshSources() { viewModelScope.launch(Dispatchers.IO) { runCatching { Calendars.sources(app) }.onSuccess { calendars.value = it }.onFailure { message.value = "No se pudieron leer los calendarios. Revisa el permiso." } } }
    fun sync() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) { try { SyncWorker.sync(app); refreshSources() } catch (e: Exception) { message.value = e.message ?: "No se pudo sincronizar." } finally { busy.value = false } }
    }
    fun connectGoogle(url: String) { viewModelScope.launch(Dispatchers.IO) { try { Secrets.save(app, url.trim()); if (url.isBlank()) { app.store.update { it.copy(events = it.events.filter { e -> e.source != "google" }, sync = it.sync.filter { s -> s.source != "google" }) }; Reminders.reschedule(app) }; sync(); message.value = if (url.isBlank()) "Google iCal desconectado." else "Dirección guardada. Actualizando calendario." } catch (_: Exception) { message.value = "Pega la dirección secreta iCal completa de Google Calendar." } } }
    fun complete(task: Task) = change { data -> data.copy(tasks = data.tasks.map { if (it.id == task.id) Scheduling.completeTask(it, System.currentTimeMillis()) else it }, snoozed = data.snoozed.filter { it.taskId != task.id }) }
    fun payment(occurrence: Occurrence) = change { d -> d.copy(movements = d.movements.map { if (it.id == occurrence.movement.id) it.copy(payments = if (occurrence.paid) it.payments - occurrence.paymentKey else it.payments + occurrence.paymentKey) else it }) }
    fun timer(action: String) = change { data ->
        val now = System.currentTimeMillis(); val current = Scheduling.finishFocus(data, now); val f = current.focus
        val next = when (action) {
            "start" -> if (f.running) f else f.copy(running = true, started = now, deadline = if (f.mode == "pomodoro") now + f.remaining else 0)
            "pause" -> if (!f.running) f else if (f.mode == "pomodoro") f.copy(running = false, remaining = (f.deadline - now).coerceAtLeast(0), deadline = 0) else f.copy(running = false, elapsed = f.elapsed + now - f.started)
            "reset" -> FocusState(mode = f.mode, remaining = current.preferences.workMinutes * 60000L, completed = f.completed)
            "stopwatch" -> FocusState(mode = "stopwatch", completed = f.completed)
            "pomodoro" -> FocusState(remaining = current.preferences.workMinutes * 60000L, completed = f.completed)
            "lap" -> f.copy(laps = (f.laps + (f.elapsed + if (f.running) now - f.started else 0)).takeLast(100))
            else -> f
        }
        current.copy(focus = next)
    }
    fun reconcileFocus() { val data = app.store.current; if (data.focus.running && data.focus.mode == "pomodoro" && data.focus.deadline <= System.currentTimeMillis()) change { Scheduling.finishFocus(it, System.currentTimeMillis()) } }
    fun export(uri: Uri, csv: Boolean) { viewModelScope.launch(Dispatchers.IO) { try {
        val data = app.store.current
        val content = if (csv) csv(data) else Backup.encode(data.copy(events = emptyList(), sync = emptyList(), snoozed = emptyList(), preferences = data.preferences.copy(calendarIds = emptySet())))
        app.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: error("No se pudo abrir el archivo.")
        app.store.update { it.copy(lastBackup = System.currentTimeMillis()) }; message.value = "Copia guardada en el archivo elegido."
    } catch (_: Exception) { message.value = "No se pudo guardar el archivo. Elige otra ubicación." } } }
    fun import(uri: Uri) { viewModelScope.launch(Dispatchers.IO) { try {
        val text = app.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var count = input.read(buffer)
            while (count != -1) { require(out.size() + count <= Backup.MAX_BYTES) { "La copia supera 5 MB." }; out.write(buffer, 0, count); count = input.read(buffer) }; out.toString("UTF-8")
        } ?: error("No se pudo abrir el archivo.")
        importPreview.value = Backup.decode(text)
    } catch (e: Exception) { message.value = "No se importó ningún dato. ${e.message ?: "Copia inválida."}" } } }
    fun confirmImport() { val imported = importPreview.value ?: return; viewModelScope.launch(Dispatchers.IO) { try {
        val before = app.store.current
        app.store.restore(imported)
        undo = { current ->
            require(current.movements == imported.movements && current.spending == imported.spending && current.goals == imported.goals && current.tasks == imported.tasks && current.health == imported.health) { "Ya hay ediciones posteriores. Restaura una copia para volver a los datos anteriores." }
            before
        }
        importPreview.value = null; Reminders.reschedule(app); Widgets.refresh(app); sync(); message.value = "Copia restaurada. Puedes deshacer desde Ajustes."
    } catch (_: Exception) { message.value = "No se pudo restaurar la copia." } } }
    private fun csv(data: AppData): String {
        fun field(value: Any) = "\"${value.toString().replace("\"", "\"\"").let { if (it.firstOrNull() in listOf('=', '+', '-', '@')) "'$it" else it }}\""
        val lines = mutableListOf("Mes,Fecha,Nombre,Tipo,Valor,Categoria,Estado")
        (0L..11L).forEach { offset ->
            val month = YearMonth.now().plusMonths(offset)
            Finance.occurrences(data, month).forEach { o -> lines += listOf(month, o.date, o.movement.name, o.movement.type, o.movement.amount, o.movement.category, if (o.paid) "Registrado" else "Pendiente").joinToString(",") { field(it) } }
            data.spending.filter { it.date.startsWith(month.toString()) }.forEach { s -> lines += listOf(month, s.date, s.note, "Gasto diario", s.amount, s.category, "Registrado").joinToString(",") { field(it) } }
        }
        return "\uFEFF" + lines.joinToString("\r\n")
    }
}
