package co.tuvida.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.YearMonth

object Backup {
    val gson = Gson()
    const val MAX_BYTES = 5_000_000
    fun encode(data: AppData): String = gson.toJson(data)
    fun decode(text: String): AppData {
        require(text.length <= MAX_BYTES) { "La copia supera 5 MB." }
        val root = JsonParser.parseString(text).asJsonObject
        if (root.has("items")) return legacy(text)
        require(root.get("version")?.asInt == 1 && root.has("movements") && root.has("tasks") && root.has("health")) { "No es una copia compatible de Tu Vida." }
        val data = gson.fromJson(root, AppData::class.java)
        validate(data)
        // Calendars are re-selected on the destination phone; secrets are never backed up.
        return data.copy(events = emptyList(), sync = emptyList(), snoozed = emptyList(), preferences = data.preferences.copy(calendarIds = emptySet()), focus = data.focus.copy(running = false, started = 0, deadline = 0))
    }
    fun validate(data: AppData) {
        require(data.version == 1 && data.movements.size <= 5000 && data.tasks.size <= 5000 && data.health.size <= 36500)
        LocalDate.parse(data.createdDate); YearMonth.parse(data.savings.startMonth)
        require(data.savings.amount in 0..1_000_000_000_000 && data.savings.frequency in co.tuvida.app.domain.Finance.frequencies)
        data.movements.forEach {
            require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 250 && it.amount in 1..1_000_000_000_000)
            require(it.type in listOf("income", "expense") && it.frequency in co.tuvida.app.domain.Finance.frequencies && it.day in 1..31)
            require(it.installments in 1..1200 && it.currentInstallment in 1..it.installments)
            LocalDate.parse(it.startDate)
            if (it.endDate.isNotEmpty()) require(LocalDate.parse(it.endDate) >= LocalDate.parse(it.startDate))
            it.payments.forEach { key -> if (key.length == 7) YearMonth.parse(key) else LocalDate.parse(key) }
        }
        require(data.movements.map { it.id }.distinct().size == data.movements.size)
        data.spending.forEach { require(it.amount in 1..1_000_000_000_000); LocalDate.parse(it.date) }
        data.goals.forEach { require(it.name.isNotBlank() && it.target > 0 && it.saved >= 0 && it.monthly >= 0); if (it.deadline.isNotBlank()) LocalDate.parse(it.deadline) }
        data.health.forEach { LocalDate.parse(it.date); require(it.minutes in 0..1440); require(!it.exercise || it.minutes > 0) }
        require(data.health.map { it.date }.distinct().size == data.health.size)
        data.tasks.forEach { require(it.title.isNotBlank() && it.title.length <= 250 && it.due > 0 && it.durationMinutes in 1..1440 && it.repeat in listOf("none", "daily", "weekly")) }
        val p = data.preferences
        require(p.workMinutes in 1..180 && p.breakMinutes in 1..60 && p.longBreakMinutes in 1..120 && p.cycles in 1..12)
        require(p.quietStart in 0..23 && p.quietEnd in 0..23 && p.exerciseHour in 0..23 && p.leadMinutes in 0..1440 && p.studyLeadMinutes in 0..1440 && p.studyDuration in 1..180)
        require(data.financeSettings.emergencyMonths in 1..12 && data.financeSettings.savingsRate in 0..80 && data.financeSettings.spendingDays.all { it in 1..7 } && data.financeSettings.spendingDays.isNotEmpty())
        require(data.financeSettings.weekStart in 1..7 && data.financeSettings.weekEnd in 1..7)
        require(data.focus.remaining >= 0 && data.focus.elapsed >= 0 && data.focus.completed >= 0)
    }
    private fun legacy(text: String): AppData {
        val root = JsonParser.parseString(text).asJsonObject
        fun com.google.gson.JsonObject.s(key: String, fallback: String = "") = get(key)?.takeIf { !it.isJsonNull }?.asString ?: fallback
        fun com.google.gson.JsonObject.n(key: String, fallback: Long = 0) = get(key)?.takeIf { !it.isJsonNull }?.asLong ?: fallback
        val items = root.getAsJsonArray("items").map { value ->
            val m = value.asJsonObject
            val frequency = m.s("frequency", "monthly")
            val payments = m.getAsJsonObject("payments")?.entrySet()?.filter { it.value.asBoolean }?.map { entry ->
                val date = entry.key.substringAfterLast(':')
                if (frequency in listOf("once", "weekly", "biweekly")) date else date.take(7)
            }?.toSet() ?: emptySet()
            Movement(id = m.s("id", newId()), name = m.s("name"), amount = m.n("amount"), type = m.s("type", "expense"), kind = m.s("kind", "expense"), frequency = m.s("frequency", "monthly"), startDate = m.s("startDate"), endDate = m.s("endDate"), day = m.n("day", 1).toInt(), schedule = m.s("schedule", "day"), installments = m.n("installments", 12).toInt(), currentInstallment = m.n("currentInstallment", 1).toInt(), category = m.s("category", "otros"), payments = payments)
        }
        val spending = root.getAsJsonArray("spending")?.map { value -> val v = value.asJsonObject; Spending(v.s("id", newId()), v.s("date"), v.n("amount"), v.s("category", "otros"), v.s("note")) } ?: emptyList()
        val goals = root.getAsJsonArray("goals")?.map { value -> val v = value.asJsonObject; Goal(v.s("id", newId()), v.s("name"), v.n("target"), v.n("saved"), v.n("monthly"), v.s("deadline")) } ?: emptyList()
        val savings = root.getAsJsonObject("savings")?.let { Savings(it.n("amount"), it.s("frequency", "monthly"), it.s("startMonth", LocalDate.now().toString().take(7))) } ?: Savings(root.n("savingsTarget"))
        val settings = root.getAsJsonObject("settings")
        val pacing = root.getAsJsonObject("pacing")
        val days = pacing?.getAsJsonArray("spendingDays")?.map { if (it.asInt == 0) 7 else it.asInt }?.toSet() ?: (1..7).toSet()
        val data = AppData(movements = items, spending = spending, goals = goals, savings = savings, financeSettings = FinanceSettings(holidays = settings?.get("skipHolidays")?.asBoolean ?: true, emergencyMonths = settings?.n("emergencyMonths", 3)?.toInt() ?: 3, savingsRate = settings?.n("savingsGoalRate", 20)?.toInt() ?: 20, deductSavings = root.get("deductSavingsFromAvailable")?.asBoolean ?: true, spendingDays = days, weekStart = pacing?.n("weekStart", 1)?.toInt()?.let { if (it == 0) 7 else it } ?: 1, weekEnd = pacing?.n("weekEnd", 0)?.toInt()?.let { if (it == 0) 7 else it } ?: 7))
        validate(data); return data
    }
}
