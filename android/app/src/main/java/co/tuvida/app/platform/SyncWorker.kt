package co.tuvida.app.platform

import android.content.Context
import androidx.work.*
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.data.*
import co.tuvida.app.domain.Scheduling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try { sync(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun install(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("calendar-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build())
        }
        fun request(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("manual-sync", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build()) }
        @Synchronized fun sync(context: Context) {
            val store = (context.applicationContext as TuVidaApplication).store
            if (store.recoveryError.isNotEmpty()) return
            val p = store.current.preferences; val now = System.currentTimeMillis()
            val active = p.football + "device" + if (runCatching { Secrets.read(context).isNotEmpty() }.getOrDefault(false)) setOf("google") else emptySet()
            store.update { it.copy(events = it.events.filter { e -> e.source in active }, sync = it.sync.filter { s -> s.source in active }) }
            active.forEach { source ->
                try {
                    val events = if (source == "device") Calendars.deviceEvents(context, p.calendarIds, now) else Calendars.parse(Calendars.fetch(if (source == "google") Secrets.read(context) else Calendars.feeds.getValue(source)), source, now)
                    store.update { data ->
                        val stillSelected = if (source in Calendars.teams) source in data.preferences.football else if (source == "device") data.preferences.calendarIds == p.calendarIds else runCatching { Secrets.read(context).isNotEmpty() }.getOrDefault(false)
                        if (!stillSelected) data else data.copy(events = data.events.filter { it.source != source } + events, sync = data.sync.filter { it.source != source } + SyncStatus(source, now))
                    }
                } catch (_: Exception) {
                    store.update { data -> data.copy(sync = data.sync.filter { it.source != source } + SyncStatus(source, data.sync.find { it.source == source }?.updated ?: 0, "No se pudo actualizar. Revisa la conexión y los permisos; se conservan los datos anteriores.")) }
                }
            }
            store.update { Scheduling.finishFocus(it, now) }
            Reminders.reschedule(context); Widgets.refresh(context)
        }
    }
}
