package org.nikoresu.transcare.data

import org.json.JSONObject
import org.nikoresu.transcare.domain.model.Profile

/** Serializes the profile to the small JSON document stored in internal storage. */
internal object ProfileJson {

    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHOTO_FILE_NAME = "photoFileName"

    fun encode(profile: Profile): String {
        val normalized = profile.normalized()
        return JSONObject()
            .put(KEY_NAME, normalized.name)
            .put(KEY_EMAIL, normalized.email)
            .put(KEY_PHOTO_FILE_NAME, normalized.photoFileName ?: JSONObject.NULL)
            .toString()
    }

    fun decode(text: String): Profile {
        val json = JSONObject(text)
        return Profile(
            name = json.optString(KEY_NAME),
            email = json.optString(KEY_EMAIL),
            photoFileName = json.optString(KEY_PHOTO_FILE_NAME)
                .takeIf { Profile.isValidPhotoFileName(it) },
        ).normalized()
    }
}
