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
import org.nikoresu.transcare.domain.model.ExamMetric
import org.nikoresu.transcare.domain.model.ExamResult

/**
 * Local storage for exam results. The data is persisted to a JSON file inside
 * the app's internal storage so it survives app restarts.
 */
class ExamRepository(context: Context) {

    private val resultsFile = File(context.filesDir, RESULTS_FILE_NAME)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveMutex = Mutex()
    private val _results = MutableStateFlow<List<ExamResult>>(emptyList())
    val results: StateFlow<List<ExamResult>> = _results.asStateFlow()

    init {
        scope.launch {
            _results.value = loadResults()
        }
    }

    suspend fun add(result: ExamResult) {
        val newResult = result.copy(id = nextId())
        _results.value = _results.value + newResult
        saveResults()
    }

    /** Appends several results at once, preserving existing entries. */
    suspend fun addAll(results: List<ExamResult>) {
        if (results.isEmpty()) return
        var nextId = nextId()
        _results.value = _results.value + results.map { result ->
            result.copy(id = nextId++)
        }
        saveResults()
    }

    suspend fun update(result: ExamResult) {
        _results.value = _results.value.map { if (it.id == result.id) result else it }
        saveResults()
    }

    suspend fun delete(result: ExamResult) {
        _results.value = _results.value.filterNot { it.id == result.id }
        saveResults()
    }

    /** Replaces all exam results, e.g. when importing a backup. */
    suspend fun replaceAll(results: List<ExamResult>) {
        _results.value = results.mapIndexed { index, result ->
            result.copy(id = index + 1L)
        }
        saveResults()
    }

    private fun nextId(): Long = (_results.value.maxOfOrNull { it.id } ?: 0L) + 1L

    private suspend fun saveResults() {
        saveMutex.withLock {
            val array = JSONArray()
            _results.value.forEach { result ->
                array.put(result.toJsonObject())
            }
            withContext(Dispatchers.IO) {
                try {
                    resultsFile.writeText(array.toString())
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not save exam results", exception)
                }
            }
        }
    }

    private fun loadResults(): List<ExamResult> {
        if (!resultsFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(resultsFile.readText())
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val json = jsonArray.optJSONObject(index) ?: continue
                    val metric = ExamMetric.entries.firstOrNull {
                        it.name == json.optString(KEY_METRIC)
                    } ?: continue
                    val value = json.optDouble(KEY_VALUE, Double.NaN)
                    if (!value.isFinite()) continue

                    add(
                        ExamResult(
                            id = json.optLong(KEY_ID),
                            metric = metric,
                            value = value,
                            dayStartMillis = json.optLong(KEY_DAY_START_MILLIS),
                            note = json.optString(KEY_NOTE),
                        ),
                    )
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not read exam results", exception)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "ExamRepository"
        const val RESULTS_FILE_NAME = "exam_results.json"
        const val KEY_ID = "id"
        const val KEY_METRIC = "metric"
        const val KEY_VALUE = "value"
        const val KEY_DAY_START_MILLIS = "dayStartMillis"
        const val KEY_NOTE = "note"
    }
}
