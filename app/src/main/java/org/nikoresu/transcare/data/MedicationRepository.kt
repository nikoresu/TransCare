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
import org.json.JSONObject
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus
import org.nikoresu.transcare.domain.model.MedicationRoute
import org.nikoresu.transcare.domain.model.MedicationSchedule

/**
 * Local storage for medications and their dose logs. Both lists are persisted
 * to JSON files inside the app's internal storage.
 */
class MedicationRepository(context: Context) {

    private val medicationsFile = File(context.filesDir, MEDICATIONS_FILE_NAME)
    private val dosesFile = File(context.filesDir, DOSES_FILE_NAME)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _doses = MutableStateFlow<List<MedicationDose>>(emptyList())
    val doses: StateFlow<List<MedicationDose>> = _doses.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** True once the stored medications and dose logs have been read. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                LocalDataCipher.migrateIfNeeded(medicationsFile)
                LocalDataCipher.migrateIfNeeded(dosesFile)
                _medications.value = readMedicationsFile(medicationsFile)
                _doses.value = readDosesFile(dosesFile)
                _isLoaded.value = true
            }
        }
    }

    suspend fun addMedication(medication: Medication) {
        mutate { medications, doses ->
            val newMedication = medication.copy(
                id = (medications.maxOfOrNull { it.id } ?: 0L) + 1L,
            )
            (medications + newMedication) to doses
        }
    }

    suspend fun updateMedication(medication: Medication) {
        mutate { medications, doses ->
            medications.map { if (it.id == medication.id) medication else it } to doses
        }
    }

    /** Deletes a medication together with its dose history. */
    suspend fun deleteMedication(medication: Medication) {
        mutate { medications, doses ->
            medications.filterNot { it.id == medication.id } to
                doses.filterNot { it.medicationId == medication.id }
        }
    }

    /**
     * Records or replaces the status for one dose slot. When the status changes
     * to or from TAKEN, the tracked supply is adjusted by one dose.
     */
    suspend fun logDose(
        medicationId: Long,
        dayStartMillis: Long,
        doseIndex: Int,
        status: MedicationDoseStatus,
        loggedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mutate { medications, doses ->
            val existing = doses.firstOrNull { dose ->
                dose.medicationId == medicationId &&
                    dose.dayStartMillis == dayStartMillis &&
                    dose.doseIndex == doseIndex
            }
            val updatedDoses = doses.filterNot { dose ->
                dose.medicationId == medicationId &&
                    dose.dayStartMillis == dayStartMillis &&
                    dose.doseIndex == doseIndex
            } + MedicationDose(
                id = existing?.id ?: ((doses.maxOfOrNull { it.id } ?: 0L) + 1L),
                medicationId = medicationId,
                dayStartMillis = dayStartMillis,
                doseIndex = doseIndex,
                status = status,
                loggedAtMillis = loggedAtMillis,
            )

            val supplyDelta = when {
                existing?.status == MedicationDoseStatus.TAKEN &&
                    status != MedicationDoseStatus.TAKEN -> 1
                existing?.status != MedicationDoseStatus.TAKEN &&
                    status == MedicationDoseStatus.TAKEN -> -1
                else -> 0
            }
            val updatedMedications = if (supplyDelta == 0) {
                medications
            } else {
                medications.map { medication ->
                    if (medication.id != medicationId) {
                        medication
                    } else {
                        medication.copy(
                            supplyDoses = medication.supplyDoses?.let { supply ->
                                (supply + supplyDelta).coerceAtLeast(0)
                            },
                        )
                    }
                }
            }
            updatedMedications to updatedDoses
        }
    }

    /** Removes a logged dose, restoring the supply if it had been taken. */
    suspend fun removeDose(medicationId: Long, dayStartMillis: Long, doseIndex: Int) {
        mutate { medications, doses ->
            val existing = doses.firstOrNull { dose ->
                dose.medicationId == medicationId &&
                    dose.dayStartMillis == dayStartMillis &&
                    dose.doseIndex == doseIndex
            }
            val updatedMedications = if (existing?.status == MedicationDoseStatus.TAKEN) {
                medications.map { medication ->
                    if (medication.id != medicationId) {
                        medication
                    } else {
                        medication.copy(
                            supplyDoses = medication.supplyDoses?.let { supply ->
                                (supply + 1).coerceAtLeast(0)
                            },
                        )
                    }
                }
            } else {
                medications
            }
            updatedMedications to doses.filterNot { dose ->
                dose.medicationId == medicationId &&
                    dose.dayStartMillis == dayStartMillis &&
                    dose.doseIndex == doseIndex
            }
        }
    }

    /** Replaces all medications and dose logs, e.g. when importing a backup. */
    suspend fun replaceAll(
        medications: List<Medication>,
        doses: List<MedicationDose>,
    ) {
        mutate { _, _ ->
            val idMap = mutableMapOf<Long, Long>()
            val usedIds = mutableSetOf<Long>()
            var nextId = 1L
            val newMedications = medications.map { medication ->
                val originalId = medication.id
                var id = originalId
                if (id <= 0L || !usedIds.add(id)) {
                    while (!usedIds.add(nextId)) nextId++
                    id = nextId
                }
                if (originalId > 0L) idMap.putIfAbsent(originalId, id)
                medication.copy(id = id)
            }
            val newDoses = doses
                .mapNotNull { dose ->
                    val medicationId = idMap[dose.medicationId] ?: return@mapNotNull null
                    dose.copy(medicationId = medicationId)
                }
                .distinctBy { Triple(it.medicationId, it.dayStartMillis, it.doseIndex) }
                .mapIndexed { index, dose -> dose.copy(id = index + 1L) }
            newMedications to newDoses
        }
    }

    private suspend fun mutate(
        block: (List<Medication>, List<MedicationDose>) ->
            Pair<List<Medication>, List<MedicationDose>>,
    ) {
        mutex.withLock {
            val (medications, doses) = block(_medications.value, _doses.value)
            _medications.value = medications
            _doses.value = doses
            withContext(Dispatchers.IO) {
                try {
                    writeMedicationsFile(medicationsFile, medications)
                    writeDosesFile(dosesFile, doses)
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not save medications", exception)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MedicationRepository"
        private const val MEDICATIONS_FILE_NAME = "medications.json"
        private const val DOSES_FILE_NAME = "medication_doses.json"

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_DOSE = "dose"
        private const val KEY_ROUTE = "route"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_START_DAY_START_MILLIS = "startDayStartMillis"
        private const val KEY_REMINDER_MINUTES = "reminderMinutes"
        private const val KEY_SUPPLY_DOSES = "supplyDoses"
        private const val KEY_NOTES = "notes"

        private const val KEY_MEDICATION_ID = "medicationId"
        private const val KEY_DAY_START_MILLIS = "dayStartMillis"
        private const val KEY_DOSE_INDEX = "doseIndex"
        private const val KEY_STATUS = "status"
        private const val KEY_LOGGED_AT_MILLIS = "loggedAtMillis"

        /** Reads medications directly from disk, for broadcast receivers. */
        fun readMedications(context: Context): List<Medication> =
            readMedicationsFile(File(context.filesDir, MEDICATIONS_FILE_NAME))

        /** Reads dose logs directly from disk, for broadcast receivers. */
        fun readDoses(context: Context): List<MedicationDose> =
            readDosesFile(File(context.filesDir, DOSES_FILE_NAME))

        internal fun parseMedication(json: JSONObject): Medication? {
            val name = json.optString(KEY_NAME).trim()
            val startDayStartMillis = json.optLong(KEY_START_DAY_START_MILLIS, -1L)
            if (name.isEmpty() || startDayStartMillis <= 0L) return null

            val reminderMinutes = if (
                json.has(KEY_REMINDER_MINUTES) && !json.isNull(KEY_REMINDER_MINUTES)
            ) {
                json.optInt(KEY_REMINDER_MINUTES, -1)
                    .takeIf { it in 0 until Medication.MINUTES_PER_DAY }
            } else {
                null
            }
            val supplyDoses = if (
                json.has(KEY_SUPPLY_DOSES) && !json.isNull(KEY_SUPPLY_DOSES)
            ) {
                json.optInt(KEY_SUPPLY_DOSES, -1).takeIf { it >= 0 }
            } else {
                null
            }

            return Medication(
                id = json.optLong(KEY_ID),
                name = name,
                dose = json.optString(KEY_DOSE).trim(),
                route = MedicationRoute.fromStorageValue(json.optString(KEY_ROUTE)),
                schedule = MedicationSchedule.fromStorageValue(json.optString(KEY_SCHEDULE)),
                startDayStartMillis = startDayStartMillis,
                reminderMinutes = reminderMinutes,
                supplyDoses = supplyDoses,
                notes = json.optString(KEY_NOTES).trim(),
            )
        }

        internal fun parseDose(json: JSONObject): MedicationDose? {
            val medicationId = json.optLong(KEY_MEDICATION_ID, 0L)
            val dayStartMillis = json.optLong(KEY_DAY_START_MILLIS, -1L)
            val doseIndex = json.optInt(KEY_DOSE_INDEX, -1)
            val status = MedicationDoseStatus.fromStorageValue(json.optString(KEY_STATUS))
                ?: return null
            if (
                medicationId <= 0L ||
                dayStartMillis <= 0L ||
                doseIndex !in 0..MAX_DOSE_INDEX
            ) {
                return null
            }
            return MedicationDose(
                id = json.optLong(KEY_ID),
                medicationId = medicationId,
                dayStartMillis = dayStartMillis,
                doseIndex = doseIndex,
                status = status,
                loggedAtMillis = json.optLong(KEY_LOGGED_AT_MILLIS, 0L),
            )
        }

        private fun readMedicationsFile(file: File): List<Medication> {
            return try {
                val text = LocalDataCipher.readText(file) ?: return emptyList()
                val jsonArray = JSONArray(text)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val json = jsonArray.optJSONObject(index) ?: continue
                        parseMedication(json)?.let(::add)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not read medications", exception)
                emptyList()
            }
        }

        private fun readDosesFile(file: File): List<MedicationDose> {
            return try {
                val text = LocalDataCipher.readText(file) ?: return emptyList()
                val jsonArray = JSONArray(text)
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val json = jsonArray.optJSONObject(index) ?: continue
                        parseDose(json)?.let(::add)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Could not read dose logs", exception)
                emptyList()
            }
        }

        private fun writeMedicationsFile(file: File, medications: List<Medication>) {
            val array = JSONArray()
            medications.forEach { array.put(it.toJsonObject()) }
            LocalDataCipher.writeText(file, array.toString())
        }

        private fun writeDosesFile(file: File, doses: List<MedicationDose>) {
            val array = JSONArray()
            doses.forEach { array.put(it.toJsonObject()) }
            LocalDataCipher.writeText(file, array.toString())
        }

        private const val MAX_DOSE_INDEX = 3
    }
}
