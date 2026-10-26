package org.nikoresu.transcare.pdf

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.R

/** A PDF could not be imported. [messageRes] is ready to show in the UI. */
sealed class PdfImportException(
    @param:StringRes val messageRes: Int,
    cause: Throwable? = null,
) : Exception(cause) {
    class TooLarge : PdfImportException(R.string.exams_pdf_error_too_large)
    class Encrypted : PdfImportException(R.string.exams_pdf_error_encrypted)
    class NoText : PdfImportException(R.string.exams_pdf_error_no_text)
    class NoResults : PdfImportException(R.string.exams_pdf_error_no_results)
    class Unreadable(cause: Throwable? = null) :
        PdfImportException(R.string.exams_pdf_error_unreadable, cause)
}

/**
 * Reads a PDF chosen through the Storage Access Framework and turns its text
 * into exam results. Only PDFs with a text layer are supported; scanned images
 * are reported as [PdfImportException.NoText].
 */
object PdfExamExtractor {

    private const val MAX_FILE_BYTES = 10 * 1024 * 1024
    private const val MAX_PAGES = 30
    private const val MIN_TEXT_CHARACTERS = 20

    suspend fun extract(context: Context, uri: Uri): ParsedPdfExams = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val resolver = context.contentResolver

        // Reject obviously oversized files before reading them into memory.
        val declaredSize = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (declaredSize > MAX_FILE_BYTES) throw PdfImportException.TooLarge()

        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                readAtMost(input, MAX_FILE_BYTES)
            } ?: throw PdfImportException.Unreadable()
        } catch (exception: PdfImportException) {
            throw exception
        } catch (exception: IOException) {
            throw PdfImportException.Unreadable(exception)
        }
        if (bytes.isEmpty()) throw PdfImportException.Unreadable()

        try {
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                if (document.isEncrypted) throw PdfImportException.Encrypted()
                if (document.numberOfPages > MAX_PAGES) throw PdfImportException.TooLarge()

                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val text = stripper.getText(document)
                if (text.count { !it.isWhitespace() } < MIN_TEXT_CHARACTERS) {
                    throw PdfImportException.NoText()
                }

                val parsed = ExamPdfParser.parse(text)
                if (parsed.results.isEmpty()) throw PdfImportException.NoResults()
                parsed
            }
        } catch (exception: PdfImportException) {
            throw exception
        } catch (exception: InvalidPasswordException) {
            throw PdfImportException.Encrypted()
        } catch (exception: IOException) {
            throw PdfImportException.Unreadable(exception)
        } catch (exception: Exception) {
            throw PdfImportException.Unreadable(exception)
        }
    }

    /** Reads at most [limit] bytes and fails instead of buffering a huge file. */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) throw PdfImportException.TooLarge()
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
