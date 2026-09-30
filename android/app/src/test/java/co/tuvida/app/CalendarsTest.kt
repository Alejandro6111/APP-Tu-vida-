package co.tuvida.app

import co.tuvida.app.platform.Calendars
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CalendarsTest {
    private val now = Instant.parse("2026-09-29T00:00:00Z").toEpochMilli()
    private fun ics(events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//TuVida//Tests//ES\r\n$events\r\nEND:VCALENDAR"
    @Test fun onlyAllowsGoogleCalendarHttpsUrl() { assertTrue(Calendars.validGoogleUrl("https://calendar.google.com/calendar/ical/test%40gmail.com/private-abc/basic.ics")); assertFalse(Calendars.validGoogleUrl("https://evil.test/calendar/ical/test/private-abc/basic.ics")); assertFalse(Calendars.validGoogleUrl("http://calendar.google.com/calendar/ical/test/public/basic.ics")); assertFalse(Calendars.validGoogleUrl("https://calendar.google.com/calendar/ical/test/public/basic.ics?token=x")) }
    @Test fun parsesUtcFootballEvent() { val text = ics("BEGIN:VEVENT\r\nUID:match-1\r\nSUMMARY:Barcelona - Rival\r\nDTSTART:20260930T190000Z\r\nDTEND:20260930T210000Z\r\nEND:VEVENT"); val e = Calendars.parse(text, "barcelona", now).single(); assertEquals(Instant.parse("2026-09-30T19:00:00Z").toEpochMilli(), e.start); assertEquals("Barcelona - Rival", e.title) }
    @Test fun expandsRecurrenceAndExclusions() { val text = ics("BEGIN:VEVENT\r\nUID:class\r\nSUMMARY:Clase\r\nDTSTART:20260929T120000Z\r\nDTEND:20260929T130000Z\r\nRRULE:FREQ=DAILY;COUNT=4\r\nEXDATE:20260930T120000Z\r\nEND:VEVENT"); val events = Calendars.parse(text, "google", now); assertEquals(3, events.size); assertFalse(events.any { it.start == Instant.parse("2026-09-30T12:00:00Z").toEpochMilli() }) }
    @Test fun cancelledOverrideRemovesOriginalInstance() { val text = ics("BEGIN:VEVENT\r\nUID:class\r\nSUMMARY:Clase\r\nDTSTART:20260929T120000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:class\r\nRECURRENCE-ID:20260930T120000Z\r\nDTSTART:20260930T120000Z\r\nSTATUS:CANCELLED\r\nEND:VEVENT"); val events = Calendars.parse(text, "google", now); assertEquals(2, events.size) }
    @Test fun movedOverrideDoesNotDuplicateOriginal() { val text = ics("BEGIN:VEVENT\r\nUID:class\r\nSUMMARY:Clase\r\nDTSTART:20260929T120000Z\r\nRRULE:FREQ=DAILY;COUNT=2\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:class\r\nSUMMARY:Clase movida\r\nRECURRENCE-ID:20260930T120000Z\r\nDTSTART:20260930T150000Z\r\nEND:VEVENT"); val events = Calendars.parse(text, "google", now); assertEquals(2, events.size); assertTrue(events.any { it.title == "Clase movida" }) }
    @Test(expected = IllegalArgumentException::class) fun rejectsHtmlResponse() { Calendars.parse("<html>bad</html>", "google", now) }
}
