package org.nikoresu.transcare.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.DiaryEntryType

/**
 * Local storage for diary entries. The data is persisted to a JSON file inside
 * the app's internal storage so it survives app restarts.
 */
class DiaryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val entriesFile = File(appContext.filesDir, ENTRIES_FILE_NAME)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveMutex = Mutex()
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** True once the stored entries have been read from disk. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            _entries.value = loadEntries()
            _isLoaded.value = true
        }
    }

    suspend fun add(entry: DiaryEntry) {
        val newEntry = entry.copy(id = nextId())
        _entries.value = _entries.value + newEntry
        saveEntries()
    }

    suspend fun update(entry: DiaryEntry) {
        val previous = _entries.value.firstOrNull { it.id == entry.id }
        _entries.value = _entries.value.map { if (it.id == entry.id) entry else it }
        saveEntries()
        if (previous != null) {
            AttachmentVault.deleteRemoved(
                context = appContext,
                previous = previous.attachments,
                updated = entry.attachments,
            )
        }
    }

    suspend fun delete(entry: DiaryEntry) {
        _entries.value = _entries.value.filterNot { it.id == entry.id }
        saveEntries()
        AttachmentVault.deleteAll(appContext, entry.attachments)
    }

    /** Replaces all diary entries, e.g. when importing a backup. */
    suspend fun replaceAll(entries: List<DiaryEntry>) {
        val previousAttachments = _entries.value.flatMap { it.attachments }
        val newAttachments = entries.flatMap { it.attachments }
        _entries.value = entries.mapIndexed { index, entry ->
            entry.copy(id = index + 1L)
        }
        saveEntries()
        AttachmentVault.deleteRemoved(
            context = appContext,
            previous = previousAttachments,
            updated = newAttachments,
        )
    }

    private fun nextId(): Long = (_entries.value.maxOfOrNull { it.id } ?: 0L) + 1L

    private suspend fun saveEntries() {
        saveMutex.withLock {
            val array = JSONArray()
            _entries.value.forEach { entry ->
                array.put(entry.toJsonObject())
            }
            withContext(Dispatchers.IO) {
                try {
                    LocalDataCipher.writeText(entriesFile, array.toString())
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not save diary entries", exception)
                }
            }
        }
    }

    private fun loadEntries(): List<DiaryEntry> {
        LocalDataCipher.migrateIfNeeded(entriesFile)
        return try {
            val text = LocalDataCipher.readText(entriesFile) ?: return emptyList()
            val jsonArray = JSONArray(text)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val json = jsonArray.optJSONObject(index) ?: continue
                    val dayStartMillis = json.optLong(KEY_DAY_START_MILLIS)
                    if (dayStartMillis <= 0L) continue
                    add(
                        DiaryEntry(
                            id = json.optLong(KEY_ID),
                            dayStartMillis = dayStartMillis,
                            title = json.optString(KEY_TITLE),
                            text = json.optString(KEY_TEXT),
                            type = DiaryEntryType.fromStorageValue(json.optString(KEY_TYPE)),
                            attachments = json.optAttachments(),
                        ),
                    )
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not read diary entries", exception)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "DiaryRepository"
        const val ENTRIES_FILE_NAME = "diary_entries.json"
        const val KEY_ID = "id"
        const val KEY_DAY_START_MILLIS = "dayStartMillis"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_TYPE = "type"
    }
}
