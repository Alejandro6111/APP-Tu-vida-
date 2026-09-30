package co.tuvida.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import co.tuvida.app.platform.*
import co.tuvida.app.ui.*

class MainActivity : ComponentActivity() {
    private var route by mutableStateOf("home")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); route = intent.getStringExtra("route") ?: "home"
        setContent { val vm: AppViewModel = viewModel(); val data by vm.state.collectAsState(); TuVidaTheme(data.preferences.theme) { App(vm, route) { route = it } } }
        SyncWorker.request(this)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); route = intent.getStringExtra("route") ?: "home" }
    override fun onResume() { super.onResume(); Reminders.reschedule(this); Widgets.refresh(this) }
}
