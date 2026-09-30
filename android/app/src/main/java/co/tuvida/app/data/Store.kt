package co.tuvida.app.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class Store(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "tuvida.json"))
    var recoveryError: String = ""; private set
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()
    val current get() = mutable.value
    private fun load(): AppData = if (!file.baseFile.exists()) AppData() else try {
        Backup.gson.fromJson(file.openRead().bufferedReader().use { it.readText() }, AppData::class.java).also(Backup::validate)
    } catch (_: Exception) {
        recoveryError = "No se pudo leer tu archivo de datos. Se conserva intacto. Exporta o revisa una copia antes de reemplazarlo."
        AppData()
    }
    @Synchronized fun update(transform: (AppData) -> AppData) {
        check(recoveryError.isEmpty()) { recoveryError }
        val next = transform(current)
        Backup.validate(next)
        write(next)
    }
    @Synchronized fun restore(data: AppData) { Backup.validate(data); write(data); recoveryError = "" }
    private fun write(data: AppData) {
        val stream = file.startWrite()
        try { stream.write(Backup.encode(data).toByteArray(Charsets.UTF_8)); file.finishWrite(stream); mutable.value = data }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
