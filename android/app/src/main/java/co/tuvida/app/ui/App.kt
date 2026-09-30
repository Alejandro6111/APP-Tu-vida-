@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package co.tuvida.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.tuvida.app.data.*
import co.tuvida.app.platform.*
import java.time.*

data class Editor(val type: String, val id: String = "", val date: String = LocalDate.now().toString())
private val destinations = listOf("home" to "Hoy", "finance" to "Dinero", "agenda" to "Agenda", "focus" to "Enfoque", "health" to "Salud")
private val navIcons = listOf(Icons.Outlined.Home, Icons.Outlined.AccountBalanceWallet, Icons.Outlined.CalendarMonth, Icons.Outlined.Timer, Icons.Outlined.FavoriteBorder)

@Composable fun App(vm: AppViewModel, route: String, navigate: (String) -> Unit) {
    val data by vm.state.collectAsState(); val context = LocalContext.current
    val message by vm.message.collectAsState(); val preview by vm.importPreview.collectAsState()
    var editor by remember { mutableStateOf<Editor?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { vm.export(it, false) } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { vm.export(it, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { vm.refreshSources(); vm.sync() } else vm.message.value = "Puedes activar Calendario después en los permisos de Android." }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.message.value = if (granted) "Notificaciones autorizadas." else "Los recordatorios requieren el permiso de notificaciones de Android." }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snackbar.showSnackbar(message); if (vm.message.value == message) vm.message.value = "" } }
    BackHandler(enabled = route != "home" && editor == null) { navigate("home") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) NavigationRail { Spacer(Modifier.height(24.dp)); destinations.forEachIndexed { index, (key, title) -> NavigationRailItem(selected = route == key, onClick = { navigate(key) }, icon = { Icon(navIcons[index], title) }, label = { Text(title) }) } }
            Scaffold(modifier = Modifier.weight(1f), topBar = { TopAppBar(title = { Text(if (route == "settings") "Ajustes" else destinations.find { it.first == route }?.second?.let { if (it == "Hoy") "Tu Vida" else it } ?: "Tu Vida") }, navigationIcon = { if (route == "settings") IconButton({ navigate("home") }) { Icon(Icons.Outlined.ArrowBack, "Volver") } }, actions = { if (route != "settings") IconButton({ navigate("settings") }) { Icon(Icons.Outlined.Settings, "Ajustes") } }) }, bottomBar = {
                if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) { destinations.forEachIndexed { index, (key, title) -> NavigationBarItem(selected = route == key, onClick = { navigate(key) }, icon = { Icon(navIcons[index], null) }, label = { Text(title) }) } }
            }, snackbarHost = { SnackbarHost(snackbar) }, floatingActionButton = {
                when (route) {
                    "agenda" -> FloatingActionButton({ editor = Editor("task") }) { Icon(Icons.Outlined.Add, "Añadir tarea o sesión") }
                    "finance" -> FloatingActionButton({ editor = Editor("movement") }) { Icon(Icons.Outlined.Add, "Nuevo movimiento") }
                    "health" -> FloatingActionButton({ editor = Editor("health") }) { Icon(Icons.Outlined.Edit, "Registrar hoy") }
                }
            }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (route) {
                        "finance" -> FinanceScreen(data, vm) { editor = it }
                        "agenda" -> AgendaScreen(data, vm, { editor = it }, { navigate("settings") }, { event ->
                            vm.change { d -> d.copy(tasks = d.tasks + Task(title = "Estudiar: ${event.title}", due = event.start, kind = "study", durationMinutes = d.preferences.studyDuration)) }; vm.message.value = "Sesión de estudio creada en tu agenda."
                        })
                        "focus" -> FocusScreen(data, vm) { editor = Editor("timer") }
                        "health" -> HealthScreen(data) { editor = Editor("health", date = it) }
                        "settings" -> SettingsScreen(data, vm,
                            requestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                            requestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                            exact = { if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) },
                            backup = { export.launch("tu-vida-${LocalDate.now()}.json") }, restore = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, csv = { csv.launch("tu-vida-12-meses.csv") }, editor = { editor = it })
                        else -> HomeScreen(data, vm, navigate) { editor = it }
                    }
                }
            }
        }
    }
    editor?.let { value -> ModalBottomSheet(onDismissRequest = { editor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { EditorForm(value, data, vm) { editor = null } } }
    preview?.let { candidate -> AlertDialog(onDismissRequest = { vm.importPreview.value = null }, title = { Text("Restaurar esta copia") }, text = { Text("Contiene ${candidate.movements.size} movimientos, ${candidate.tasks.size} tareas y ${candidate.health.size} registros de salud. Reemplazará tus datos actuales. Puedes deshacer la restauración desde Ajustes hasta el siguiente cambio con opción de deshacer.") }, confirmButton = { TextButton(vm::confirmImport) { Text("Restaurar") } }, dismissButton = { TextButton({ vm.importPreview.value = null }) { Text("Cancelar") } }) }
}
