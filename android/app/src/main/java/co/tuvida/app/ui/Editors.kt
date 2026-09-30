@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package co.tuvida.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.*
import co.tuvida.app.domain.Finance
import java.time.*

@Composable fun EditorForm(editor: Editor, data: AppData, vm: AppViewModel, close: () -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (editor.type) {
            "movement" -> MovementForm(data.movements.find { it.id == editor.id }, vm, close)
            "spending" -> SpendingForm(data.spending.find { it.id == editor.id }, editor.date, vm, close)
            "goal" -> GoalForm(data.goals.find { it.id == editor.id }, vm, close)
            "contribute" -> ContributionForm(data.goals.first { it.id == editor.id }, vm, close)
            "task" -> TaskForm(data.tasks.find { it.id == editor.id }, vm, close)
            "health" -> HealthForm(data.health.find { it.date == editor.date }, editor.date, vm, close)
            "timer" -> TimerForm(data.preferences, vm, close)
            "savings" -> SavingsForm(data.savings, vm, close)
            "financeSettings" -> FinanceSettingsForm(data.financeSettings, vm, close)
        }
    }
}
@Composable private fun FormSave(error: String, label: String = "Guardar", save: () -> Unit, close: () -> Unit) {
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(close, Modifier.weight(1f)) { Text("Cancelar") }; Button(save, Modifier.weight(1f)) { Text(label) } }
}
private fun validAmount(value: String, zero: Boolean = false): Long? = value.toLongOrNull()?.takeIf { it in (if (zero) 0L else 1L)..1_000_000_000_000L }

@Composable private fun MovementForm(existing: Movement?, vm: AppViewModel, close: () -> Unit) {
    val base = existing ?: Movement(day = LocalDate.now().dayOfMonth)
    var name by remember { mutableStateOf(base.name) }; var amount by remember { mutableStateOf(if (existing == null) "" else base.amount.toString()) }
    var type by remember { mutableStateOf(if (base.kind == "credit") "credit" else base.type) }; var frequency by remember { mutableStateOf(base.frequency) }
    var start by remember { mutableStateOf(base.startDate) }; var end by remember { mutableStateOf(base.endDate) }; var hasEnd by remember { mutableStateOf(base.endDate.isNotEmpty()) }
    var day by remember { mutableStateOf(base.day.toString()) }; var lastBusiness by remember { mutableStateOf(base.schedule == "last-business-day") }
    var total by remember { mutableStateOf(base.installments.toString()) }; var current by remember { mutableStateOf(base.currentInstallment.toString()) }
    var category by remember { mutableStateOf(base.category) }; var error by remember { mutableStateOf("") }
    Text(if (existing == null) "Nuevo movimiento" else "Editar movimiento", style = MaterialTheme.typography.headlineSmall)
    Field("Nombre", name, { name = it.take(250) }); Field("Valor en pesos colombianos", amount, { amount = it.filter(Char::isDigit) }, true)
    Choice("Tipo", type, linkedMapOf("income" to "Ingreso", "expense" to "Gasto", "credit" to "Crédito")) { type = it; category = if (it == "income") "salario" else if (it == "credit") "deudas" else "otros"; if (it == "credit") frequency = "monthly" }
    Choice("Categoría", category, Finance.categories.filterKeys { if (type == "income") it in listOf("salario", "extra", "ventas", "otros-ingreso") else it !in listOf("salario", "extra", "ventas", "otros-ingreso") }) { category = it }
    if (type != "credit") Choice("Frecuencia", frequency, Finance.frequencies) { frequency = it }
    DateField("Empieza", start) { start = it }
    if (frequency !in listOf("once", "weekly", "biweekly")) {
        Toggle("Último día hábil del mes", lastBusiness, { lastBusiness = it }, "Respeta fines de semana y festivos colombianos.")
        if (!lastBusiness) Field("Día del mes (1–31)", day, { day = it.filter(Char::isDigit) }, true, helper = "Si el mes es más corto, se usa su último día.")
    }
    if (type == "credit") {
        Field("Total de cuotas", total, { total = it.filter(Char::isDigit) }, true)
        Field("Cuota inicial en el mes de comienzo", current, { current = it.filter(Char::isDigit) }, true)
        val last = runCatching { YearMonth.from(LocalDate.parse(start)).plusMonths(((total.toIntOrNull() ?: 12) - (current.toIntOrNull() ?: 1)).coerceAtLeast(0).toLong()) }.getOrNull()
        if (last != null) Text("Última cuota: ${monthLabel(last)}", color = MaterialTheme.colorScheme.primary)
        var lastMonth by remember { mutableStateOf(last?.toString() ?: YearMonth.now().toString()) }
        Field("Cambiar última cuota (AAAA-MM)", lastMonth, { lastMonth = it }, helper = "Al aplicar, recalcula el total de cuotas.")
        TextButton({ val chosen = runCatching { YearMonth.parse(lastMonth) }.getOrNull(); if (chosen != null) { val gap = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(LocalDate.parse(start)), chosen); if (gap >= 0) total = (gap + (current.toIntOrNull() ?: 1)).toString() } }) { Text("Aplicar última cuota") }
    } else if (frequency != "once") {
        Toggle("Tiene fecha final", hasEnd, { hasEnd = it; if (end.isEmpty()) end = start })
        if (hasEnd) DateField("Termina", end) { end = it }
    }
    val impactAmount = validAmount(amount) ?: 0
    val preview = runCatching { val m = base.copy(amount = impactAmount, frequency = frequency, startDate = start, day = day.toIntOrNull() ?: 1, schedule = if (lastBusiness) "last-business-day" else "day"); val months = (0L..11L).map { YearMonth.from(LocalDate.parse(start)).plusMonths(it) }; val first = Finance.dates(m, months.first(), true).size * impactAmount; val year = months.sumOf { Finance.dates(m, it, true).size * impactAmount }; "Impacto: ${money(first)} el primer mes · ${money(year)} en 12 meses" }.getOrDefault("")
    if (impactAmount > 0) Text(preview, style = MaterialTheme.typography.bodySmall)
    FormSave(error, save = {
        val value = validAmount(amount); val d = day.toIntOrNull(); val t = total.toIntOrNull(); val c = current.toIntOrNull()
        if (name.isBlank() || value == null || d !in 1..31 || (type == "credit" && (t !in 1..1200 || c !in 1..(t ?: 1))) || (hasEnd && end < start)) error = "Revisa nombre, valor, día, cuotas y fechas." else {
            val item = base.copy(name = name.trim(), amount = value, type = if (type == "income") "income" else "expense", kind = type, frequency = if (type == "credit") "monthly" else frequency, startDate = start, endDate = if (hasEnd && type != "credit") end else "", day = d!!, schedule = if (lastBusiness) "last-business-day" else "day", installments = t ?: 12, currentInstallment = c ?: 1, category = category)
            vm.change { it.copy(movements = it.movements.filter { m -> m.id != item.id } + item) }; close()
        }
    }, close = close)
}
@Composable private fun SpendingForm(existing: Spending?, date: String, vm: AppViewModel, close: () -> Unit) {
    val base = existing ?: Spending(date = date)
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }; var note by remember { mutableStateOf(base.note) }; var category by remember { mutableStateOf(base.category) }; var day by remember { mutableStateOf(base.date) }; var error by remember { mutableStateOf("") }
    Text("Anotar gasto", style = MaterialTheme.typography.headlineSmall)
    Field("Valor en pesos", amount, { amount = it.filter(Char::isDigit) }, true); Field("Nota", note, { note = it.take(500) }); DateField("Fecha", day) { day = it }
    Choice("Categoría", category, Finance.categories.filterKeys { it !in listOf("salario", "extra", "ventas", "otros-ingreso") }) { category = it }
    FormSave(error, save = { val value = validAmount(amount); if (value == null) error = "Introduce un valor mayor que cero." else { val s = base.copy(amount = value, note = note, category = category, date = day); vm.change { it.copy(spending = it.spending.filter { v -> v.id != s.id } + s) }; close() } }, close = close)
}
@Composable private fun GoalForm(existing: Goal?, vm: AppViewModel, close: () -> Unit) {
    val base = existing ?: Goal(); var name by remember { mutableStateOf(base.name) }; var target by remember { mutableStateOf(existing?.target?.toString() ?: "") }; var monthly by remember { mutableStateOf(base.monthly.toString()) }; var saved by remember { mutableStateOf(base.saved.toString()) }; var deadline by remember { mutableStateOf(base.deadline.ifBlank { LocalDate.now().plusMonths(6).toString() }) }; var hasDeadline by remember { mutableStateOf(base.deadline.isNotEmpty()) }; var error by remember { mutableStateOf("") }
    Text("Meta de ahorro", style = MaterialTheme.typography.headlineSmall); Field("Nombre", name, { name = it.take(250) }); Field("Objetivo en pesos", target, { target = it.filter(Char::isDigit) }, true); Field("Ya ahorrado", saved, { saved = it.filter(Char::isDigit) }, true); Field("Aporte mensual", monthly, { monthly = it.filter(Char::isDigit) }, true); Toggle("Fecha objetivo", hasDeadline, { hasDeadline = it }); if (hasDeadline) DateField("Fecha", deadline) { deadline = it }
    FormSave(error, save = { val t = validAmount(target); val s = validAmount(saved, true); val m = validAmount(monthly, true); if (name.isBlank() || t == null || s == null || m == null) error = "Completa el nombre y los valores válidos." else { val goal = base.copy(name = name.trim(), target = t, saved = s, monthly = m, deadline = if (hasDeadline) deadline else ""); vm.change { it.copy(goals = it.goals.filter { g -> g.id != goal.id } + goal) }; close() } }, close = close)
}
@Composable private fun ContributionForm(goal: Goal, vm: AppViewModel, close: () -> Unit) {
    var amount by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    Text("Abonar a ${goal.name}", style = MaterialTheme.typography.headlineSmall); Text("El abono registra ahorro en esta meta. No crea un movimiento de gasto.", style = MaterialTheme.typography.bodyMedium); Field("Abono en pesos", amount, { amount = it.filter(Char::isDigit) }, true)
    FormSave(error, "Abonar", { val value = validAmount(amount); if (value == null) error = "Introduce un abono válido." else { vm.change { d -> d.copy(goals = d.goals.map { if (it.id == goal.id) it.copy(saved = it.saved + value) else it }) }; close() } }, close)
}
@Composable private fun TaskForm(existing: Task?, vm: AppViewModel, close: () -> Unit) {
    val base = existing ?: Task(due = System.currentTimeMillis() + 3600000)
    val due = Instant.ofEpochMilli(base.due).atZone(ZoneId.systemDefault())
    var title by remember { mutableStateOf(base.title) }; var date by remember { mutableStateOf(due.toLocalDate().toString()) }; var time by remember { mutableStateOf(due.toLocalTime().withSecond(0).withNano(0).toString()) }; var kind by remember { mutableStateOf(base.kind) }; var repeat by remember { mutableStateOf(base.repeat) }; var duration by remember { mutableStateOf(base.durationMinutes.toString()) }; var error by remember { mutableStateOf("") }
    Text("Tarea o sesión", style = MaterialTheme.typography.headlineSmall); Field("Qué necesitas hacer", title, { title = it.take(250) }); Choice("Tipo", kind, linkedMapOf("task" to "Tarea", "study" to "Estudio", "exercise" to "Ejercicio")) { kind = it }; DateField("Fecha", date) { date = it }; TimeField(time) { time = it }; Field("Duración en minutos", duration, { duration = it.filter(Char::isDigit) }, true); Choice("Repetición", repeat, linkedMapOf("none" to "Una vez", "daily" to "Diaria", "weekly" to "Semanal")) { repeat = it }
    FormSave(error, save = { val minutes = duration.toIntOrNull(); if (title.isBlank() || minutes !in 1..(if (kind == "study") 180 else 1440)) error = "Completa título y duración: estudio admite 1–180 minutos; las otras tareas, 1–1440." else { val task = base.copy(title = title.trim(), due = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), kind = kind, repeat = repeat, durationMinutes = minutes!!); vm.change { it.copy(tasks = it.tasks.filter { t -> t.id != task.id } + task) }; close() } }, close = close)
}
@Composable private fun HealthForm(existing: HealthDay?, date: String, vm: AppViewModel, close: () -> Unit) {
    val base = existing ?: HealthDay(date = date)
    var breakfast by remember { mutableStateOf(base.breakfast) }; var lunch by remember { mutableStateOf(base.lunch) }; var dinner by remember { mutableStateOf(base.dinner) }; var snacks by remember { mutableStateOf(base.snacks) }; var exercise by remember { mutableStateOf(base.exercise) }; var minutes by remember { mutableStateOf(base.minutes.toString()) }; var activity by remember { mutableStateOf(base.activity) }; var notes by remember { mutableStateOf(base.notes) }; var error by remember { mutableStateOf("") }
    Text("Registro del $date", style = MaterialTheme.typography.headlineSmall); Field("Desayuno", breakfast, { breakfast = it.take(1000) }); Field("Almuerzo", lunch, { lunch = it.take(1000) }); Field("Cena", dinner, { dinner = it.take(1000) }); Field("Entre comidas", snacks, { snacks = it.take(1000) }); Toggle("Hice ejercicio", exercise, { exercise = it }); if (exercise) { Field("Tiempo en minutos", minutes, { minutes = it.filter(Char::isDigit) }, true); Field("Actividad", activity, { activity = it.take(500) }) }; Field("Notas del día", notes, { notes = it.take(2000) }, lines = 2)
    FormSave(error, save = { val m = minutes.toIntOrNull(); if (LocalDate.parse(date) > LocalDate.now()) error = "Registra solamente hoy o días anteriores." else if (exercise && m !in 1..1440) error = "Indica de 1 a 1440 minutos de actividad." else { val day = base.copy(breakfast = breakfast, lunch = lunch, dinner = dinner, snacks = snacks, exercise = exercise, minutes = if (exercise) m!! else 0, activity = if (exercise) activity else "", notes = notes); vm.change { it.copy(health = it.health.filter { h -> h.date != date } + day) }; close() } }, close = close)
}
@Composable private fun TimerForm(prefs: Preferences, vm: AppViewModel, close: () -> Unit) {
    var work by remember { mutableStateOf(prefs.workMinutes.toString()) }; var pause by remember { mutableStateOf(prefs.breakMinutes.toString()) }; var long by remember { mutableStateOf(prefs.longBreakMinutes.toString()) }; var cycles by remember { mutableStateOf(prefs.cycles.toString()) }; var error by remember { mutableStateOf("") }
    Text("Configurar pomodoro", style = MaterialTheme.typography.headlineSmall); Field("Enfoque (1–180 min)", work, { work = it.filter(Char::isDigit) }, true); Field("Pausa (1–60 min)", pause, { pause = it.filter(Char::isDigit) }, true); Field("Pausa larga (1–120 min)", long, { long = it.filter(Char::isDigit) }, true); Field("Ciclos antes de pausa larga (1–12)", cycles, { cycles = it.filter(Char::isDigit) }, true)
    Text("Guardar reinicia el temporizador actual; el historial de estudio se conserva.", style = MaterialTheme.typography.bodySmall)
    FormSave(error, save = { val w = work.toIntOrNull(); val p = pause.toIntOrNull(); val l = long.toIntOrNull(); val c = cycles.toIntOrNull(); if (w !in 1..180 || p !in 1..60 || l !in 1..120 || c !in 1..12) error = "Revisa los rangos indicados." else { vm.change { it.copy(preferences = it.preferences.copy(workMinutes = w!!, breakMinutes = p!!, longBreakMinutes = l!!, cycles = c!!), focus = FocusState(remaining = w!! * 60000L, completed = it.focus.completed)) }; close() } }, close = close)
}
@Composable private fun SavingsForm(plan: Savings, vm: AppViewModel, close: () -> Unit) {
    var amount by remember { mutableStateOf(plan.amount.toString()) }; var frequency by remember { mutableStateOf(plan.frequency) }; var date by remember { mutableStateOf(plan.startMonth + "-01") }; var error by remember { mutableStateOf("") }
    Text("Plan de ahorro protegido", style = MaterialTheme.typography.headlineSmall); Field("Monto por aporte en pesos", amount, { amount = it.filter(Char::isDigit) }, true); Choice("Frecuencia", frequency, Finance.frequencies) { frequency = it }; DateField("Mes de inicio", date) { date = it }; Text("Se aparta solo el dinero que cabe en el saldo disponible. La proyección acumula los aportes.")
    FormSave(error, save = { val value = validAmount(amount, true); if (value == null) error = "Introduce un monto válido." else { vm.change { it.copy(savings = Savings(value, frequency, date.take(7))) }; close() } }, close = close)
}
@Composable private fun FinanceSettingsForm(prefs: FinanceSettings, vm: AppViewModel, close: () -> Unit) {
    var holidays by remember { mutableStateOf(prefs.holidays) }; var months by remember { mutableStateOf(prefs.emergencyMonths.toString()) }; var rate by remember { mutableStateOf(prefs.savingsRate.toString()) }; var days by remember { mutableStateOf(prefs.spendingDays) }; var start by remember { mutableStateOf(prefs.weekStart.toString()) }; var end by remember { mutableStateOf(prefs.weekEnd.toString()) }; var error by remember { mutableStateOf("") }
    val names = mapOf("1" to "Lunes", "2" to "Martes", "3" to "Miércoles", "4" to "Jueves", "5" to "Viernes", "6" to "Sábado", "7" to "Domingo")
    Text("Ajustes financieros", style = MaterialTheme.typography.headlineSmall); Toggle("Tener en cuenta festivos colombianos", holidays, { holidays = it }); Field("Colchón ideal (1–12 meses)", months, { months = it.filter(Char::isDigit) }, true); Field("Ahorro ideal (0–80 %)", rate, { rate = it.filter(Char::isDigit) }, true); Text("Días en los que usas tu presupuesto")
    names.forEach { (id, name) -> Toggle(name, id.toInt() in days, { checked -> days = if (checked) days + id.toInt() else days - id.toInt() }) }
    Choice("Tu semana empieza", start, names) { start = it }; Choice("Tu semana termina", end, names) { end = it }
    FormSave(error, save = { val m = months.toIntOrNull(); val r = rate.toIntOrNull(); if (m !in 1..12 || r !in 0..80 || days.isEmpty()) error = "Revisa los valores y elige al menos un día de uso." else { vm.change { it.copy(financeSettings = it.financeSettings.copy(holidays = holidays, emergencyMonths = m!!, savingsRate = r!!, spendingDays = days, weekStart = start.toInt(), weekEnd = end.toInt())) }; close() } }, close = close)
}
