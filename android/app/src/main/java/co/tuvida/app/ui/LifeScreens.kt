@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package co.tuvida.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import co.tuvida.app.platform.Calendars
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(data: AppData, vm: AppViewModel, navigate: (String) -> Unit, edit: (Editor) -> Unit) {
    val today = LocalDate.now(); val summary = Finance.summary(data, YearMonth.from(today)); val now = System.currentTimeMillis()
    LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Spanish)).replaceFirstChar { it.uppercase(Spanish) }, style = MaterialTheme.typography.headlineSmall); Text("Un lugar para cuidar tu día.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tu plan para hoy", style = MaterialTheme.typography.titleLarge)
                    AmountRow("Presupuesto del día", summary.today)
                    AmountRow("Ahorro protegido del mes", summary.saving)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ edit(Editor("spending")) }) { Text("Anotar gasto") }; TextButton({ navigate("finance") }) { Text("Ver finanzas") } }
                }
            }
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { edit(Editor("task")) }, label = { Text("Añadir tarea") }, leadingIcon = { Icon(Icons.Outlined.AddTask, null) }); AssistChip(onClick = { navigate("focus") }, label = { Text("Estudiar") }, leadingIcon = { Icon(Icons.Outlined.Timer, null) }); AssistChip(onClick = { edit(Editor("health")) }, label = { Text("Registrar salud") }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null) }) } }
        item { Section("Lo que viene", action = { TextButton({ navigate("agenda") }) { Text("Ver agenda") } }) }
        item { AssistChip(onClick = { navigate("music") }, label = { Text("Escuchar mi música") }, leadingIcon = { Icon(Icons.Outlined.MusicNote, null) }) }
        val events = data.events.filter { it.end > now }.sortedBy { it.start }.take(3)
        val tasks = data.tasks.filter { !it.done }.sortedBy { it.due }.take(3)
        if (tasks.isEmpty() && events.isEmpty()) item { Info("Empieza por tu próximo plan", "Añade una tarea o conecta tu calendario en Ajustes.", Icons.Outlined.CalendarMonth) }
        items(tasks, key = { "home-${it.id}" }) { task -> TaskRow(task, vm, edit) }
        items(events, key = { "home-${it.id}" }) { event -> EventRow(event) }
        item { Section("Tu salud hoy") }
        item { val health = data.health.find { it.date == today.toString() }; Info(if (health == null) "Todavía sin registro" else if (health.exercise) "Ya te moviste: ${health.minutes} minutos" else "Hoy registraste un día sin ejercicio", if (health == null) "Anota tus comidas y cómo te fue con el ejercicio." else health.activity.ifBlank { "Cada registro te ayuda a conocer tu rutina." }, Icons.Outlined.FavoriteBorder) }
        val advice = Finance.alerts(data, YearMonth.from(today)).take(3)
        if (advice.isNotEmpty()) item { Section("Para tener presente") }
        items(advice) { item -> Info(item.title, item.body) }
        if (!data.preferences.notifications) item { Info("Los avisos están apagados", "Actívalos en Ajustes para recibir tus recordatorios.", Icons.Outlined.NotificationsOff) }
    }
}
@Composable private fun TaskRow(task: Task, vm: AppViewModel, edit: (Editor) -> Unit) {
    ListItem(headlineContent = { Text(task.title) }, supportingContent = { Text("${dateLabel(task.due)} · ${task.durationMinutes} min${if (task.repeat != "none") " · recurrente" else ""}") }, leadingContent = { Checkbox(task.done, { if (task.done) vm.change { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) it.copy(done = false) else it }) } else vm.complete(task) }, Modifier.semantics { contentDescription = "Completar ${task.title}" }) }, trailingContent = { IconButton({ edit(Editor("task", task.id)) }) { Icon(Icons.Outlined.Edit, "Editar tarea") } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
}
@Composable private fun EventRow(event: AgendaEvent, action: (@Composable () -> Unit)? = null) {
    ListItem(headlineContent = { Text(event.title) }, supportingContent = { Text("${if (event.allDay) Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault()).toLocalDate().toString() + " · Todo el día" else dateLabel(event.start)}\n${Calendars.teams[event.source] ?: "Calendario personal"}") }, leadingContent = { Icon(if (event.source in Calendars.teams) Icons.Outlined.SportsSoccer else Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = action, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
}
@Composable fun AgendaScreen(data: AppData, vm: AppViewModel, edit: (Editor) -> Unit, settings: () -> Unit, study: (AgendaEvent) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }; var filter by remember { mutableStateOf("all") }; var query by remember { mutableStateOf("") }; var completed by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    Column {
        TabRow(tab) { listOf("Agenda", "Tareas", "Partidos").forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) } }
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(settings) { Text("Conectar calendarios") }; TextButton(vm::sync, enabled = !busy) { Text(if (busy) "Actualizando…" else "Actualizar") } } }
            item { Field("Buscar en agenda", query, { query = it }) }
            if (tab == 1) {
                item { Toggle("Mostrar completadas", completed, { completed = it }) }
                val tasks = data.tasks.filter { it.done == completed && it.title.contains(query, true) }.sortedBy { it.due }
                if (tasks.isEmpty()) item { Info("Sin tareas en esta vista", "Pulsa + para añadir una tarea, una sesión de estudio o ejercicio.") }
                items(tasks, key = { it.id }) { task ->
                    TaskRow(task, vm, edit)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (task.kind == "study") TextButton({ vm.change { d -> d.copy(focus = FocusState(remaining = task.durationMinutes * 60000L), preferences = d.preferences.copy(workMinutes = task.durationMinutes.coerceAtMost(180))) }; vm.message.value = "Sesión preparada. Abre Enfoque para empezar." }) { Text("Preparar enfoque") }; TextButton({ vm.undoable { d -> d.copy(tasks = d.tasks.filter { it.id != task.id }) } }) { Text("Eliminar") } }
                    HorizontalDivider()
                }
            } else {
                if (tab == 2) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (mapOf("all" to "Todos") + Calendars.teams).forEach { (id, label) -> FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) }) } } }
                val now = System.currentTimeMillis()
                val events = data.events.filter { it.end >= now && it.title.contains(query, true) && (tab == 0 || it.source in Calendars.teams) && (tab != 2 || filter == "all" || it.source == filter) }.sortedBy { it.start }
                if (tab == 0) {
                    val tasks = data.tasks.filter { !it.done && it.title.contains(query, true) }.sortedBy { it.due }.take(20)
                    if (tasks.isNotEmpty()) item { Section("Tus tareas y sesiones") }
                    items(tasks, key = { "agenda-task-${it.id}" }) { TaskRow(it, vm, edit) }
                    item { Section("Calendarios") }
                }
                if (events.isEmpty()) item { Info(if (tab == 2) "Sin próximos partidos disponibles" else "Sin eventos sincronizados", "Actualiza las fuentes o selecciona tus calendarios en Ajustes. Las fechas dependen del proveedor.", Icons.Outlined.Event) }
                items(events, key = { it.id }) { event -> EventRow(event, if (event.source !in Calendars.teams && event.start > now) { { IconButton({ study(event) }) { Icon(Icons.Outlined.School, "Crear sesión de estudio para este evento") } } } else null); HorizontalDivider() }
                items(data.sync.filter { it.error.isNotBlank() }) { status -> Info(Calendars.teams[status.source] ?: "Calendario personal", status.error + if (status.updated > 0) " Última actualización: ${dateLabel(status.updated)}." else "") }
            }
        }
    }
}
@Composable fun FocusScreen(data: AppData, vm: AppViewModel, configure: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.focus.running, data.focus.deadline) { while (true) { now = System.currentTimeMillis(); vm.reconcileFocus(); delay(1000) } }
    val f = data.focus
    val value = if (f.mode == "pomodoro") if (f.running) f.deadline - now else f.remaining else f.elapsed + if (f.running) now - f.started else 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(f.mode == "pomodoro", { vm.timer("pomodoro") }, label = { Text("Pomodoro") }); FilterChip(f.mode == "stopwatch", { vm.timer("stopwatch") }, label = { Text("Cronómetro") }) }
        Spacer(Modifier.height(16.dp))
        Icon(if (f.mode == "pomodoro") Icons.Outlined.LocalLibrary else Icons.Outlined.Timer, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (f.mode == "stopwatch") "Tu tiempo, a tu ritmo" else when (f.phase) { "focus" -> "Un momento de enfoque"; "longBreak" -> "Disfruta una pausa larga"; else -> "Respira y descansa" }, style = MaterialTheme.typography.headlineSmall)
        Text(clock(value), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FilledTonalButton({ vm.timer("reset") }) { Icon(Icons.Outlined.RestartAlt, null); Text("Reiniciar") }; Button({ vm.timer(if (f.running) "pause" else "start") }) { Icon(if (f.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null); Text(if (f.running) "Pausar" else "Empezar") } }
        if (f.mode == "stopwatch") { OutlinedButton({ vm.timer("lap") }, enabled = f.running) { Text("Registrar vuelta") }; f.laps.forEachIndexed { index, lap -> Text("Vuelta ${index + 1}: ${clock(lap)}", Modifier.fillMaxWidth()) } }
        else {
            Text("${f.completed} sesiones completadas · pausa larga cada ${data.preferences.cycles} ciclos", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(configure) { Icon(Icons.Outlined.Tune, null); Spacer(Modifier.width(8.dp)); Text("Configurar tiempos") }
            Info("Una etapa a la vez", "Al terminar recibirás un aviso. Tú decides cuándo empezar la siguiente etapa; el tiempo se conserva al cerrar la app.")
            val minutes = data.studyLog.filter { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }.sumOf { it.minutes }
            Text("Hoy estudiaste $minutes minutos", style = MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable fun HealthScreen(data: AppData, edit: (String) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }; var selected by remember { mutableStateOf(LocalDate.now().toString()) }
    val days = data.health.filter { it.date.startsWith(month.toString()) }; val total = days.sumOf { it.minutes }; val active = days.count { it.exercise }
    LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Pequeñas acciones, día a día", style = MaterialTheme.typography.headlineSmall); Text("Registra lo que comes y el tiempo que dedicas a moverte.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { MonthPicker(month) { month = it; selected = it.atDay(1).toString() }; Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Metric("Días de ejercicio", "$active", Modifier.weight(1f)); Metric("Tiempo del mes", "$total min", Modifier.weight(1f)) } }
        item { HealthCalendar(month, data.health, selected) { selected = it } }
        item { Text("Verde: ejercicio · rojo: sin ejercicio registrado · neutro: sin registro", style = MaterialTheme.typography.bodySmall) }
        item { Section("Tu registro del $selected", action = { TextButton({ edit(selected) }, enabled = LocalDate.parse(selected) <= LocalDate.now()) { Text("Editar") } }) }
        val record = data.health.find { it.date == selected }
        if (record == null) item { Info("Este día está sin registrar", "Un día sin registro conserva su color neutro. Registra el día para marcar ejercicio o descanso.") }
        else {
            item { Info(if (record.exercise) "Ejercicio · ${record.minutes} min" else "Día sin ejercicio", record.activity.ifBlank { "Actividad no anotada" }, Icons.Outlined.DirectionsRun) }
            item { listOf("Desayuno" to record.breakfast, "Almuerzo" to record.lunch, "Cena" to record.dinner, "Entre comidas" to record.snacks).forEach { (title, value) -> Section(title); Text(value.ifBlank { "Sin anotar" }, color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }; if (record.notes.isNotBlank()) { Section("Notas"); Text(record.notes) } }
        }
    }
}
@Composable fun HealthCalendar(month: YearMonth, records: List<HealthDay>, selected: String, pick: (String) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
        (0 until cells step 7).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { (0..6).forEach { col ->
            val day = row + col - offset + 1
            if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp)) else {
                val key = month.atDay(day).toString(); val record = records.find { it.date == key }
                val color = if (record == null) MaterialTheme.colorScheme.surfaceVariant else if (record.exercise) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                val foreground = if (record == null) MaterialTheme.colorScheme.onSurfaceVariant else if (record.exercise) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                Surface(onClick = { pick(key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "$key, ${if (record == null) "sin registro" else if (record.exercise) "ejercicio ${record.minutes} minutos" else "sin ejercicio"}${if (key == selected) ", seleccionado" else ""}" }, color = color, contentColor = foreground, shape = MaterialTheme.shapes.small, border = if (key == selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                    Box(contentAlignment = Alignment.Center) { Text(day.toString(), style = MaterialTheme.typography.labelLarge) }
                }
            }
        } } }
    }
}
