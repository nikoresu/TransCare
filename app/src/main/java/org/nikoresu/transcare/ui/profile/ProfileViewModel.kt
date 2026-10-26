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

    /** Saves the name and email while keeping the current photo. */
    suspend fun save(name: String, email: String): Boolean = withContext(NonCancellable) {
        val current = profile.value
        repository.save(
            current.copy(name = name, email = email),
        )
    }

    /**
     * Copies the picked image into internal storage and saves it together with
     * the current draft name and email.
     */
    suspend fun setPhoto(name: String, email: String, uri: Uri): Boolean =
        withContext(NonCancellable) {
            val app = getApplication<Application>()
            val fileName = ProfileImageStore.save(app, uri) ?: return@withContext false
            val saved = repository.save(
                Profile(name = name, email = email, photoFileName = fileName),
            )
            if (saved) {
                ProfileImageStore.cleanup(app, keepFileName = fileName)
            } else {
                ProfileImageStore.delete(app, fileName)
            }
            saved
        }

    /** Removes the profile picture and saves the current draft name and email. */
    suspend fun removePhoto(name: String, email: String): Boolean =
        withContext(NonCancellable) {
            val app = getApplication<Application>()
            val saved = repository.save(
                Profile(name = name, email = email, photoFileName = null),
            )
            if (saved) ProfileImageStore.cleanup(app, keepFileName = null)
            saved
        }
}
