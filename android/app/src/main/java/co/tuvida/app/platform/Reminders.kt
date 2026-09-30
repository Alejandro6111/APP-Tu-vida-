package co.tuvida.app.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import co.tuvida.app.MainActivity
import co.tuvida.app.R
import co.tuvida.app.TuVidaApplication
import co.tuvida.app.data.*
import co.tuvida.app.domain.*
import java.time.*

object Reminders {
    val names = mapOf("tasks" to "Tareas y agenda", "matches" to "Partidos", "health" to "Ejercicio diario", "study" to "Estudio y temporizadores", "finance" to "Pagos y finanzas")
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        names.forEach { (id, name) -> manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true); description = "Recordatorios de Tu Vida; personaliza sonido y vibración en Android." }) }
    }
    fun exact(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun intent(context: Context, reminder: Reminder, action: String = "show"): Intent = Intent(context, ReminderReceiver::class.java).setAction(action).setData(android.net.Uri.parse("tuvida://reminder/${android.net.Uri.encode(reminder.id)}")).putExtra("reminder", Backup.gson.toJson(reminder))
    private fun pending(context: Context, reminder: Reminder, id: Int) = PendingIntent.getBroadcast(context, id, intent(context, reminder), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun reschedule(context: Context) {
        val store = (context.applicationContext as TuVidaApplication).store
        if (store.recoveryError.isNotEmpty()) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val old = prefs.getString("plan", "[]") ?: "[]"
        runCatching { Backup.gson.fromJson(old, Array<Reminder>::class.java).forEachIndexed { i, reminder -> alarms.cancel(pending(context, reminder, i)) } }
        val reminders = Scheduling.plan(store.current)
        prefs.edit().putString("plan", Backup.gson.toJson(reminders)).apply()
        reminders.forEachIndexed { index, reminder ->
            val pi = pending(context, reminder, index)
            try { if (exact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi) }
            catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi) }
        }
    }
    fun show(context: Context, reminder: Reminder) {
        val store = (context.applicationContext as TuVidaApplication).store
        val data = store.current; val now = System.currentTimeMillis()
        if (!data.preferences.notifications || (reminder.id != "focus" && Scheduling.quiet(now, data.preferences))) return
        if (reminder.taskId.isNotEmpty() && data.tasks.none { it.id == reminder.taskId && !it.done }) return
        if (reminder.channel == "health" && data.health.any { it.date == LocalDate.now().toString() && it.exercise }) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(context)
        val notificationId = reminder.id.hashCode()
        val open = PendingIntent.getActivity(context, notificationId, Intent(context, MainActivity::class.java).putExtra("route", reminder.route).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, reminder.channel).setSmallIcon(R.drawable.ic_app).setContentTitle(reminder.title).setContentText(reminder.body).setStyle(Notification.BigTextStyle().bigText(reminder.body)).setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setGroup("tuvida-${reminder.channel}")
        if (reminder.taskId.isNotEmpty()) {
            val complete = PendingIntent.getBroadcast(context, notificationId, intent(context, reminder, "complete"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Completar", complete).build())
        }
        val snooze = PendingIntent.getBroadcast(context, notificationId, intent(context, reminder, "snooze"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(Notification.Action.Builder(null, "En 10 min", snooze).build())
        context.getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = runCatching { Backup.gson.fromJson(intent.getStringExtra("reminder"), Reminder::class.java) }.getOrNull() ?: return
        val store = (context.applicationContext as TuVidaApplication).store
        if (store.recoveryError.isNotEmpty()) return
        when (intent.action) {
            "complete" -> {
                store.update { data -> data.copy(tasks = data.tasks.map { if (it.id == reminder.taskId) Scheduling.completeTask(it, System.currentTimeMillis()) else it }, snoozed = data.snoozed.filter { it.taskId != reminder.taskId }) }
                context.getSystemService(NotificationManager::class.java).cancel(reminder.id.hashCode()); Reminders.reschedule(context)
            }
            "snooze" -> {
                val next = reminder.copy(id = "snooze:${reminder.id}", at = System.currentTimeMillis() + 10 * 60000)
                store.update { data -> data.copy(snoozed = data.snoozed.filter { it.id != next.id && it.at > System.currentTimeMillis() } + SnoozedReminder(next.id, next.at, next.title, next.body, next.channel, next.taskId, next.route)) }
                Reminders.reschedule(context)
                context.getSystemService(NotificationManager::class.java).cancel(reminder.id.hashCode())
            }
            else -> {
                if (reminder.id.startsWith("snooze:")) store.update { it.copy(snoozed = it.snoozed.filter { s -> s.id != reminder.id }) }
                if (reminder.id == "focus") store.update { Scheduling.finishFocus(it, System.currentTimeMillis()) }
                Reminders.show(context, reminder)
            }
        }
        Widgets.refresh(context)
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        Reminders.reschedule(context); Widgets.refresh(context); SyncWorker.request(context)
    }
}
