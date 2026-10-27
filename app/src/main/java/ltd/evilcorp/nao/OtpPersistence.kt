package ltd.evilcorp.nao

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.saveable.Saver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

val HotpItemSetSaver = Saver<Set<HotpItem>, List<String>>(
    save = { set -> set.map { it.toJson().toString() } },
    restore = { list -> list.map { HotpItem.fromJson(JSONObject(it)) }.toSet() },
)

suspend fun saveItems(
    context: Context,
    items: List<OtpItem>,
) = withContext(Dispatchers.IO) {
    val array = JSONArray()
    items.forEach { array.put(it.toJson()) }

    val atomicFile = AtomicFile(File(context.filesDir, "nao.json"))
    var fos: FileOutputStream? = null
    try {
        fos = atomicFile.startWrite()
        fos.write(array.toString().toByteArray())
        atomicFile.finishWrite(fos)
    } catch (e: IOException) {
        Log.e("MainActivity", "Error saving items", e)
        atomicFile.failWrite(fos)
    }
}

suspend fun loadItems(context: Context): List<OtpItem> =
    withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "nao.json")
        if (!file.exists()) return@withContext emptyList()

        try {
            val atomicFile = AtomicFile(file)
            val jsonString = atomicFile.openRead().bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val items = mutableListOf<OtpItem>()
            for (i in 0 until array.length()) {
                items.add(OtpItem.fromJson(array.getJSONObject(i)))
            }
            items
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading items", e)
            val backupFile = File(context.filesDir, "nao-${Date().time}.json.old")
            file.renameTo(backupFile)
            emptyList()
        }
    }
