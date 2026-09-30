package co.tuvida.app.data

import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
data class Movement(
    val id: String = newId(), val name: String = "", val amount: Long = 0,
    val type: String = "expense", val kind: String = "expense", val frequency: String = "monthly",
    val startDate: String = LocalDate.now().toString(), val endDate: String = "", val day: Int = 1,
    val schedule: String = "day", val installments: Int = 12, val currentInstallment: Int = 1,
    val category: String = "otros", val payments: Set<String> = emptySet()
)
data class Spending(val id: String = newId(), val date: String = LocalDate.now().toString(), val amount: Long = 0, val category: String = "otros", val note: String = "")
data class Goal(val id: String = newId(), val name: String = "", val target: Long = 0, val saved: Long = 0, val monthly: Long = 0, val deadline: String = "")
data class Savings(val amount: Long = 0, val frequency: String = "monthly", val startMonth: String = LocalDate.now().toString().take(7))
data class FinanceSettings(val holidays: Boolean = true, val emergencyMonths: Int = 3, val savingsRate: Int = 20, val deductSavings: Boolean = true, val spendingDays: Set<Int> = (1..7).toSet(), val weekStart: Int = 1, val weekEnd: Int = 7)
data class Task(val id: String = newId(), val title: String = "", val due: Long = System.currentTimeMillis(), val kind: String = "task", val durationMinutes: Int = 25, val repeat: String = "none", val done: Boolean = false)
data class HealthDay(val date: String = LocalDate.now().toString(), val breakfast: String = "", val lunch: String = "", val dinner: String = "", val snacks: String = "", val exercise: Boolean = false, val minutes: Int = 0, val activity: String = "", val notes: String = "")
data class CalendarSource(val id: String, val name: String, val account: String)
data class AgendaEvent(val id: String, val title: String, val start: Long, val end: Long, val source: String, val allDay: Boolean = false)
data class SyncStatus(val source: String, val updated: Long = 0, val error: String = "")
data class Preferences(
    val theme: String = "system", val notifications: Boolean = true, val intensity: String = "intense",
    val leadMinutes: Int = 30, val quietStart: Int = 22, val quietEnd: Int = 7,
    val quietEnabled: Boolean = true, val exerciseReminder: Boolean = true, val exerciseHour: Int = 18,
    val studyReminders: Boolean = true, val studyKeywords: String = "estudio,clase,curso,study",
    val studyLeadMinutes: Int = 15, val studyDuration: Int = 25,
    val calendarIds: Set<String> = emptySet(), val football: Set<String> = setOf("barcelona", "colombia", "millonarios"),
    val workMinutes: Int = 25, val breakMinutes: Int = 5, val longBreakMinutes: Int = 15, val cycles: Int = 4
)
data class FocusState(val mode: String = "pomodoro", val phase: String = "focus", val running: Boolean = false, val started: Long = 0, val deadline: Long = 0, val elapsed: Long = 0, val remaining: Long = 25 * 60_000L, val completed: Int = 0, val laps: List<Long> = emptyList())
data class StudyLog(val id: String = newId(), val timestamp: Long = System.currentTimeMillis(), val minutes: Int = 25)
data class SnoozedReminder(val id: String, val at: Long, val title: String, val body: String, val channel: String, val taskId: String = "", val route: String = "agenda")
data class AppData(
    val version: Int = 1, val createdDate: String = LocalDate.now().toString(),
    val movements: List<Movement> = emptyList(), val spending: List<Spending> = emptyList(), val goals: List<Goal> = emptyList(),
    val savings: Savings = Savings(), val financeSettings: FinanceSettings = FinanceSettings(),
    val tasks: List<Task> = emptyList(), val health: List<HealthDay> = emptyList(), val events: List<AgendaEvent> = emptyList(),
    val sync: List<SyncStatus> = emptyList(), val preferences: Preferences = Preferences(), val focus: FocusState = FocusState(),
    val studyLog: List<StudyLog> = emptyList(), val lastBackup: Long = 0, val snoozed: List<SnoozedReminder> = emptyList()
)
