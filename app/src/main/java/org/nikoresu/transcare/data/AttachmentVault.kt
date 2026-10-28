package org.nikoresu.transcare.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.domain.model.Attachment

/**
 * Stores attachment files encrypted inside the app's private data folder.
 *
 * The bytes use the same [LocalDataCipher] frame as the JSON files, so they are
 * protected by the non-exportable Android Keystore key. Only the attachment
 * metadata is written to the owner's JSON file; the file itself lives here
 * under a random id.
 */
object AttachmentVault {

    /** A single attachment is capped so it can be encrypted in memory. */
    const val MAX_ATTACHMENT_BYTES: Long = 20L * 1024 * 1024

    private const val DIR_NAME = "attachments"
    private const val EXTENSION = ".bin"
    private const val PREVIEW_DIR_NAME = "attachment_preview"
    private val ID_PATTERN = Regex("^[A-Za-z0-9-]{8,64}$")

    class TooLargeException : Exception("The attachment is too large")

    class UnsupportedTypeException : Exception("Unsupported attachment type")

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun newId(): String = UUID.randomUUID().toString()

    fun directory(context: Context): File = File(context.filesDir, DIR_NAME)

    fun fileFor(context: Context, id: String): File =
        File(directory(context), "$id$EXTENSION")

    fun exists(context: Context, attachment: Attachment): Boolean =
        isValidId(attachment.id) && fileFor(context, attachment.id).isFile

    /** Copies a picked document into the vault and returns its metadata. */
    suspend fun importFromUri(context: Context, uri: Uri): Attachment =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(context, uri)
            val mimeType = resolveMimeType(resolver.getType(uri), displayName)
            val bytes = resolver.openInputStream(uri)?.use { input ->
                input.readAtMost(MAX_ATTACHMENT_BYTES)
            } ?: throw IOException("Could not open the selected file")

            val id = newId()
            LocalDataCipher.writeBytes(fileFor(context, id), bytes)
            Attachment(
                id = id,
                fileName = displayName.ifBlank { "attachment" },
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                addedAtMillis = System.currentTimeMillis(),
            )
        }

    /** Decrypts an attachment, or returns null when the file is gone. */
    suspend fun readBytes(context: Context, attachment: Attachment): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!isValidId(attachment.id)) return@withContext null
            val file = fileFor(context, attachment.id)
            if (!file.isFile) return@withContext null
            LocalDataCipher.readBytes(file)
        }

    /**
     * Opens an attachment for streaming reads, decrypting on the fly, so a
     * large file can be copied into a backup without holding it in memory.
     * Returns null when the file is gone or the id is invalid.
     */
    fun openReadStream(context: Context, id: String): InputStream? {
        if (!isValidId(id)) return null
        return LocalDataCipher.openInputStream(fileFor(context, id))
    }

    suspend fun delete(context: Context, attachment: Attachment) =
        deleteById(context, attachment.id)

    suspend fun deleteById(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            if (isValidId(id)) fileFor(context, id).delete()
        }
    }

    /** Deletes files that were attached before an edit but removed in it. */
    suspend fun deleteRemoved(
        context: Context,
        previous: List<Attachment>,
        updated: List<Attachment>,
    ) {
        val keptIds = updated.mapTo(HashSet()) { it.id }
        previous.filterNot { it.id in keptIds }.forEach { delete(context, it) }
    }

    suspend fun deleteAll(context: Context, attachments: List<Attachment>) {
        attachments.forEach { delete(context, it) }
    }

    /**
     * Replaces the vault with the files from an imported backup. Files whose
     * metadata was not included in the backup are removed, so a restore does
     * not leave stale documents behind.
     *
     * Staged attachments are encrypted into the vault one stream at a time, so
     * a backup of any size can be restored with a constant amount of memory.
     */
    suspend fun restoreAll(
        context: Context,
        files: Map<String, AttachmentData>,
    ): Int = withContext(Dispatchers.IO) {
        val directory = directory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the attachment folder")
        }

        val restoredIds = mutableSetOf<String>()
        files.forEach { (id, data) ->
            try {
                val size = data.sizeBytes()
                if (!isValidId(id) || size <= 0 || size > MAX_ATTACHMENT_BYTES) {
                    return@forEach
                }
                val target = fileFor(context, id)
                data.openStream().use { input ->
                    LocalDataCipher.writeBytes(target, input)
                }
                restoredIds += id
            } finally {
                data.delete()
            }
        }

        directory.listFiles()?.forEach { file ->
            val name = file.name
            if (!name.endsWith(EXTENSION)) return@forEach
            val id = name.removeSuffix(EXTENSION)
            if (id !in restoredIds) file.delete()
        }
        restoredIds.size
    }

    /**
     * Writes a temporary plain copy in the app cache so an external viewer can
     * open it. The cache is private to the app and old previews are removed
     * before each new one is created.
     */
    suspend fun previewUri(context: Context, attachment: Attachment): Uri? =
        withContext(Dispatchers.IO) {
            val bytes = readBytes(context, attachment) ?: return@withContext null
            val directory = File(context.cacheDir, PREVIEW_DIR_NAME)
            if (directory.exists()) {
                directory.listFiles()?.forEach { it.delete() }
            } else if (!directory.mkdirs()) {
                return@withContext null
            }
            val file = File(directory, previewFileName(attachment))
            file.writeBytes(bytes)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.attachments",
                file,
            )
        }

    private fun previewFileName(attachment: Attachment): String {
        val safeName = attachment.fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { attachment.id }
        val hasExtension = safeName.substringAfterLast('.', "").isNotEmpty()
        return if (hasExtension) {
            "${attachment.id}-$safeName"
        } else {
            "${attachment.id}-$safeName${extensionFor(attachment.mimeType)}"
        }
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        Attachment.PDF_MIME_TYPE -> ".pdf"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/heic", "image/heif" -> ".heic"
        else -> ".jpg"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveMimeType(reported: String?, fileName: String): String {
        val normalized = reported?.trim()?.lowercase(Locale.ROOT)
        if (
            normalized != null &&
            Attachment.isSupportedMimeType(normalized) &&
            normalized != "image/*"
        ) {
            return normalized
        }

        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "pdf" -> Attachment.PDF_MIME_TYPE
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            else -> throw UnsupportedTypeException()
        }
    }

    private fun InputStream.readAtMost(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw TooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
