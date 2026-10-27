package org.nikoresu.transcare.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.data.ProfileImageStore
import org.nikoresu.transcare.data.ProfileRepository
import org.nikoresu.transcare.domain.model.Profile

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProfileRepository(application)

    val profile: StateFlow<Profile> = repository.profile

    /** True once the stored profile has been loaded. */
    val isLoaded: StateFlow<Boolean> = repository.isLoaded

    /** Saves a complete draft, including age, medical profile and current photo. */
    suspend fun save(draft: Profile): Boolean = withContext(NonCancellable) {
        val app = getApplication<Application>()
        val normalized = draft.normalized()
        val saved = repository.save(normalized)
        if (saved) {
            ProfileImageStore.cleanup(app, keepFileName = normalized.photoFileName)
        }
        saved
    }

    /**
     * Copies the picked image into internal storage and saves it together with
     * the rest of the draft. Returns the stored profile, or null on failure.
     */
    suspend fun setPhoto(draft: Profile, uri: Uri): Profile? =
        withContext(NonCancellable) {
            val app = getApplication<Application>()
            val fileName = ProfileImageStore.save(app, uri) ?: return@withContext null
            val updated = draft.copy(photoFileName = fileName).normalized()
            val saved = repository.save(updated)
            if (saved) {
                ProfileImageStore.cleanup(app, keepFileName = fileName)
                updated
            } else {
                ProfileImageStore.delete(app, fileName)
                null
            }
        }

    /** Removes the profile picture and saves the rest of the draft. */
    suspend fun removePhoto(draft: Profile): Profile? =
        withContext(NonCancellable) {
            val app = getApplication<Application>()
            val updated = draft.copy(photoFileName = null).normalized()
            val saved = repository.save(updated)
            if (saved) {
                ProfileImageStore.cleanup(app, keepFileName = null)
                updated
            } else {
                null
            }
        }

    /**
     * Copies a picked image for onboarding without saving the profile yet, so
     * an unfinished onboarding never looks like an existing install.
     */
    suspend fun preparePhoto(uri: Uri, previousFileName: String? = null): String? =
        withContext(NonCancellable) {
            val app = getApplication<Application>()
            val fileName = ProfileImageStore.save(app, uri) ?: return@withContext null
            if (previousFileName != null && previousFileName != fileName) {
                ProfileImageStore.delete(app, previousFileName)
            }
            fileName
        }

    suspend fun deletePhoto(fileName: String?) = withContext(NonCancellable) {
        ProfileImageStore.delete(getApplication(), fileName)
    }
}
