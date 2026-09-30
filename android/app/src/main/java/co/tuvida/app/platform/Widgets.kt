package co.tuvida.app.platform

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import co.tuvida.app.*
import co.tuvida.app.domain.Finance
import java.time.*
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

open class BaseWidget(private val route: String, private val title: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val data = (context.applicationContext as TuVidaApplication).store.current
        val today = LocalDate.now(); val now = System.currentTimeMillis()
        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply { maximumFractionDigits = 0 }
        val body = when (route) {
            "finance" -> { val s = Finance.summary(data, YearMonth.from(today)); "Para hoy: ${currency.format(s.today)}\nDisponible: ${currency.format(s.free)}\nPendientes: ${Finance.occurrences(data, YearMonth.from(today)).count { !it.paid && it.movement.type == "expense" }} pagos" }
            "health" -> data.health.find { it.date == today.toString() }?.let { if (it.exercise) "Ejercicio registrado · ${it.minutes} min\n${it.activity}" else "Día sin ejercicio registrado\nMañana puedes retomar" } ?: "Hoy todavía sin registro\nAnota tus comidas y actividad"
            "focus" -> if (data.focus.running) { if (data.focus.mode == "stopwatch") "Cronómetro en marcha" else "Enfoque · ${((data.focus.deadline - now).coerceAtLeast(0) / 60000)} min restantes" } else "${data.preferences.workMinutes} min de enfoque\n${data.preferences.breakMinutes} min de pausa\n${data.focus.completed} sesiones completadas"
            else -> {
                val upcoming = (data.events.filter { it.end >= now }.map { it.start to it.title } + data.tasks.filter { !it.done }.flatMap { task -> co.tuvida.app.domain.Scheduling.taskDates(task, now).filter { it >= now }.take(1).map { it to task.title } }).sortedBy { it.first }.take(3)
                if (upcoming.isEmpty()) "Tu agenda está libre\nAñade una tarea o conecta Google" else upcoming.joinToString("\n") { "${Instant.ofEpochMilli(it.first).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))} · ${it.second}" }
            }
        }
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.widget_title, title); views.setTextViewText(R.id.widget_body, body)
            val intent = Intent(context, MainActivity::class.java).putExtra("route", route).setData(android.net.Uri.parse("tuvida://widget/$route/$id")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
}
class AgendaWidget : BaseWidget("agenda", "Tu Vida · Próximos planes")
class FinanceWidget : BaseWidget("finance", "Tu Vida · Tu dinero")
class HealthWidget : BaseWidget("health", "Tu Vida · Salud de hoy")
class FocusWidget : BaseWidget("focus", "Tu Vida · Enfoque")
object Widgets {
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(AgendaWidget(), FinanceWidget(), HealthWidget(), FocusWidget()).forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider.javaClass)); if (ids.isNotEmpty()) provider.onUpdate(context, manager, ids)
        }
    }
}
