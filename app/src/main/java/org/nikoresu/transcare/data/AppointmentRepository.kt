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
import org.nikoresu.transcare.domain.model.Appointment

/**
 * Local storage for appointments. The data is persisted to a JSON file inside
 * the app's internal storage so it survives app restarts.
 */
class AppointmentRepository(context: Context) {

    private val appointmentsFile = File(context.filesDir, APPOINTMENTS_FILE_NAME)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveMutex = Mutex()
    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val appointments: StateFlow<List<Appointment>> = _appointments.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** True once the stored appointments have been read from disk. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            _appointments.value = loadAppointments()
            _isLoaded.value = true
        }
    }

    suspend fun add(appointment: Appointment) {
        val newAppointment = appointment.copy(id = nextId())
        _appointments.value = _appointments.value + newAppointment
        saveAppointments()
    }

    suspend fun update(appointment: Appointment) {
        _appointments.value = _appointments.value.map { if (it.id == appointment.id) appointment else it }
        saveAppointments()
    }

    suspend fun delete(appointment: Appointment) {
        _appointments.value = _appointments.value.filterNot { it.id == appointment.id }
        saveAppointments()
    }

    /** Replaces all appointments, e.g. when importing a backup. */
    suspend fun replaceAll(appointments: List<Appointment>) {
        _appointments.value = appointments.mapIndexed { index, appointment ->
            appointment.copy(id = index + 1L)
        }
        saveAppointments()
    }

    private fun nextId(): Long = (_appointments.value.maxOfOrNull { it.id } ?: 0L) + 1L

    private suspend fun saveAppointments() {
        saveMutex.withLock {
            val array = JSONArray()
            _appointments.value.forEach { appointment ->
                array.put(appointment.toJsonObject())
            }
            withContext(Dispatchers.IO) {
                try {
                    appointmentsFile.writeText(array.toString())
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not save appointments", exception)
                }
            }
        }
    }

    private fun loadAppointments(): List<Appointment> =
        readAppointmentsFile(appointmentsFile)

    companion object {
        const val TAG = "AppointmentRepository"
        private const val APPOINTMENTS_FILE_NAME = "appointments.json"
        private const val KEY_ID = "id"
        private const val KEY_DOCTOR_NAME = "doctorName"
        private const val KEY_PLACE = "place"
        private const val KEY_DAY_START_MILLIS = "dayStartMillis"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"

        /**
         * Reads appointments directly from disk. Used by broadcast receivers
         * that reschedule reminders when the app is not running.
         */
        fun readAppointments(context: Context): List<Appointment> =
            readAppointmentsFile(File(context.filesDir, APPOINTMENTS_FILE_NAME))

        private fun readAppointmentsFile(file: File): List<Appointment> {
            if (!file.exists()) return emptyList()
            return try {
                val jsonArray = JSONArray(file.readText())
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val json = jsonArray.optJSONObject(index) ?: continue
                        add(
                            Appointment(
                                id = json.optLong(KEY_ID),
                                doctorName = json.optString(KEY_DOCTOR_NAME),
                                place = json.optString(KEY_PLACE),
                                dayStartMillis = json.optLong(KEY_DAY_START_MILLIS),
                                hour = json.optInt(KEY_HOUR),
                                minute = json.optInt(KEY_MINUTE),
                            ),
                        )
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not read appointments", exception)
                emptyList()
            }
        }
    }
}
