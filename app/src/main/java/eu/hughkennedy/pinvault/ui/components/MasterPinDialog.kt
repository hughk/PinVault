package eu.hughkennedy.pinvault.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.hughkennedy.pinvault.R

@Composable
fun MasterPinDialog(
    isPinAlreadySet: Boolean,
    onVerifyCurrentPin: (String) -> Boolean,
    onSaveNewPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPinAlreadySet) {
                        stringResource(R.string.master_pin_dialog_title_change)
                    } else {
                        stringResource(R.string.master_pin_dialog_title_set)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isPinAlreadySet) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { input ->
                            currentPin = input.filter { it.isDigit() }.take(4)
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.master_pin_current_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { input ->
                        newPin = input.filter { it.isDigit() }.take(4)
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.master_pin_new_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        confirmPin = input.filter { it.isDigit() }.take(4)
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.master_pin_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPinAlreadySet && !onVerifyCurrentPin(currentPin)) {
                        errorMessage = context.getString(R.string.master_pin_error_current)
                        return@Button
                    }
                    if (newPin.length != 4) {
                        errorMessage = context.getString(R.string.master_pin_error_length)
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        errorMessage = context.getString(R.string.master_pin_error_mismatch)
                        return@Button
                    }
                    onSaveNewPin(newPin)
                    Toast.makeText(
                        context,
                        context.getString(R.string.master_pin_updated_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
