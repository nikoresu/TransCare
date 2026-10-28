package org.nikoresu.transcare.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.domain.model.Profile

/**
 * Stores the profile picture as a compressed JPEG inside internal storage.
 * No storage permission is needed because the source image arrives through the
 * system photo picker and the copy is private to the app.
 */
object ProfileImageStore {

    private const val TAG = "ProfileImageStore"
    private const val DIRECTORY_NAME = "profile"
    private const val MAX_EDGE_PX = 512
    private const val JPEG_QUALITY = 90

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)

    fun fileFor(context: Context, fileName: String?): File? {
        val safeName = fileName?.takeIf { Profile.isValidPhotoFileName(it) } ?: return null
        return File(directory(context), safeName)
    }

    /** Decodes the picked image, downsizes it, and saves a JPEG copy. */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val width = info.size.width
                val height = info.size.height
                val longestEdge = maxOf(width, height)
                if (longestEdge > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longestEdge
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }

            val targetDirectory = directory(context)
            if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                throw IOException("Could not create the profile picture folder")
            }
            val fileName = "profile_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID().toString().take(8)}.jpg"
            val target = File(targetDirectory, fileName)
            try {
                val jpeg = ByteArrayOutputStream().use { output ->
                    val compressed = bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        output,
                    )
                    if (!compressed) throw IOException("Could not encode the profile picture")
                    output.toByteArray()
                }
                LocalDataCipher.writeBytes(target, jpeg)
            } catch (exception: Exception) {
                target.delete()
                throw exception
            } finally {
                bitmap.recycle()
            }

            fileName
        } catch (exception: Exception) {
            Log.e(TAG, "Could not save profile picture", exception)
            null
        }
    }

    /** Loads the saved JPEG for display; call from a background dispatcher. */
    fun loadBitmap(context: Context, fileName: String?): Bitmap? {
        val file = fileFor(context, fileName) ?: return null
        if (!file.exists()) return null
        return try {
            LocalDataCipher.migrateIfNeeded(file)
            val bytes = LocalDataCipher.readBytes(file) ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (exception: Exception) {
            Log.e(TAG, "Could not load profile picture", exception)
            null
        }
    }

    fun delete(context: Context, fileName: String?) {
        val file = fileFor(context, fileName) ?: return
        try {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Could not delete profile picture")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not delete profile picture", exception)
        }
    }

    /** Removes replaced and orphaned pictures, keeping only [keepFileName]. */
    fun cleanup(context: Context, keepFileName: String?) {
        val files = directory(context).listFiles() ?: return
        files.forEach { file ->
            if (file.isFile && file.name != keepFileName) {
                try {
                    if (!file.delete()) Log.w(TAG, "Could not delete old profile picture")
                } catch (exception: Exception) {
                    Log.e(TAG, "Could not delete old profile picture", exception)
                }
            }
        }
    }
}
