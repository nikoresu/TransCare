package org.nikoresu.transcare.data

import android.content.Context
import android.util.Log
import java.io.File
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
import org.nikoresu.transcare.domain.model.Profile

/**
 * Local storage for the profile. The data is persisted as JSON inside the
 * app's internal storage, so it survives restarts without a backend.
 */
class ProfileRepository(context: Context) {

    private val appContext = context.applicationContext
    private val profileFile = File(appContext.filesDir, PROFILE_FILE_NAME)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveMutex = Mutex()
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** True once the stored profile has been read from disk. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            _profile.value = loadProfile()
            _isLoaded.value = true
        }
    }

    /** Persists the profile and returns false when the file could not be written. */
    suspend fun save(profile: Profile): Boolean {
        val normalized = profile.normalized()
        return saveMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    LocalDataCipher.writeText(profileFile, ProfileJson.encode(normalized))
                    _profile.value = normalized
                    true
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not save profile", exception)
                    false
                }
            }
        }
    }

    private fun loadProfile(): Profile {
        LocalDataCipher.migrateIfNeeded(profileFile)
        return try {
            val text = LocalDataCipher.readText(profileFile) ?: return Profile()
            val profile = ProfileJson.decode(text)
            if (isPhotoAvailable(profile.photoFileName)) {
                profile
            } else {
                profile.copy(photoFileName = null)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not read profile", exception)
            Profile()
        }
    }

    /** A missing or empty file means the picture cannot be shown, so forget it. */
    private fun isPhotoAvailable(fileName: String?): Boolean {
        if (fileName == null) return true
        val file = ProfileImageStore.fileFor(appContext, fileName) ?: return false
        return file.isFile && file.length() > 0L
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val PROFILE_FILE_NAME = "profile.json"
    }
}
