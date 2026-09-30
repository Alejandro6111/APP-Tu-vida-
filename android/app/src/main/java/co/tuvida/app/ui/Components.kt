package co.tuvida.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Spanish: Locale = Locale.forLanguageTag("es-CO")
fun money(value: Long): String = NumberFormat.getCurrencyInstance(Spanish).apply { maximumFractionDigits = 0 }.format(value)
fun dateLabel(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Spanish))
fun monthLabel(value: YearMonth) = value.format(DateTimeFormatter.ofPattern("MMMM yyyy", Spanish)).replaceFirstChar { it.uppercase(Spanish) }
fun clock(value: Long): String { val seconds = value.coerceAtLeast(0) / 1000; return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) }

@Composable fun Section(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; action?.invoke()
    }
}
@Composable fun Info(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Info) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodyMedium) } }
    }
}
@Composable fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) { Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleLarge) }
}
@Composable fun AmountRow(label: String, amount: Long, suffix: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(money(amount) + suffix, color = if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) }
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit, numeric: Boolean = false, lines: Int = 1, helper: String = "") {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = lines == 1, minLines = lines, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text), supportingText = if (helper.isNotBlank()) { { Text(helper) } } else null)
}
@Composable fun Choice(label: String, value: String, options: Map<String, String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: ${options[value] ?: value}", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) }; DropdownMenu(expanded, { expanded = false }) { options.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { change(key); expanded = false }) } } }
}
@Composable fun DateField(label: String, value: String, change: (String) -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    OutlinedButton({ DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("$label: $value") }
}
@Composable fun TimeField(value: String, change: (String) -> Unit) {
    val context = LocalContext.current
    val time = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(18, 0))
    OutlinedButton({ TimePickerDialog(context, { _, h, m -> change("%02d:%02d".format(h, m)) }, time.hour, time.minute, true).show() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text("Hora: $value") }
}
@Composable fun Toggle(title: String, checked: Boolean, change: (Boolean) -> Unit, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Column(Modifier.weight(1f)) { Text(title); if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, change, Modifier.semantics { contentDescription = title }) }
}
@Composable fun MonthPicker(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { IconButton({ change(month.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, "Mes anterior") }; Text(monthLabel(month), style = MaterialTheme.typography.titleMedium); IconButton({ change(month.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, "Mes siguiente") } }
}
