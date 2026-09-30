package co.tuvida.app

import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SchedulingTest {
    private val zone = ZoneId.of("America/Bogota")
    private val now = LocalDate.of(2026, 9, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    @Test fun overnightQuietHours() { val p = Preferences(); assertTrue(Scheduling.quiet(now + 11 * 3600000, p, zone)); assertFalse(Scheduling.quiet(now, p, zone)) }
    @Test fun daytimeQuietHours() { assertTrue(Scheduling.quiet(now, Preferences(quietStart = 10, quietEnd = 14), zone)) }
    @Test fun notificationOptOutHasNoPlan() { assertTrue(Scheduling.plan(AppData(preferences = Preferences(notifications = false)), now, zone).isEmpty()) }
    @Test fun completedTasksHaveNoAlarms() { val t = Task(title = "Hecha", due = now + 3600000, done = true); assertTrue(Scheduling.plan(AppData(tasks = listOf(t)), now, zone).none { it.taskId == t.id }) }
    @Test fun intenseTasksHaveLeadAndFollowUps() { val t = Task(title = "Pendiente", due = now + 3600000); assertEquals(6, Scheduling.plan(AppData(tasks = listOf(t)), now, zone).count { it.taskId == t.id }) }
    @Test fun normalTaskHasOnlyOneReminder() { val t = Task(title = "Pendiente", due = now + 3600000); assertEquals(1, Scheduling.plan(AppData(tasks = listOf(t), preferences = Preferences(intensity = "normal")), now, zone).count { it.taskId == t.id }) }
    @Test fun exerciseLogStopsTodayReminders() { val data = AppData(health = listOf(HealthDay(date = "2026-09-29", exercise = true, minutes = 30))); assertFalse(Scheduling.plan(data, now, zone).any { it.id.startsWith("health:2026-09-29") }) }
    @Test fun recurringTasksKeepLocalHour() { val task = Task(title = "Rutina", due = now, repeat = "daily"); val dates = Scheduling.taskDates(task, now, zone = zone); assertTrue(dates.all { Instant.ofEpochMilli(it).atZone(zone).hour == 12 }) }
    @Test fun completingDailyAdvancesRatherThanDisables() { val task = Task(title = "Rutina", due = now, repeat = "daily"); val next = Scheduling.completeTask(task, now, zone); assertFalse(next.done); assertEquals(now + 86400000, next.due) }
    @Test fun finishingTimerLogsExactlyOneSession() { val data = AppData(focus = FocusState(running = true, deadline = now - 1)); val next = Scheduling.finishFocus(data, now); assertEquals(1, next.studyLog.size); assertFalse(next.focus.running); assertEquals("break", next.focus.phase); assertEquals(1, Scheduling.finishFocus(next, now).studyLog.size) }
    @Test fun longPauseAfterConfiguredCycles() { val data = AppData(focus = FocusState(running = true, deadline = now, completed = 3)); assertEquals("longBreak", Scheduling.finishFocus(data, now).focus.phase) }
    @Test fun returningFromPauseDoesNotLogFocus() { val data = AppData(focus = FocusState(running = true, phase = "break", deadline = now)); val next = Scheduling.finishFocus(data, now); assertEquals("focus", next.focus.phase); assertTrue(next.studyLog.isEmpty()) }
    @Test fun silentHoursStillAllowExplicitTimer() { val late = now + 11 * 3600000; val data = AppData(focus = FocusState(running = true, deadline = late)); assertTrue(Scheduling.plan(data, now, zone).any { it.id == "focus" }) }
    @Test fun calendarStudyGetsPreparationReminder() { val event = AgendaEvent("study", "Clase de inglés", now + 3600000, now + 7200000, "device"); assertTrue(Scheduling.plan(AppData(events = listOf(event)), now, zone).any { it.channel == "study" && it.title.contains("Prepara") }) }
    @Test fun disabledFootballSourceDoesNotGenerateReminders() { val event = AgendaEvent("match", "Partido", now + 3600000, now + 7200000, "barcelona"); assertFalse(Scheduling.plan(AppData(events = listOf(event), preferences = Preferences(football = emptySet())), now, zone).any { it.id.startsWith("event:") }) }
    @Test fun snoozedReminderSurvivesReplanning() { val snooze = SnoozedReminder("snooze:task", now + 600000, "Leer", "Recordatorio", "tasks"); assertTrue(Scheduling.plan(AppData(snoozed = listOf(snooze)), now, zone).any { it.id == snooze.id && it.at == snooze.at }) }
}
