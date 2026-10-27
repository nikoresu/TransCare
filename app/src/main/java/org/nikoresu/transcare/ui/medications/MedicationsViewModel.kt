package org.nikoresu.transcare.ui.medications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.MedicationRepository
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.MedicationDoseStatus

class MedicationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicationRepository(application)

    val medications: StateFlow<List<Medication>> = repository.medications
    val doses: StateFlow<List<MedicationDose>> = repository.doses

    /** True once stored medications and dose logs have been loaded. */
    val isLoaded: StateFlow<Boolean> = repository.isLoaded

    fun addMedication(medication: Medication) {
        viewModelScope.launch { repository.addMedication(medication) }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch { repository.updateMedication(medication) }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch { repository.deleteMedication(medication) }
    }

    fun logDose(
        medicationId: Long,
        dayStartMillis: Long,
        doseIndex: Int,
        status: MedicationDoseStatus,
    ) {
        viewModelScope.launch {
            repository.logDose(medicationId, dayStartMillis, doseIndex, status)
        }
    }

    fun removeDose(medicationId: Long, dayStartMillis: Long, doseIndex: Int) {
        viewModelScope.launch {
            repository.removeDose(medicationId, dayStartMillis, doseIndex)
        }
    }

    /** Suspends until the imported medications have been persisted. */
    suspend fun replaceAll(
        medications: List<Medication>,
        doses: List<MedicationDose>,
    ) {
        repository.replaceAll(medications, doses)
    }
}
