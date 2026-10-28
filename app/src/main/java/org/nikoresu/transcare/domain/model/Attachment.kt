package org.nikoresu.transcare.domain.model

/**
 * A photo or PDF linked to an appointment, exam result or diary entry.
 *
 * Only the metadata travels with the owner. The file itself is stored
 * encrypted in the app's private attachment vault, keyed by [id].
 */
data class Attachment(
    val id: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val addedAtMillis: Long = 0L,
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == PDF_MIME_TYPE

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val IMAGE_MIME_PREFIX = "image/"

        fun isSupportedMimeType(mimeType: String): Boolean =
            mimeType == PDF_MIME_TYPE || mimeType.startsWith(IMAGE_MIME_PREFIX)
    }
}
