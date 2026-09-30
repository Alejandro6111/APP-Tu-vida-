package co.tuvida.app

import co.tuvida.app.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesData() { val data = AppData(health = listOf(HealthDay(exercise = true, minutes = 45)), tasks = listOf(Task(title = "Leer"))); val copy = Backup.decode(Backup.encode(data)); assertEquals(data.health, copy.health); assertEquals(data.tasks, copy.tasks) }
    @Test fun restoreDoesNotResumeAnOldTimer() { val copy = Backup.decode(Backup.encode(AppData(focus = FocusState(running = true, deadline = 10000)))); assertFalse(copy.focus.running) }
    @Test fun restoreDropsCalendarIdsAndCache() { val data = AppData(events = listOf(AgendaEvent("1", "Privado", 10, 20, "device")), preferences = Preferences(calendarIds = setOf("45"))); val copy = Backup.decode(Backup.encode(data)); assertTrue(copy.events.isEmpty()); assertTrue(copy.preferences.calendarIds.isEmpty()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownSchema() { Backup.decode("{\"version\":99,\"movements\":[],\"tasks\":[],\"health\":[]}") }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeAmount() { Backup.validate(AppData(movements = listOf(Movement(name = "Inválido", amount = -1)))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnboundedPreferences() { Backup.validate(AppData(preferences = Preferences(workMinutes = 0))) }
    @Test(expected = java.time.DateTimeException::class) fun rejectsInvalidDate() { Backup.validate(AppData(health = listOf(HealthDay(date = "2026-02-31")))) }
    @Test fun importsLegacyFinanceCopy() { val text = """{"dataVersion":8,"items":[{"id":"a","name":"Ingreso","amount":1000000,"type":"income","kind":"income","frequency":"monthly","startDate":"2026-08-01","day":1,"payments":{"2026-08":true}}],"spending":[],"goals":[],"savings":{"amount":100000,"frequency":"monthly","startMonth":"2026-08"}}"""; val data = Backup.decode(text); assertEquals(1000000, data.movements.single().amount); assertEquals(setOf("2026-08"), data.movements.single().payments); assertEquals(100000, data.savings.amount) }
    @Test fun importsOriginalIdDatePaymentKeys() { val text = """{"dataVersion":8,"items":[{"id":"credit-a","name":"Crédito","amount":100000,"kind":"credit","frequency":"monthly","startDate":"2026-08-01","day":2,"payments":{"credit-a:2026-08-02":true}}]}"""; val data = Backup.decode(text); assertEquals(setOf("2026-08"), data.movements.single().payments); assertTrue(co.tuvida.app.domain.Finance.occurrences(data, java.time.YearMonth.of(2026, 8)).single().paid) }
}
