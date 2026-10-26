package org.nikoresu.transcare.ui.appointments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.AppointmentRepository
import org.nikoresu.transcare.domain.model.Appointment

class AppointmentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppointmentRepository(application)

    val appointments: StateFlow<List<Appointment>> = repository.appointments

    /** True once stored appointments have been loaded. */
    val isLoaded: StateFlow<Boolean> = repository.isLoaded

    fun addAppointment(appointment: Appointment) {
        viewModelScope.launch { repository.add(appointment) }
    }

    fun updateAppointment(appointment: Appointment) {
        viewModelScope.launch { repository.update(appointment) }
    }

    fun deleteAppointment(appointment: Appointment) {
        viewModelScope.launch { repository.delete(appointment) }
    }

    /** Suspends until the imported data has been persisted. */
    suspend fun replaceAll(appointments: List<Appointment>) {
        repository.replaceAll(appointments)
    }
}
