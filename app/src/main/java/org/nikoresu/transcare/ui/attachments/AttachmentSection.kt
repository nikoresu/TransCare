package org.nikoresu.transcare.ui.attachments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nikoresu.transcare.R
import org.nikoresu.transcare.data.AttachmentVault
import org.nikoresu.transcare.data.toAttachmentOrNull
import org.nikoresu.transcare.data.toJsonObject
import org.nikoresu.transcare.domain.model.Attachment

/** Keeps attachment metadata across configuration changes in the editors. */
internal val AttachmentListSaver: Saver<List<Attachment>, Any> = listSaver(
    save = { attachments -> attachments.map { it.toJsonObject().toString() } },
    restore = { saved ->
        saved.mapNotNull { json ->
            runCatching { JSONObject(json).toAttachmentOrNull() }.getOrNull()
        }
    },
)

/**
 * Editor section for photos and PDFs. Files are copied into the encrypted
 * vault as soon as they are picked; the editor only receives the metadata.
 */
@Composable
fun AttachmentSection(
    attachments: List<Attachment>,
    onAdd: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val tooLargeMessage = stringResource(R.string.attachments_too_large)
    val unsupportedMessage = stringResource(R.string.attachments_unsupported)
    val importFailedMessage = stringResource(R.string.attachments_import_failed)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        errorMessage = null
        scope.launch {
            try {
                val attachment = AttachmentVault.importFromUri(context, uri)
                onAdd(attachment)
            } catch (_: AttachmentVault.TooLargeException) {
                errorMessage = tooLargeMessage
            } catch (_: AttachmentVault.UnsupportedTypeException) {
                errorMessage = unsupportedMessage
            } catch (_: Exception) {
                errorMessage = importFailedMessage
            } finally {
                isImporting = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.attachments_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    launcher.launch(arrayOf("image/*", Attachment.PDF_MIME_TYPE))
                },
                enabled = enabled && !isImporting,
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.attachments_add))
                }
            }
        }

        if (attachments.isEmpty()) {
            Text(
                text = stringResource(R.string.attachments_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    enabled = enabled,
                    onRemove = { onRemove(attachment) },
                )
            }
        }

        Text(
            text = stringResource(R.string.attachments_storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openFailedMessage = stringResource(R.string.attachments_open_failed)
    val openTitle = stringResource(R.string.attachments_open_title)
    val exists = remember(attachment.id) {
        AttachmentVault.exists(context, attachment)
    }
    var errorMessage by remember(attachment.id) { mutableStateOf<String?>(null) }

    fun open() {
        if (!exists) return
        scope.launch {
            val uri = AttachmentVault.previewUri(context, attachment)
            if (uri == null) {
                errorMessage = openFailedMessage
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        openTitle,
                    ),
                )
            } catch (_: ActivityNotFoundException) {
                errorMessage = openFailedMessage
            } catch (_: Exception) {
                errorMessage = openFailedMessage
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled && exists) { open() }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentPreview(attachment = attachment)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (exists) {
                        formatAttachmentSize(attachment.sizeBytes)
                    } else {
                        stringResource(R.string.attachments_missing)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exists) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            TextButton(onClick = onRemove, enabled = enabled) {
                Text(text = stringResource(R.string.attachments_remove))
            }
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = attachment.id,
    ) {
        if (!attachment.isImage) return@produceState
        value = withContext(Dispatchers.IO) {
            val bytes = AttachmentVault.readBytes(context, attachment)
                ?: return@withContext null
            decodeThumbnail(bytes)
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = when {
                    attachment.isPdf -> "PDF"
                    attachment.isImage -> "IMG"
                    else -> "FILE"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small "2 files" label for list cards. */
@Composable
fun AttachmentCountLabel(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Text(
        text = pluralStringResource(R.plurals.attachment_count_short, count, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

internal fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L ->
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L ->
        String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun decodeThumbnail(bytes: ByteArray): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > 256 ||
            bounds.outHeight / sampleSize > 256
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?.asImageBitmap()
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}
