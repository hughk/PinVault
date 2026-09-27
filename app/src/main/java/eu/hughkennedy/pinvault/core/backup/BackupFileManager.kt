package eu.hughkennedy.pinvault.core.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.IOException

object BackupFileManager {

    const val DEFAULT_BACKUP_FILENAME = "pinvault-backup.pinvault"

    /**
     * Saves backup JSON directly into the device's public Downloads directory.
     * Uses MediaStore.Downloads on Android 10+ (API 29+) and Environment.DIRECTORY_DOWNLOADS on Android 8-9.
     * Returns the Uri of the saved file and the actual filename written.
     */
    fun saveToDownloads(context: Context, filename: String, content: String): Pair<Uri, String> {
        val cleanFilename = if (filename.endsWith(".pinvault", ignoreCase = true)) {
            filename.trim()
        } else {
            "${filename.trim()}.pinvault"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFilename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            // If a file with this name was previously saved in Downloads by this app, delete it first to cleanly replace
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(cleanFilename, "%${Environment.DIRECTORY_DOWNLOADS}%")
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val existingUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        context.contentResolver.delete(existingUri, null, null)
                    }
                }
            } catch (_: Exception) {
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Could not create entry in Downloads folder")

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IOException("Could not open output stream for Downloads file")

            val actualName = queryFileName(context, uri)
            return Pair(uri, actualName)
        } else {
            // Android 8.0 - 9.0 (API 26-28)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, cleanFilename)
            file.writeText(content, Charsets.UTF_8)
            return Pair(Uri.fromFile(file), file.name)
        }
    }

    /**
     * Writes backup JSON to any user-selected Uri from SAF CreateDocument picker.
     */
    fun writeToUri(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri, "rwt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: throw IOException("Could not open output stream for selected file")
    }

    /**
     * Reads backup JSON text from a Uri.
     */
    fun readFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IOException("Could not read backup file")
    }

    /**
     * Resolves the human-readable display name for any content:// or file:// Uri.
     */
    fun queryFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_BACKUP_FILENAME
    }

    /**
     * Searches Downloads directory for an existing .pinvault or .pinkeeper file.
     * Returns the Uri and filename of the most recent file if accessible, or null.
     */
    fun findDefaultBackupInDownloads(context: Context): Pair<Uri, String>? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.pinvault' OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.pinkeeper'"
                val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        return Pair(uri, name)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() && downloadsDir.isDirectory) {
                    val files = downloadsDir.listFiles { file ->
                        file.name.endsWith(".pinvault", ignoreCase = true) || file.name.endsWith(".pinkeeper", ignoreCase = true)
                    }?.sortedByDescending { it.lastModified() }
                    if (!files.isNullOrEmpty()) {
                        val file = files.first()
                        return Pair(Uri.fromFile(file), file.name)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Creates an OpenDocument contract that suggests the Downloads directory as initial location.
     */
    class OpenBackupDocumentContract : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent {
            val intent = super.createIntent(context, input)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val downloadsUri = Uri.parse("content://com.android.providers.downloads.documents/document/downloads")
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
            }
            return intent
        }
    }

    /**
     * Creates a CreateDocument contract that suggests the Downloads directory as initial location.
     */
    class CreateBackupDocumentContract : ActivityResultContracts.CreateDocument("application/json") {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val downloadsUri = Uri.parse("content://com.android.providers.downloads.documents/document/downloads")
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
            }
            return intent
        }
    }
}
