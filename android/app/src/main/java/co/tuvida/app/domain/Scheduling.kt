package co.tuvida.app.domain

import co.tuvida.app.data.*
import java.time.*

data class Reminder(val id: String, val at: Long, val title: String, val body: String, val channel: String, val taskId: String = "", val route: String = "agenda")
object Scheduling {
    fun quiet(at: Long, prefs: Preferences, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!prefs.quietEnabled || prefs.quietStart == prefs.quietEnd) return false
        val hour = Instant.ofEpochMilli(at).atZone(zone).hour
        return if (prefs.quietStart < prefs.quietEnd) hour in prefs.quietStart until prefs.quietEnd else hour >= prefs.quietStart || hour < prefs.quietEnd
    }
    fun taskDates(task: Task, now: Long, days: Long = 30, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        if (task.done) return emptyList()
        if (task.repeat == "none") return listOf(task.due)
        var next = Instant.ofEpochMilli(task.due).atZone(zone)
        val step = if (task.repeat == "daily") 1L else 7L
        val current = Instant.ofEpochMilli(now).atZone(zone)
        if (next < current.minusDays(1)) {
            val gap = java.time.temporal.ChronoUnit.DAYS.between(next.toLocalDate(), current.toLocalDate())
            next = next.plusDays((gap / step) * step)
        }
        return buildList { while (next.toInstant().toEpochMilli() < now + days * 86400000) { add(next.toInstant().toEpochMilli()); next = next.plusDays(step) } }
    }
    fun plan(data: AppData, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Reminder> {
        val p = data.preferences; if (!p.notifications) return emptyList()
        val reminders = mutableListOf<Reminder>()
        data.snoozed.filter { it.at > now }.forEach { reminders += Reminder(it.id, it.at, it.title, it.body, it.channel, it.taskId, it.route) }
        val leads = if (p.intensity == "intense") listOf(p.leadMinutes, 10, 0).distinct() else listOf(p.leadMinutes)
        data.tasks.filter { !it.done }.forEach { t -> taskDates(t, now, zone = zone).forEach { date ->
            leads.forEach { lead -> reminders += Reminder("task:${t.id}:$date:$lead", date - lead * 60000L, t.title, if (lead == 0) "Es el momento. Puedes completar o posponer." else "Empieza en $lead minutos.", if (t.kind == "study") "study" else "tasks", t.id, if (t.kind == "study") "focus" else "agenda") }
            if (p.intensity == "intense") (1..3).forEach { repeat -> reminders += Reminder("repeat:${t.id}:$date:$repeat", date + repeat * 15 * 60000L, t.title, "La tarea sigue pendiente. Completa o pospón este aviso.", "tasks", t.id) }
        } }
        data.events.filter { it.source !in setOf("barcelona", "colombia", "millonarios") || it.source in p.football }.forEach { event ->
            val football = event.source in p.football
            val keywords = p.studyKeywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val study = p.studyReminders && !football && keywords.any { event.title.contains(it, true) }
            val eventLeads = if (study) (leads + p.studyLeadMinutes).distinct() else leads
            eventLeads.forEach { lead ->
                val start = if (event.allDay) Instant.ofEpochMilli(event.start).atZone(zone).toLocalDate().atTime(9, 0).atZone(zone).toInstant().toEpochMilli() else event.start
                reminders += Reminder("event:${event.id}:$lead", start - lead * 60000L, if (study) "Prepara tu sesión: ${event.title}" else event.title, if (event.allDay) "Evento de hoy · ${event.source}" else if (lead == 0) "Empieza ahora · ${event.source}" else "Empieza en $lead minutos · ${event.source}", if (football) "matches" else if (study) "study" else "tasks", route = if (study) "focus" else "agenda")
            }
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (p.exerciseReminder) (0L..29L).forEach { offset ->
            val day = today.plusDays(offset)
            if (data.health.none { it.date == day.toString() && it.exercise }) {
                val at = day.atTime(p.exerciseHour, 0).atZone(zone).toInstant().toEpochMilli()
                val repeats = if (p.intensity == "intense") listOf(0, 30, 60) else listOf(0)
                repeats.forEach { delay -> reminders += Reminder("health:$day:$delay", at + delay * 60000L, "Tu momento de moverte", "Haz ejercicio y registra tu actividad de hoy.", "health", route = "health") }
            }
        }
        (0L..1L).forEach { monthOffset ->
            Finance.occurrences(data, YearMonth.from(today).plusMonths(monthOffset)).filter { !it.paid && it.movement.type == "expense" }.forEach { occurrence ->
                val at = occurrence.date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                reminders += Reminder("finance:${occurrence.movement.id}:${occurrence.paymentKey}", at, "Pago: ${occurrence.movement.name}", "Revisa y marca el pago en Finanzas.", "finance", route = "finance")
            }
        }
        if (data.focus.running && data.focus.mode == "pomodoro") reminders += Reminder("focus", data.focus.deadline, "Sesión terminada", "Tu siguiente etapa está lista en Enfoque.", "study", route = "focus")
        // Focus endings are explicit user timers and remain audible during quiet hours.
        return reminders.filter { it.at > now && (it.id == "focus" || !quiet(it.at, p, zone)) }.distinctBy { it.id }.sortedBy { it.at }.take(350)
    }
    fun completeTask(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): Task {
        if (task.repeat == "none") return task.copy(done = true)
        var due = Instant.ofEpochMilli(task.due).atZone(zone)
        val step = if (task.repeat == "daily") 1L else 7L
        do { due = due.plusDays(step) } while (due.toInstant().toEpochMilli() <= now)
        return task.copy(due = due.toInstant().toEpochMilli())
    }
    fun finishFocus(data: AppData, now: Long): AppData {
        val focus = data.focus
        if (!focus.running || focus.mode != "pomodoro" || focus.deadline > now) return data
        val completed = focus.completed + if (focus.phase == "focus") 1 else 0
        val nextPhase = if (focus.phase != "focus") "focus" else if (completed % data.preferences.cycles == 0) "longBreak" else "break"
        val minutes = when (nextPhase) { "focus" -> data.preferences.workMinutes; "longBreak" -> data.preferences.longBreakMinutes; else -> data.preferences.breakMinutes }
        return data.copy(focus = focus.copy(running = false, phase = nextPhase, remaining = minutes * 60000L, deadline = 0, completed = completed), studyLog = if (focus.phase == "focus") data.studyLog + StudyLog(timestamp = focus.deadline, minutes = data.preferences.workMinutes) else data.studyLog)
    }
}
