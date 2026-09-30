package co.tuvida.app

import android.app.Application
import co.tuvida.app.data.Store
import co.tuvida.app.platform.Reminders
import co.tuvida.app.platform.SyncWorker

class TuVidaApplication : Application() {
    lateinit var store: Store; private set
    override fun onCreate() {
        super.onCreate(); store = Store(this); Reminders.channels(this); SyncWorker.install(this)
    }
}
