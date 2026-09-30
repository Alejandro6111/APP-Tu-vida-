package co.tuvida.app.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import biweekly.Biweekly
import co.tuvida.app.data.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.Date
import java.util.TimeZone
import java.time.*

object Calendars {
    val teams = linkedMapOf("barcelona" to "FC Barcelona", "colombia" to "Colombia", "millonarios" to "Millonarios")
    val feeds = mapOf("barcelona" to "https://ics.fixtur.es/v2/fc-barcelona.ics", "colombia" to "https://ics.fixtur.es/v2/co.ics", "millonarios" to "https://ics.fixtur.es/v2/millonarios.ics")
    fun sources(context: Context): List<CalendarSource> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val columns = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        return context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns, "${CalendarContract.Calendars.VISIBLE}=1", null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(CalendarSource(cursor.getLong(0).toString(), cursor.getString(1) ?: "Calendario", cursor.getString(2) ?: "")) }
        } ?: emptyList()
    }
    fun deviceEvents(context: Context, selected: Set<String>, now: Long): List<AgendaEvent> {
        if (selected.isEmpty()) return emptyList()
        check(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) { "Autoriza el acceso a calendarios en Ajustes." }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now - 86400000); ContentUris.appendId(it, now + 60L * 86400000) }.build()
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.ALL_DAY)
        val ids = selected.filter { it.toLongOrNull() != null }
        if (ids.isEmpty()) return emptyList()
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString { "?" }})"
        return context.contentResolver.query(uri, columns, selection, ids.toTypedArray(), "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            buildList { while (c.moveToNext() && size < 2000) {
                val allDay = c.getInt(5) != 0
                fun local(ts: Long) = if (allDay) Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else ts
                val start = local(c.getLong(2)); val end = local(c.getLong(3))
                add(AgendaEvent("device:${c.getLong(0)}:$start", c.getString(1) ?: "Sin título", start, end, "device", allDay))
            } }
        } ?: emptyList()
    }
    fun validGoogleUrl(raw: String): Boolean = runCatching {
        val uri = java.net.URI(raw)
        raw.length <= 2048 && uri.scheme == "https" && uri.host == "calendar.google.com" && uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null && Regex("/calendar/ical/[^/]+/(private-[^/]+|public)/basic\\.ics").matches(uri.rawPath)
    }.getOrDefault(false)
    fun fetch(url: String): String {
        require(url in feeds.values || validGoogleUrl(url)) { "Usa una dirección iCal de Google Calendar." }
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 12000; connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == 200) { "El servidor del calendario no está disponible (HTTP ${connection.responseCode})." }
            return connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var length = stream.read(buffer)
                while (length != -1) { require(out.size() + length <= Backup.MAX_BYTES) { "El calendario supera 5 MB." }; out.write(buffer, 0, length); length = stream.read(buffer) }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
    fun parse(text: String, source: String, now: Long): List<AgendaEvent> {
        require(text.length <= Backup.MAX_BYTES && text.contains("BEGIN:VCALENDAR")) { "El servidor no devolvió un calendario iCal." }
        val calendar = Biweekly.parse(text).first() ?: error("Calendario iCal inválido.")
        val zone = TimeZone.getDefault()
        val overrides = calendar.events.filter { it.recurrenceId != null }.groupBy { it.uid?.value ?: "" }
        val result = mutableListOf<AgendaEvent>()
        calendar.events.forEach { event ->
            if (event.status?.value == "CANCELLED") return@forEach
            val startProperty = event.dateStart ?: return@forEach
            val start = startProperty.value.time
            val allDay = !startProperty.value.hasTime()
            val duration = (event.dateEnd?.value?.time?.minus(start) ?: if (allDay) 86400000L else 3600000L).coerceAtLeast(1)
            val uid = event.uid?.value ?: "$source:$start:${event.summary?.value}"
            val exceptions = overrides[uid]?.mapNotNull { it.recurrenceId?.value?.time }?.toSet() ?: emptySet()
            val tz = calendar.timezoneInfo.getTimezone(startProperty)?.timeZone ?: zone
            val iterator = event.getDateIterator(tz)
            iterator.advanceTo(Date(now - 86400000))
            var count = 0
            while (iterator.hasNext() && count++ < 2000 && result.size < 4000) {
                val date = iterator.next().time
                if (date > now + 60L * 86400000) break
                if (event.recurrenceId == null && date in exceptions) continue
                if (date + duration > now - 86400000) result += AgendaEvent("$source:$uid:$date", event.summary?.value?.take(500) ?: "Sin título", date, date + duration, source, allDay)
            }
        }
        return result.distinctBy { it.id }.sortedBy { it.start }
    }
}
