package co.tuvida.app

import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.tuvida.app.data.*
import co.tuvida.app.domain.Reminder
import co.tuvida.app.platform.Reminders
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var app: TuVidaApplication
    @Before fun setup() {
        app = ui.activity.application as TuVidaApplication
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("pm grant co.tuvida.app android.permission.POST_NOTIFICATIONS").close()
        automation.executeShellCommand("appops set co.tuvida.app SCHEDULE_EXACT_ALARM allow").close()
        app.getSystemService(NotificationManager::class.java).cancelAll()
        app.store.restore(AppData(preferences = Preferences(football = emptySet(), quietEnabled = false, exerciseReminder = false)))
    }
    @Test fun finishingTimerWithoutAnOpenActivityDeliversNotification() {
        app.store.update { it.copy(focus = FocusState(running = true, deadline = System.currentTimeMillis() + 5000)) }
        Reminders.reschedule(app)
        ui.activity.runOnUiThread { ui.activity.finish() }
        val end = System.currentTimeMillis() + 20000
        while (System.currentTimeMillis() < end && app.store.current.studyLog.isEmpty()) Thread.sleep(100)
        Assert.assertEquals(1, app.store.current.studyLog.size)
        Assert.assertFalse(app.store.current.focus.running)
        Assert.assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.notification.extras.getString("android.title") == "Sesión terminada" })
    }
    @Test fun completeActionUpdatesTaskAndCancelsNotification() {
        val task = Task(title = "Tarea de prueba", due = System.currentTimeMillis() + 60000)
        app.store.update { it.copy(tasks = listOf(task)) }
        val reminder = Reminder("action-test", task.due, task.title, "Completa la tarea", "tasks", task.id)
        Reminders.show(app, reminder)
        val notification = app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
        notification.actions.first { it.title == "Completar" }.actionIntent.send()
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end && !app.store.current.tasks.single().done) Thread.sleep(100)
        Assert.assertTrue(app.store.current.tasks.single().done)
    }
    @Test fun snoozeActionIsPersistedForRescheduling() {
        val reminder = Reminder("snooze-test", System.currentTimeMillis(), "Prueba", "Aviso", "tasks")
        Reminders.show(app, reminder)
        app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification.actions.first { it.title == "En 10 min" }.actionIntent.send()
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end && app.store.current.snoozed.isEmpty()) Thread.sleep(100)
        Assert.assertEquals(1, app.store.current.snoozed.size)
        Assert.assertTrue(app.store.current.snoozed.single().at > System.currentTimeMillis())
    }
}
