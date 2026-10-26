package org.nikoresu.transcare.ui.exams

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.ExamRepository
import org.nikoresu.transcare.domain.model.ExamResult

class ExamsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExamRepository(application)

    val results: StateFlow<List<ExamResult>> = repository.results

    fun addResult(result: ExamResult) {
        viewModelScope.launch { repository.add(result) }
    }

    fun updateResult(result: ExamResult) {
        viewModelScope.launch { repository.update(result) }
    }

    fun deleteResult(result: ExamResult) {
        viewModelScope.launch { repository.delete(result) }
    }

    /** Suspends until the imported data has been persisted. */
    suspend fun replaceAll(results: List<ExamResult>) {
        repository.replaceAll(results)
    }
}
