@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package co.tuvida.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import java.time.*
import kotlin.math.*

@Composable fun FinanceScreen(data: AppData, vm: AppViewModel, edit: (Editor) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }; var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("all") }; var search by remember { mutableStateOf("") }; var projectionView by remember { mutableStateOf("total") }
    var extra by remember { mutableStateOf("50000") }; var strategy by remember { mutableStateOf("snowball") }; var selected by remember { mutableStateOf(LocalDate.now().toString()) }
    val summary = Finance.summary(data, month); val occurrences = Finance.occurrences(data, month)
    val tabs = listOf("Resumen", "Movimientos", "Diario", "Deudas", "Metas", "Proyección", "Calendario", "Categorías")
    Column {
        ScrollableTabRow(tab, edgePadding = 8.dp) { tabs.forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) } }
        LazyColumn(contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { MonthPicker(month) { month = it; selected = it.atDay(1).toString() } }
            if (data.movements.isEmpty()) item { Info("Tu dinero empieza contigo", "Añade tus ingresos, gastos y créditos con +. También puedes importar una copia JSON de Mi Plata Clara en Ajustes.", Icons.Outlined.AccountBalanceWallet) }
            when (tab) {
                0 -> {
                    item {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Dinero disponible del mes", style = MaterialTheme.typography.titleMedium)
                                Text(money(if (data.financeSettings.deductSavings) summary.free else summary.closing), style = MaterialTheme.typography.headlineLarge, color = if (summary.free < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Después de compromisos y gastos anotados${if (data.financeSettings.deductSavings) ", con el ahorro separado" else ""}.", style = MaterialTheme.typography.bodySmall)
                                AmountRow("Saldo que venía", summary.opening); AmountRow("Ingresos previstos", summary.income); AmountRow("Compromisos", summary.committed); AmountRow("Gastos del día a día", summary.spending); HorizontalDivider(); AmountRow("Saldo registrado", summary.cash)
                            }
                        }
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("Para hoy", money(summary.today), Modifier.weight(1f)); Metric("Por semana", money(summary.perWeek), Modifier.weight(1f)) }; Text("Referencia por día: ${money(summary.perDay)}. Se reparte entre tus días de uso restantes.", style = MaterialTheme.typography.bodySmall) }
                    item { Section("Tu ahorro", action = { TextButton({ edit(Editor("savings")) }) { Text("Configurar") } }); AmountRow("Quiero guardar este mes", Finance.plannedSavings(data.savings, month)); AmountRow("Ahorro que cabe en el saldo", summary.saving); Toggle("Descontar ahorro del disponible", data.financeSettings.deductSavings, { checked -> vm.change { it.copy(financeSettings = it.financeSettings.copy(deductSavings = checked)) } }) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ edit(Editor("spending")) }) { Text("Anotar gasto") }; OutlinedButton({ edit(Editor("financeSettings")) }) { Text("Presupuesto") } } }
                    item { Section("Comparado con el mes anterior"); val previous = Finance.summary(data, month.minusMonths(1)); AmountRow("Cambio en ingresos", summary.income - previous.income); AmountRow("Cambio en gastos", summary.committed + summary.spending - previous.committed - previous.spending); AmountRow("Cambio en dinero libre", summary.free - previous.free) }
                    item { Section("Salud financiera"); Text("${Finance.healthScore(data, month)} de 100", style = MaterialTheme.typography.headlineSmall); LinearProgressIndicator(progress = { Finance.healthScore(data, month) / 100f }, modifier = Modifier.fillMaxWidth()); Text("Ahorro, carga de cuotas, colchón de emergencia y margen disponible. Un ingreso previsto no equivale a dinero recibido.", style = MaterialTheme.typography.bodySmall) }
                    items(Finance.alerts(data, month)) { advice -> Info(advice.title, advice.body) }
                }
                1 -> {
                    item { Field("Buscar movimientos", search, { search = it }); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { mapOf("all" to "Todos", "income" to "Ingresos", "expense" to "Gastos", "credit" to "Créditos", "pending" to "Pendientes").forEach { (key, label) -> FilterChip(filter == key, { filter = key }, label = { Text(label) }) } } }
                    val entries = occurrences.filter { it.movement.name.contains(search, true) && when (filter) { "income", "expense" -> it.movement.type == filter; "credit" -> it.movement.kind == "credit"; "pending" -> !it.paid; else -> true } }
                    if (entries.isEmpty()) item { Info("Sin movimientos para este mes", "Cambia el filtro, revisa la fecha de inicio o crea un movimiento.") }
                    items(entries, key = { "${it.movement.id}:${it.paymentKey}" }) { occurrence ->
                        val m = occurrence.movement
                        ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("${occurrence.date} · ${Finance.frequencies[m.frequency]}${if (m.kind == "credit") " · cuota ${occurrence.installment}/${m.installments}" else ""}\n${money(m.amount)} · ${Finance.categories[m.category] ?: m.category}") }, leadingContent = { Checkbox(occurrence.paid, { vm.payment(occurrence) }) }, trailingContent = { IconButton({ edit(Editor("movement", m.id)) }) { Icon(Icons.Outlined.Edit, "Editar movimiento") } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ vm.change { it.copy(movements = it.movements + m.copy(id = newId(), name = m.name + " (copia)", payments = emptySet())) } }) { Text("Duplicar") }; TextButton({ vm.undoable { it.copy(movements = it.movements.filter { item -> item.id != m.id }) } }) { Text("Eliminar") } }; HorizontalDivider()
                    }
                    val hidden = data.movements.filter { m -> occurrences.none { it.movement.id == m.id } }
                    if (hidden.isNotEmpty()) item { Section("Fuera de este mes", "Edita también movimientos que aún no empiezan o que ya terminaron.") }
                    items(hidden, key = { "hidden-${it.id}" }) { m -> ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("${money(m.amount)} · desde ${m.startDate}") }, trailingContent = { IconButton({ edit(Editor("movement", m.id)) }) { Icon(Icons.Outlined.Edit, "Editar movimiento fuera del mes") } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
                }
                2 -> {
                    item { Section("Gastos del día a día", action = { TextButton({ edit(Editor("spending")) }) { Text("Anotar") } }); AmountRow("Anotado en el mes", summary.spending) }
                    val spending = data.spending.filter { it.date.startsWith(month.toString()) }.sortedByDescending { it.date }
                    if (spending.isEmpty()) item { Info("Todavía sin gastos anotados", "Registra los gastos pequeños para que tu presupuesto sea más preciso.") }
                    items(spending, key = { it.id }) { spending -> ListItem(headlineContent = { Text(money(spending.amount)) }, supportingContent = { Text("${spending.date} · ${Finance.categories[spending.category]}\n${spending.note}") }, trailingContent = { Row { IconButton({ edit(Editor("spending", spending.id)) }) { Icon(Icons.Outlined.Edit, "Editar gasto") }; IconButton({ vm.undoable { it.copy(spending = it.spending.filter { item -> item.id != spending.id }) } }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar gasto") } } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)); HorizontalDivider() }
                }
                3 -> {
                    val debts = Finance.debts(data, month)
                    item { Section("Tu camino para salir de deudas"); AmountRow("Falta por pagar", debts.sumOf { it.remaining }); AmountRow("Cuotas previstas este mes", occurrences.filter { it.movement.kind == "credit" }.sumOf { it.movement.amount }); Text("Última cuota del plan: ${debts.maxOfOrNull { it.end }?.let(::monthLabel) ?: "Sin deudas activas"}") }
                    items(debts, key = { it.movement.id }) { debt ->
                        val m = debt.movement
                        Section(m.name, "${debt.paidCount} de ${m.installments} cuotas registradas o anteriores a la cuota inicial", action = { IconButton({ edit(Editor("movement", m.id)) }) { Icon(Icons.Outlined.Edit, "Editar crédito") } }); LinearProgressIndicator(progress = { debt.paidCount.toFloat() / m.installments }, modifier = Modifier.fillMaxWidth()); AmountRow("Saldo sin intereses", debt.remaining); AmountRow("Cuota", m.amount); Text("Fin previsto: ${monthLabel(debt.end)}", style = MaterialTheme.typography.bodySmall); HorizontalDivider()
                    }
                    if (debts.isNotEmpty()) {
                        item { Section("Simular abonos extra"); Field("Abono adicional al mes", extra, { extra = it.filter(Char::isDigit) }, true); Choice("Estrategia", strategy, mapOf("snowball" to "Deuda más pequeña", "largest" to "Cuota más alta")) { strategy = it } }
                        item { val payoff = Finance.simulate(data, month, extra.toLongOrNull()?.coerceIn(0, 1_000_000_000_000) ?: 0, strategy); Info("${payoff.extraMonths} meses con este plan", "Sin abonos: aproximadamente ${payoff.normalMonths} meses. Reutiliza cuotas liberadas. No calcula intereses ni sustituye las condiciones de tu crédito.") }
                    }
                }
                4 -> {
                    item { Section("Tus metas", action = { TextButton({ edit(Editor("goal")) }) { Text("Crear meta") } }) }
                    if (data.goals.isEmpty()) item { Info("Dale un propósito a tu ahorro", "Crea una meta y registra tus abonos.", Icons.Outlined.Flag) }
                    items(data.goals, key = { it.id }) { goal ->
                        Section(goal.name, action = { IconButton({ edit(Editor("goal", goal.id)) }) { Icon(Icons.Outlined.Edit, "Editar meta") } }); LinearProgressIndicator(progress = { (goal.saved.toFloat() / goal.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); AmountRow("Ahorrado", goal.saved); AmountRow("Objetivo", goal.target); AmountRow("Aporte mensual", goal.monthly)
                        val remaining = max(0, goal.target - goal.saved); val months = if (goal.monthly > 0) ceil(remaining.toDouble() / goal.monthly).toLong() else 0
                        Text(if (remaining == 0L) "Meta cumplida" else if (goal.monthly == 0L) "Define un aporte para estimar una fecha" else "Llegarías en ${monthLabel(month.plusMonths(months))}", color = MaterialTheme.colorScheme.primary)
                        if (goal.deadline.isNotEmpty()) Text("Fecha objetivo: ${goal.deadline}", style = MaterialTheme.typography.bodySmall)
                        Row { TextButton({ edit(Editor("contribute", goal.id)) }) { Text("Abonar") }; TextButton({ vm.undoable { it.copy(goals = it.goals.filter { g -> g.id != goal.id }) } }) { Text("Eliminar") } }; HorizontalDivider()
                    }
                    if (data.goals.isNotEmpty()) item { OutlinedButton({ vm.change { it.copy(savings = Savings(it.goals.sumOf { g -> g.monthly }, "monthly", month.toString())) } }) { Text("Usar aportes como ahorro mensual") } }
                }
                5 -> {
                    val projection = Finance.projection(data, month)
                    item { Section("Los próximos 12 meses", "El saldo acumula ingresos y egresos previstos; no confirma pagos reales."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(projectionView == "total", { projectionView = "total" }, label = { Text("Saldo total") }); FilterChip(projectionView == "free", { projectionView = "free" }, label = { Text("Solo lo libre") }, enabled = data.savings.amount > 0) }; ProjectionChart(projection, projectionView) }
                    items(projection) { p -> Section(monthLabel(p.month)); AmountRow(if (projectionView == "free") "Saldo libre" else "Saldo de cierre", if (projectionView == "free") p.free else p.closing); AmountRow("Ahorro acumulado protegido", p.savings); if (p.closing < 0) Text("Este mes cerraría en rojo", color = MaterialTheme.colorScheme.error); HorizontalDivider() }
                }
                6 -> {
                    item { Section("Calendario del dinero", "Elige un día para ver pagos, cobros, gastos y saldo proyectado."); FinancialCalendar(month, occurrences, data, selected) { selected = it } }
                    item { Section(selected); val day = runCatching { LocalDate.parse(selected) }.getOrDefault(month.atDay(1)); if (day in Finance.holidays(month.year)) Text("Festivo colombiano", color = MaterialTheme.colorScheme.secondary); val toDate = occurrences.filter { it.date <= day }; val expenses = data.spending.filter { it.date.startsWith(month.toString()) && it.date <= selected }.sumOf { it.amount }; AmountRow("Saldo previsto hasta este día", summary.opening + toDate.sumOf { if (it.movement.type == "income") it.movement.amount else -it.movement.amount } - expenses) }
                    items(occurrences.filter { it.date.toString() == selected }) { occurrence -> ListItem(headlineContent = { Text(occurrence.movement.name) }, supportingContent = { Text(money(occurrence.movement.amount) + if (occurrence.paid) " · registrado" else " · pendiente") }, trailingContent = { Checkbox(occurrence.paid, { vm.payment(occurrence) }) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
                    items(data.spending.filter { it.date == selected }) { s -> AmountRow(s.note.ifBlank { Finance.categories[s.category] ?: "Gasto diario" }, -s.amount) }
                    item { TextButton({ edit(Editor("spending", date = selected)) }) { Text("Anotar gasto para este día") } }
                }
                7 -> {
                    item { Section("En qué se mueve tu dinero", "Planeado incluye movimientos; registrado incluye pagos marcados y gastos diarios.") }
                    items(Finance.categoryTotals(data, month).entries.toList()) { (key, value) -> Section(Finance.categories[key] ?: key); AmountRow("Planeado", value.first); AmountRow("Registrado", value.second); HorizontalDivider() }
                }
            }
        }
    }
}
@Composable private fun FinancialCalendar(month: YearMonth, events: List<Occurrence>, data: AppData, selected: String, pick: (String) -> Unit) {
    // Reuse the accessible grid; records signal presence of a financial occurrence, not health.
    val offset = month.atDay(1).dayOfWeek.value - 1; val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column {
        Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        (0 until cells step 7).forEach { row -> Row { (0..6).forEach { col -> val day = row + col - offset + 1
            if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp)) else {
                val date = month.atDay(day); val key = date.toString(); val count = events.count { it.date == date } + data.spending.count { it.date == key }; val festive = date in Finance.holidays(month.year)
                TextButton({ pick(key) }, Modifier.weight(1f).heightIn(min = 56.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected == key) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(day.toString(), color = if (festive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }
            }
        } } }
    }
}
@Composable private fun ProjectionChart(values: List<Projection>, mode: String) {
    val primary = MaterialTheme.colorScheme.primary; val secondary = MaterialTheme.colorScheme.secondary; val error = MaterialTheme.colorScheme.error; val line = MaterialTheme.colorScheme.outline
    val maxValue = values.maxOfOrNull { max(abs(it.closing), abs(it.free)) }?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(vertical = 12.dp).semantics { contentDescription = "Gráfico de saldo previsto; los valores de cada mes están en la lista siguiente." }) {
        val baseline = size.height * .75f; val span = size.width / values.size
        drawLine(line, Offset(0f, baseline), Offset(size.width, baseline), 1f)
        values.forEachIndexed { i, p ->
            val value = if (mode == "free") p.free else p.closing; val height = abs(value).toFloat() / maxValue * size.height * .7f
            drawRect(if (value < 0) error else primary, Offset(span * i + span * .18f, if (value < 0) baseline else baseline - height), Size(span * .64f, if (value < 0) min(height, size.height - baseline) else height))
            if (mode == "total" && value > 0 && p.savings > 0) { val savedHeight = p.savings.toFloat() / maxValue * size.height * .7f; drawRect(secondary, Offset(span * i + span * .18f, baseline - savedHeight), Size(span * .64f, savedHeight)) }
        }
    }
    Text("Verde: saldo · dorado: ahorro · rojo: déficit. Meses en orden, de izquierda a derecha.", style = MaterialTheme.typography.bodySmall)
}
