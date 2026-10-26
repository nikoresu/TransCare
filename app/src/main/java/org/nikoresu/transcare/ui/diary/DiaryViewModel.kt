package org.nikoresu.transcare.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.nikoresu.transcare.data.DiaryRepository
import org.nikoresu.transcare.domain.model.DiaryEntry

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DiaryRepository(application)

    val entries: StateFlow<List<DiaryEntry>> = repository.entries

    /** True once stored entries have been loaded. */
    val isLoaded: StateFlow<Boolean> = repository.isLoaded

    fun addEntry(entry: DiaryEntry) {
        viewModelScope.launch { repository.add(entry) }
    }

    fun updateEntry(entry: DiaryEntry) {
        viewModelScope.launch { repository.update(entry) }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    /** Suspends until the imported entries have been persisted. */
    suspend fun replaceAll(entries: List<DiaryEntry>) {
        repository.replaceAll(entries)
    }
}
