package eu.hughkennedy.pinvault.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.R

@Composable
fun LockScreen(
    onUnlockSuccess: () -> Unit,
    onBiometricRequested: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    getRemainingLockoutSeconds: () -> Long = { 0L },
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableStateOf(getRemainingLockoutSeconds()) }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            while (lockoutSeconds > 0) {
                kotlinx.coroutines.delay(1000L)
                lockoutSeconds = getRemainingLockoutSeconds()
            }
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4 && lockoutSeconds <= 0) {
            if (onVerifyPin(enteredPin)) {
                onUnlockSuccess()
            } else {
                val remaining = getRemainingLockoutSeconds()
                if (remaining > 0) {
                    lockoutSeconds = remaining
                } else {
                    isError = true
                    kotlinx.coroutines.delay(600)
                    isError = false
                }
                enteredPin = ""
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Shield Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.lock_shield_status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Lock Icon & PIN Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                val subtitleText = when {
                    lockoutSeconds > 0 -> stringResource(R.string.lock_subtitle_locked_out, lockoutSeconds)
                    isError -> stringResource(R.string.lock_subtitle_error)
                    else -> stringResource(R.string.lock_subtitle_normal)
                }
                val subtitleColor = when {
                    lockoutSeconds > 0 || isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                isError || lockoutSeconds > 0 -> MaterialTheme.colorScheme.error
                                isFilled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            label = "dot_color"
                        )
                        val dotScale by animateFloatAsState(
                            targetValue = if (isFilled) 1.25f else 1.0f,
                            label = "dot_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            // Keypad (3x4 grid)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("bio", "0", "del")
                )

                rows.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        rowKeys.forEach { key ->
                            KeypadButton(
                                key = key,
                                enabled = lockoutSeconds <= 0,
                                onClick = {
                                    if (lockoutSeconds <= 0) {
                                        when (key) {
                                            "bio" -> onBiometricRequested()
                                            "del" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isAction = key == "bio" || key == "del"
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isAction && key == "bio" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentAlpha = if (enabled) 1.0f else 0.38f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled) { onClick() }
    ) {
        when (key) {
            "bio" -> {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.lock_action_biometric),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(30.dp)
                )
            }
            "del" -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.lock_action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                Text(
                    text = key,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
            }
        }
    }
}
