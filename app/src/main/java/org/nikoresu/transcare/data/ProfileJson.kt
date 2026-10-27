package org.nikoresu.transcare.data

import org.json.JSONObject
import org.nikoresu.transcare.domain.model.MedicalProfile
import org.nikoresu.transcare.domain.model.Profile

/** Serializes the profile to the small JSON document stored in internal storage. */
internal object ProfileJson {

    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHOTO_FILE_NAME = "photoFileName"
    private const val KEY_AGE = "age"
    private const val KEY_MEDICAL_PROFILE = "medicalProfile"

    fun encode(profile: Profile): String {
        val normalized = profile.normalized()
        return JSONObject()
            .put(KEY_NAME, normalized.name)
            .put(KEY_EMAIL, normalized.email)
            .put(KEY_PHOTO_FILE_NAME, normalized.photoFileName ?: JSONObject.NULL)
            .put(KEY_AGE, normalized.age ?: JSONObject.NULL)
            .put(KEY_MEDICAL_PROFILE, normalized.medicalProfile?.name ?: JSONObject.NULL)
            .toString()
    }

    fun decode(text: String): Profile {
        val json = JSONObject(text)
        return Profile(
            name = json.optString(KEY_NAME),
            email = json.optString(KEY_EMAIL),
            photoFileName = json.optString(KEY_PHOTO_FILE_NAME)
                .takeIf { Profile.isValidPhotoFileName(it) },
            age = if (json.has(KEY_AGE) && !json.isNull(KEY_AGE)) {
                json.optInt(KEY_AGE)
            } else {
                null
            },
            medicalProfile = MedicalProfile.fromStorage(
                json.optString(KEY_MEDICAL_PROFILE).takeIf { it.isNotEmpty() },
            ),
        ).normalized()
    }
}
