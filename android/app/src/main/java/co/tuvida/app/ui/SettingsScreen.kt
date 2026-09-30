@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package co.tuvida.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.*
import co.tuvida.app.platform.*

@Composable fun SettingsScreen(data: AppData, vm: AppViewModel, requestCalendar: () -> Unit, requestNotifications: () -> Unit, exact: () -> Unit, backup: () -> Unit, restore: () -> Unit, csv: () -> Unit, editor: (Editor) -> Unit) {
    val p = data.preferences; val context = LocalContext.current; val sources by vm.calendars.collectAsState(); val busy by vm.busy.collectAsState()
    var url by remember { mutableStateOf("") }; var lead by remember { mutableStateOf(p.leadMinutes.toString()) }; var quietStart by remember { mutableStateOf(p.quietStart.toString()) }; var quietEnd by remember { mutableStateOf(p.quietEnd.toString()) }; var exerciseHour by remember { mutableStateOf(p.exerciseHour.toString()) }; var keywords by remember { mutableStateOf(p.studyKeywords) }; var studyLead by remember { mutableStateOf(p.studyLeadMinutes.toString()) }; var studyDuration by remember { mutableStateOf(p.studyDuration.toString()) }
    val calendarGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("Tu apariencia"); Choice("Tema", p.theme, mapOf("system" to "Según Android", "light" to "Claro", "dark" to "Oscuro")) { choice -> vm.preferences { it.copy(theme = choice) } } }
        item { Section("Google Calendar", "Conecta los calendarios que ya sincroniza tu cuenta Google en el teléfono."); Button(requestCalendar) { Text(if (calendarGranted) "Revisar permiso de calendario" else "Permitir acceso a calendarios") }; Text("La conexión es de solo lectura. No se modifican tus eventos.", style = MaterialTheme.typography.bodySmall) }
        if (sources.isEmpty()) item { Info("No hay calendarios visibles", "Añade tu cuenta Google en los ajustes del teléfono, activa la sincronización de Calendario y pulsa Actualizar.", Icons.Outlined.CalendarMonth) }
        sources.forEach { source -> item(key = "calendar-${source.id}") { Toggle(source.name, source.id in p.calendarIds, { checked -> vm.preferences { it.copy(calendarIds = if (checked) it.calendarIds + source.id else it.calendarIds - source.id) } }, source.account) } }
        item { OutlinedButton({ context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }) { Text("Abrir sincronización de Android") }; TextButton(vm::sync, enabled = !busy) { Text(if (busy) "Actualizando…" else "Actualizar calendarios y partidos") } }
        item { Section("Conectar por iCal", "Opcional: Google Calendar → Ajustes → Integrar calendario → Dirección secreta en formato iCal."); Field("Dirección iCal de Google", url, { url = it }, helper = "Se guarda cifrada en este teléfono y se excluye de las copias."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.connectGoogle(url) }, enabled = url.isNotBlank()) { Text("Conectar") }; TextButton({ vm.connectGoogle(""); url = "" }) { Text("Desconectar iCal") } } }
        item { Section("Tus equipos", "Calendarios públicos de Fixtur.es, como en Aurora.") }
        Calendars.teams.forEach { (id, name) -> item { Toggle(name, id in p.football, { checked -> vm.preferences { it.copy(football = if (checked) it.football + id else it.football - id) } }) } }
        item { Section("Notificaciones"); Toggle("Recibir recordatorios", p.notifications, { enabled -> vm.preferences { it.copy(notifications = enabled) } }); Choice("Intensidad", p.intensity, mapOf("intense" to "Intensa", "normal" to "Normal")) { choice -> vm.preferences { it.copy(intensity = choice) } }; Text("Intensa: aviso anticipado, a 10 minutos y al empezar; las tareas pendientes se recuerdan tres veces más cada 15 minutos. Los canales de Android controlan el sonido.", style = MaterialTheme.typography.bodySmall); Field("Anticipación (0–1440 min)", lead, { lead = it.filter(Char::isDigit) }, true) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(requestNotifications) { Text(if (notificationsGranted) "Permiso autorizado" else "Permitir notificaciones") }; OutlinedButton(exact) { Text(if (Reminders.exact(context)) "Alarmas precisas autorizadas" else "Permitir alarmas precisas") } }; OutlinedButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Sonidos y canales de Android") } }
        item { Toggle("Horas de silencio", p.quietEnabled, { checked -> vm.preferences { it.copy(quietEnabled = checked) } }, "Los temporizadores iniciados por ti avisan también durante el silencio."); Field("Silencio desde (hora 0–23)", quietStart, { quietStart = it.filter(Char::isDigit) }, true); Field("Silencio hasta (hora 0–23)", quietEnd, { quietEnd = it.filter(Char::isDigit) }, true) }
        item { Section("Ejercicio diario"); Toggle("Recordarme hacer ejercicio", p.exerciseReminder, { enabled -> vm.preferences { it.copy(exerciseReminder = enabled) } }); Field("Hora del recordatorio (0–23)", exerciseHour, { exerciseHour = it.filter(Char::isDigit) }, true); Text("En intensidad alta avisa a esa hora, media hora después y una hora después. Deja de insistir cuando registras ejercicio.", style = MaterialTheme.typography.bodySmall) }
        item { Section("Estudio según tu calendario"); Toggle("Recordatorios para eventos de estudio", p.studyReminders, { enabled -> vm.preferences { it.copy(studyReminders = enabled) } }); Field("Palabras del título, separadas por comas", keywords, { keywords = it.take(500) }); Field("Preparar estudio antes (0–1440 min)", studyLead, { studyLead = it.filter(Char::isDigit) }, true); Field("Duración de la sesión sugerida (1–180 min)", studyDuration, { studyDuration = it.filter(Char::isDigit) }, true); Text("Un evento cuyo título coincida recibirá un aviso de preparación. En Agenda puedes crear una sesión para cualquier evento personal.", style = MaterialTheme.typography.bodySmall) }
        item { Button({
            val l = lead.toIntOrNull(); val s = quietStart.toIntOrNull(); val e = quietEnd.toIntOrNull(); val h = exerciseHour.toIntOrNull(); val sl = studyLead.toIntOrNull(); val sd = studyDuration.toIntOrNull()
            if (l !in 0..1440 || s !in 0..23 || e !in 0..23 || h !in 0..23 || sl !in 0..1440 || sd !in 1..180) vm.message.value = "Revisa las horas y duraciones indicadas." else { vm.preferences { it.copy(leadMinutes = l!!, quietStart = s!!, quietEnd = e!!, exerciseHour = h!!, studyKeywords = keywords, studyLeadMinutes = sl!!, studyDuration = sd!!) }; vm.message.value = "Preferencias de recordatorios guardadas." }
        }, Modifier.fillMaxWidth()) { Text("Guardar horarios y recordatorios") } }
        item { Section("Tu Redmi y los avisos"); Info("Permite que Tu Vida funcione en segundo plano", "En HyperOS/MIUI revisa Inicio automático, Notificaciones y Ahorro de batería de Tu Vida. Para avisos intensos, permite inicio automático y funcionamiento sin restricciones. Android puede retrasar avisos si no permites alarmas precisas.", Icons.Outlined.NotificationsActive); OutlinedButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Abrir ajustes de Tu Vida") } }
        item { Section("Widgets de inicio"); Text("Mantén pulsada la pantalla principal → Widgets → Tu Vida. Añade Agenda, Finanzas, Salud y Enfoque. Pulsa cualquiera para abrir su apartado.") }
        item { Section("Tus datos", "Guardados en este teléfono. Exporta copias antes de desinstalar o cambiar de dispositivo."); Button(backup, Modifier.fillMaxWidth()) { Text("Exportar copia JSON") }; OutlinedButton(restore, Modifier.fillMaxWidth()) { Text("Restaurar Tu Vida o Mi Plata Clara") }; OutlinedButton(csv, Modifier.fillMaxWidth()) { Text("Exportar 12 meses a CSV") }; TextButton(vm::undo) { Text("Deshacer la última eliminación o restauración") }; if (data.lastBackup > 0) Text("Última copia: ${dateLabel(data.lastBackup)}", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton({ editor(Editor("financeSettings")) }, Modifier.fillMaxWidth()) { Text("Ajustes de presupuesto y festivos") }; Text("Tu Vida 1.0.0 · Android nativo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}
