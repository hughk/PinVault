package eu.hughkennedy.pinvault.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.R
import eu.hughkennedy.pinvault.core.backup.BackupFileManager
import eu.hughkennedy.pinvault.core.backup.BackupMigrationManager
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.security.ClipboardSecurityHelper

@Composable
fun BackupRestoreDialog(
    cards: List<CardEntity>,
    onDismiss: () -> Unit,
    onImportSuccess: (List<CardEntity>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Export, 1: Import

    var exportPassphrase by remember { mutableStateOf("") }
    var exportFilename by remember { mutableStateOf(BackupFileManager.DEFAULT_BACKUP_FILENAME) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    var importPassphrase by remember { mutableStateOf("") }
    var importPayloadText by remember { mutableStateOf("") }
    var isReplaceMode by remember { mutableStateOf(false) } // false: merge, true: replace
    var isTextMode by remember { mutableStateOf(false) } // false: file from Downloads, true: raw text paste
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImportFileName by remember { mutableStateOf<String?>(null) }
    var isAutoDetectedFromDownloads by remember { mutableStateOf(false) }

    // SAF Document Launchers with Downloads directory hint
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = BackupFileManager.CreateBackupDocumentContract()
    ) { uri: Uri? ->
        if (uri != null && pendingExportJson != null) {
            try {
                BackupFileManager.writeToUri(context, uri, pendingExportJson!!)
                val actualName = BackupFileManager.queryFileName(context, uri)
                Toast.makeText(context, context.getString(R.string.backup_saved_to_downloads_toast, actualName), Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.backup_error_export_failed, e.localizedMessage ?: "")
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = BackupFileManager.OpenBackupDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImportUri = uri
            selectedImportFileName = BackupFileManager.queryFileName(context, uri)
            isAutoDetectedFromDownloads = false
            errorMessage = null
        }
    }

    // Auto-detect recent/default backup in Downloads upon opening dialog
    LaunchedEffect(Unit) {
        if (selectedImportUri == null) {
            val detected = BackupFileManager.findDefaultBackupInDownloads(context)
            if (detected != null) {
                selectedImportUri = detected.first
                selectedImportFileName = detected.second
                isAutoDetectedFromDownloads = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.backup_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null },
                        text = { Text(stringResource(R.string.backup_tab_export), fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null },
                        text = { Text(stringResource(R.string.backup_tab_import), fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // EXPORT TAB: Default destination is Downloads folder
                    Text(
                        text = stringResource(R.string.backup_export_desc, cards.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = exportFilename,
                        onValueChange = { exportFilename = it; errorMessage = null },
                        label = { Text(stringResource(R.string.backup_field_filename)) },
                        placeholder = { Text(stringResource(R.string.backup_field_filename_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it; errorMessage = null },
                        label = { Text(stringResource(R.string.backup_field_passphrase)) },
                        placeholder = { Text(stringResource(R.string.backup_field_passphrase_placeholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Button: Directly saves to Downloads folder
                    Button(
                        onClick = {
                            if (exportPassphrase.isBlank()) {
                                errorMessage = context.getString(R.string.backup_error_empty_passphrase)
                                return@Button
                            }
                            if (exportPassphrase.length < 8) {
                                errorMessage = context.getString(R.string.backup_error_passphrase_too_short)
                                return@Button
                            }
                            try {
                                val backupJson = BackupMigrationManager.exportVault(cards, exportPassphrase)
                                val (_, savedName) = BackupFileManager.saveToDownloads(context, exportFilename, backupJson)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.backup_saved_to_downloads_toast, savedName),
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            } catch (e: Exception) {
                                errorMessage = context.getString(R.string.backup_error_export_failed, e.localizedMessage ?: "")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_action_save_downloads), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Actions: Custom folder via SAF picker & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (exportPassphrase.isBlank()) {
                                    errorMessage = context.getString(R.string.backup_error_empty_passphrase)
                                    return@OutlinedButton
                                }
                                if (exportPassphrase.length < 8) {
                                    errorMessage = context.getString(R.string.backup_error_passphrase_too_short)
                                    return@OutlinedButton
                                }
                                try {
                                    val backupJson = BackupMigrationManager.exportVault(cards, exportPassphrase)
                                    pendingExportJson = backupJson
                                    val cleanName = if (exportFilename.endsWith(".pinvault", ignoreCase = true)) {
                                        exportFilename.trim()
                                    } else {
                                        "${exportFilename.trim()}.pinvault"
                                    }
                                    createDocLauncher.launch(cleanName)
                                } catch (e: Exception) {
                                    errorMessage = context.getString(R.string.backup_error_export_failed, e.localizedMessage ?: "")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.backup_action_save_as), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (exportPassphrase.isBlank()) {
                                    errorMessage = context.getString(R.string.backup_error_empty_passphrase)
                                    return@OutlinedButton
                                }
                                if (exportPassphrase.length < 8) {
                                    errorMessage = context.getString(R.string.backup_error_passphrase_too_short)
                                    return@OutlinedButton
                                }
                                try {
                                    val backupJson = BackupMigrationManager.exportVault(cards, exportPassphrase)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, backupJson)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.backup_share_chooser_title))
                                    context.startActivity(shareIntent)
                                    onDismiss()
                                } catch (e: Exception) {
                                    errorMessage = context.getString(R.string.backup_error_export_failed, e.localizedMessage ?: "")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                if (exportPassphrase.isBlank()) {
                                    errorMessage = context.getString(R.string.backup_error_empty_passphrase_short)
                                    return@OutlinedButton
                                }
                                if (exportPassphrase.length < 8) {
                                    errorMessage = context.getString(R.string.backup_error_passphrase_too_short)
                                    return@OutlinedButton
                                }
                                try {
                                    val backupJson = BackupMigrationManager.exportVault(cards, exportPassphrase)
                                    ClipboardSecurityHelper.copySensitiveText(
                                        context = context,
                                        label = "Pin Vault Backup",
                                        text = backupJson,
                                        autoClearSeconds = 60L,
                                        scope = coroutineScope
                                    )
                                    Toast.makeText(context, context.getString(R.string.backup_copied_toast), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } catch (e: Exception) {
                                    errorMessage = context.getString(R.string.backup_error_copy_failed, e.localizedMessage ?: "")
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    // IMPORT TAB: Reads from Downloads folder by default
                    Text(
                        text = stringResource(R.string.backup_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isTextMode) {
                        // File Selection UI (Defaults to Downloads)
                        if (selectedImportUri != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = selectedImportFileName ?: BackupFileManager.DEFAULT_BACKUP_FILENAME,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = if (isAutoDetectedFromDownloads) {
                                                    stringResource(R.string.backup_file_detected_downloads)
                                                } else {
                                                    stringResource(R.string.backup_file_selected)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isAutoDetectedFromDownloads) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.backup_action_change_file), fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.backup_action_select_file))
                            }
                        }
                    } else {
                        // Fallback Text Mode: Paste Raw Payload
                        OutlinedTextField(
                            value = importPayloadText,
                            onValueChange = { importPayloadText = it; errorMessage = null },
                            label = { Text(stringResource(R.string.backup_field_payload)) },
                            placeholder = { Text(stringResource(R.string.backup_field_payload_placeholder)) },
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it; errorMessage = null },
                        label = { Text(stringResource(R.string.backup_field_passphrase)) },
                        placeholder = { Text(stringResource(R.string.backup_field_passphrase_placeholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(stringResource(R.string.backup_mode_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = !isReplaceMode,
                            onClick = { isReplaceMode = false }
                        )
                        Text(stringResource(R.string.backup_mode_merge), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = isReplaceMode,
                            onClick = { isReplaceMode = true }
                        )
                        Text(stringResource(R.string.backup_mode_replace), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!isTextMode && selectedImportUri == null) {
                                errorMessage = context.getString(R.string.backup_error_no_file_selected)
                                return@Button
                            }
                            if (isTextMode && importPayloadText.isBlank()) {
                                errorMessage = context.getString(R.string.backup_error_empty_payload)
                                return@Button
                            }
                            if (importPassphrase.isBlank()) {
                                errorMessage = context.getString(R.string.backup_error_empty_import_passphrase)
                                return@Button
                            }
                            try {
                                val payload = if (isTextMode) {
                                    importPayloadText.trim()
                                } else {
                                    BackupFileManager.readFromUri(context, selectedImportUri!!)
                                }
                                val importedCards = BackupMigrationManager.importVault(payload, importPassphrase)
                                onImportSuccess(importedCards, isReplaceMode)
                                Toast.makeText(context, context.getString(R.string.backup_restore_success_toast, importedCards.size), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: Exception) {
                                errorMessage = context.getString(R.string.backup_error_decrypt_failed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.backup_action_restore))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { isTextMode = !isTextMode },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (isTextMode) {
                                stringResource(R.string.backup_toggle_use_file)
                            } else {
                                stringResource(R.string.backup_toggle_paste_text)
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
