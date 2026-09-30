package co.tuvida.app.domain

import co.tuvida.app.data.*
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class Occurrence(val movement: Movement, val date: LocalDate, val paymentKey: String, val paid: Boolean, val installment: Int = 0)
data class MonthSummary(val opening: Long, val income: Long, val committed: Long, val spending: Long, val closing: Long, val cash: Long, val saving: Long, val free: Long, val perDay: Long, val today: Long, val perWeek: Long)
data class Projection(val month: YearMonth, val closing: Long, val savings: Long, val free: Long)
data class Debt(val movement: Movement, val remaining: Long, val paidCount: Int, val end: YearMonth)
data class Payoff(val normalMonths: Int, val extraMonths: Int, val remaining: Long)
data class Advice(val title: String, val body: String)

object Finance {
    val frequencies = linkedMapOf("once" to "Una vez", "weekly" to "Semanal", "biweekly" to "Cada 15 días", "monthly" to "Mensual", "bimonthly" to "Cada 2 meses", "quarterly" to "Trimestral", "semiannual" to "Semestral", "yearly" to "Anual")
    val categories = linkedMapOf("vivienda" to "Vivienda", "mercado" to "Mercado", "transporte" to "Transporte", "servicios" to "Servicios", "salud" to "Salud", "educacion" to "Educación", "ocio" to "Ocio", "suscripciones" to "Suscripciones", "deudas" to "Créditos", "familia" to "Familia", "personal" to "Personal", "ahorro" to "Ahorro", "otros" to "Otros gastos", "salario" to "Salario", "extra" to "Ingreso extra", "ventas" to "Ventas", "otros-ingreso" to "Otros ingresos")
    private val intervals = mapOf("monthly" to 1, "bimonthly" to 2, "quarterly" to 3, "semiannual" to 6, "yearly" to 12)

    fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        return LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
    }
    fun holidays(year: Int): Set<LocalDate> {
        fun monday(date: LocalDate) = date.plusDays(((8 - date.dayOfWeek.value) % 7).toLong())
        val fixed = listOf(1 to 1, 5 to 1, 7 to 20, 8 to 7, 12 to 8, 12 to 25).map { LocalDate.of(year, it.first, it.second) }
        val shifted = listOf(1 to 6, 3 to 19, 6 to 29, 8 to 15, 10 to 12, 11 to 1, 11 to 11).map { monday(LocalDate.of(year, it.first, it.second)) }
        val easter = easter(year)
        return (fixed + shifted + listOf(easter.minusDays(3), easter.minusDays(2), monday(easter.plusDays(39)), monday(easter.plusDays(60)), monday(easter.plusDays(68)))).toSet()
    }
    fun lastBusinessDay(month: YearMonth, skipHolidays: Boolean): LocalDate {
        var date = month.atEndOfMonth(); val festive = if (skipHolidays) holidays(month.year) else emptySet()
        while (date.dayOfWeek.value >= 6 || date in festive) date = date.minusDays(1)
        return date
    }
    fun dates(movement: Movement, month: YearMonth, skipHolidays: Boolean): List<LocalDate> {
        val start = LocalDate.parse(movement.startDate)
        val end = movement.endDate.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        if (month < YearMonth.from(start) || (end != null && month > YearMonth.from(end))) return emptyList()
        val offset = ChronoUnit.MONTHS.between(YearMonth.from(start), month).toInt()
        if (movement.kind == "credit" && offset + movement.currentInstallment > movement.installments) return emptyList()
        val values = when (movement.frequency) {
            "once" -> if (YearMonth.from(start) == month) listOf(start) else emptyList()
            "weekly", "biweekly" -> {
                val days = if (movement.frequency == "weekly") 7L else 15L
                val gap = max(0L, ChronoUnit.DAYS.between(start, month.atDay(1)))
                var date = start.plusDays(((gap + days - 1) / days) * days)
                buildList { while (date <= month.atEndOfMonth()) { add(date); date = date.plusDays(days) } }
            }
            else -> if (offset % (intervals[movement.frequency] ?: 1) == 0) listOf(if (movement.schedule == "last-business-day") lastBusinessDay(month, skipHolidays) else month.atDay(movement.day.coerceIn(1, month.lengthOfMonth()))) else emptyList()
        }
        return values.filter { it >= start && (end == null || it <= end) }
    }
    fun occurrences(data: AppData, month: YearMonth): List<Occurrence> = data.movements.flatMap { m ->
        dates(m, month, data.financeSettings.holidays).map { date ->
            val key = if (m.frequency in listOf("weekly", "biweekly", "once")) date.toString() else month.toString()
            val installment = if (m.kind == "credit") m.currentInstallment + ChronoUnit.MONTHS.between(YearMonth.from(LocalDate.parse(m.startDate)), month).toInt() else 0
            Occurrence(m, date, key, key in m.payments, installment)
        }
    }.sortedBy { it.date }
    private fun net(data: AppData, month: YearMonth): Long = occurrences(data, month).sumOf { if (it.movement.type == "income") it.movement.amount else -it.movement.amount } - data.spending.filter { it.date.startsWith(month.toString()) }.sumOf { it.amount }
    fun opening(data: AppData, month: YearMonth): Long {
        val starts = data.movements.map { YearMonth.from(LocalDate.parse(it.startDate)) } + data.spending.map { YearMonth.from(LocalDate.parse(it.date)) }
        var cursor = starts.minOrNull() ?: month; var total = 0L; var steps = 0
        while (cursor < month && steps++ < 1200) { total += net(data, cursor); cursor = cursor.plusMonths(1) }
        return total
    }
    fun plannedSavings(plan: Savings, month: YearMonth): Long {
        if (plan.amount <= 0 || month < YearMonth.parse(plan.startMonth)) return 0
        val movement = Movement(amount = plan.amount, frequency = plan.frequency, startDate = plan.startMonth + "-01", day = 1)
        return dates(movement, month, false).size * plan.amount
    }
    fun summary(data: AppData, month: YearMonth, today: LocalDate = LocalDate.now()): MonthSummary {
        val entries = occurrences(data, month); val opening = opening(data, month)
        val income = entries.filter { it.movement.type == "income" }.sumOf { it.movement.amount }
        val committed = entries.filter { it.movement.type == "expense" }.sumOf { it.movement.amount }
        val spending = data.spending.filter { it.date.startsWith(month.toString()) }.sumOf { it.amount }
        val closing = opening + income - committed - spending
        val cash = opening + entries.filter { it.paid }.sumOf { if (it.movement.type == "income") it.movement.amount else -it.movement.amount } - spending
        val saved = min(max(0, closing), plannedSavings(data.savings, month))
        val free = closing - saved
        val from = if (YearMonth.from(today) == month) today else month.atDay(1)
        val spendingDays = data.financeSettings.spendingDays
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= month.atEndOfMonth() }.count { it.dayOfWeek.value in spendingDays }
        val perDay = max(0, free) / max(1, days)
        val spentToday = data.spending.filter { it.date == today.toString() }.sumOf { it.amount }
        val beforeTodaySpent = free + if (YearMonth.from(today) == month) spentToday else 0
        val todayBudget = if (today.dayOfWeek.value !in spendingDays || YearMonth.from(today) != month) 0 else max(0, beforeTodaySpent) / max(1, days) - spentToday
        val start = data.financeSettings.weekStart; val end = data.financeSettings.weekEnd
        val weekDays = (0..6).map { (start - 1 + it) % 7 + 1 }.take((end - start + 7) % 7 + 1).count { it in spendingDays }
        return MonthSummary(opening, income, committed, spending, closing, cash, saved, free, perDay, max(0, todayBudget), perDay * weekDays)
    }
    fun projection(data: AppData, from: YearMonth): List<Projection> {
        var balance = opening(data, from); var savings = 0L
        return (0L..11L).map { step ->
            val month = from.plusMonths(step); balance += net(data, month)
            savings = min(max(0, balance), savings + plannedSavings(data.savings, month))
            Projection(month, balance, savings, balance - savings)
        }
    }
    fun debts(data: AppData, month: YearMonth): List<Debt> = data.movements.filter { it.kind == "credit" }.map { m ->
        val start = YearMonth.from(LocalDate.parse(m.startDate))
        val assumedPaid = m.currentInstallment - 1
        val paid = m.payments.filter { runCatching { YearMonth.parse(it.take(7)) >= start }.getOrDefault(false) }.size
        val count = (assumedPaid + paid).coerceAtMost(m.installments)
        Debt(m, (m.installments - count) * m.amount, count, start.plusMonths((m.installments - m.currentInstallment).toLong()))
    }.filter { it.remaining > 0 }
    fun simulate(data: AppData, month: YearMonth, extra: Long, strategy: String): Payoff {
        val debts = debts(data, month); val amounts = debts.associate { it.movement.id to it.remaining }.toMutableMap()
        val normal = debts.maxOfOrNull { ceil(it.remaining.toDouble() / max(1, it.movement.amount)).toInt() } ?: 0
        var months = 0
        val initialMonthly = debts.sumOf { it.movement.amount }
        while (amounts.values.any { it > 0 } && months < 1200) {
            var budget = initialMonthly + extra
            debts.forEach { val amount = min(amounts[it.movement.id] ?: 0, it.movement.amount); amounts[it.movement.id] = (amounts[it.movement.id] ?: 0) - amount; budget -= amount }
            val ordered = if (strategy == "largest") debts.sortedByDescending { it.movement.amount } else debts.sortedBy { amounts[it.movement.id] }
            ordered.forEach { val payment = min(max(0, budget), amounts[it.movement.id] ?: 0); amounts[it.movement.id] = (amounts[it.movement.id] ?: 0) - payment; budget -= payment }
            months++
        }
        return Payoff(normal, months, debts.sumOf { it.remaining })
    }
    fun healthScore(data: AppData, month: YearMonth): Int {
        val s = summary(data, month); if (s.income <= 0) return 0
        val saveScore = (s.saving.toDouble() / s.income / max(.01, data.financeSettings.savingsRate / 100.0) * 30).coerceIn(0.0, 30.0)
        val debtRatio = occurrences(data, month).filter { it.movement.kind == "credit" }.sumOf { it.movement.amount }.toDouble() / s.income
        val debtScore = (25 * (1 - debtRatio / .5)).coerceIn(0.0, 25.0)
        val cushion = ((max(0, s.opening) + data.goals.sumOf { it.saved }).toDouble() / max(1, s.committed + s.spending) / data.financeSettings.emergencyMonths * 25).coerceIn(0.0, 25.0)
        val margin = (s.free.toDouble() / s.income / .3 * 20).coerceIn(0.0, 20.0)
        return (saveScore + debtScore + cushion + margin).roundToInt()
    }
    fun alerts(data: AppData, month: YearMonth, today: LocalDate = LocalDate.now()): List<Advice> = buildList {
        val entries = occurrences(data, month).filter { it.movement.type == "expense" && !it.paid }
        entries.filter { it.date < today }.forEach { add(Advice("Pago pendiente: ${it.movement.name}", "Venció el ${it.date}. Márcalo cuando lo pagues.")) }
        entries.filter { it.date >= today && it.date <= today.plusDays(7) }.forEach { add(Advice("Pago esta semana", "${it.movement.name} · ${it.date}")) }
        if (summary(data, month).closing < 0) add(Advice("El mes cierra en rojo", "Revisa compromisos e ingresos antes de gastar más."))
        if (projection(data, month).any { it.closing < 0 }) add(Advice("Hay meses con déficit", "Consulta la proyección de 12 meses."))
        if (data.lastBackup == 0L || System.currentTimeMillis() - data.lastBackup > 30L * 86400000) add(Advice("Protege tus datos", "Exporta una copia desde Ajustes."))
        data.goals.filter { it.deadline.isNotEmpty() }.forEach { goal ->
            val months = max(1, ChronoUnit.MONTHS.between(month.atDay(1), LocalDate.parse(goal.deadline)) + 1)
            if (goal.saved + goal.monthly * months < goal.target) add(Advice("Ajusta la meta ${goal.name}", "El aporte mensual no alcanza para la fecha objetivo."))
        }
        debts(data, month).filter { it.end == month }.forEach { add(Advice("Última cuota", "${it.movement.name} termina este mes según el plan.")) }
    }
    fun categoryTotals(data: AppData, month: YearMonth): Map<String, Pair<Long, Long>> = categories.keys.associateWith { category ->
        val planned = occurrences(data, month).filter { it.movement.category == category }.sumOf { it.movement.amount }
        val actual = occurrences(data, month).filter { it.paid && it.movement.category == category }.sumOf { it.movement.amount } + data.spending.filter { it.date.startsWith(month.toString()) && it.category == category }.sumOf { it.amount }
        planned to actual
    }.filterValues { it.first > 0 || it.second > 0 }
}
