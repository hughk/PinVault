package eu.hughkennedy.pinvault.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.R
import eu.hughkennedy.pinvault.core.engine.ArtistPathManager
import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.security.BiometricKeyManager
import eu.hughkennedy.pinvault.core.totp.TotpManager
import eu.hughkennedy.pinvault.ui.components.CategoryIconBadge
import eu.hughkennedy.pinvault.ui.components.MatrixGridView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixDetailScreen(
    card: CardEntity,
    onBack: () -> Unit,
    onEditCard: () -> Unit,
    onDeleteCard: () -> Unit,
    onBiometricRevealRequested: (String, (BiometricKeyManager.DecryptedCardSecret) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var currentTiles by remember(card) { mutableStateOf(card.tiles) }
    val encryptedCardSecret = remember(card.id, card.secretColor, card.ruleHint, card.totpSecret) {
        BiometricKeyManager.encryptCardSecret(card.secretColor, card.ruleHint, card.totpSecret)
    }
    var revealedCardSecret by remember { mutableStateOf<BiometricKeyManager.DecryptedCardSecret?>(null) }
    val isPeeking = revealedCardSecret != null
    val activeSecretColor = revealedCardSecret?.secretColor ?: card.secretColor
    val activeRuleHint = revealedCardSecret?.ruleHint ?: card.ruleHint
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var totpRemainingSeconds by remember { mutableIntStateOf(0) }
    var currentTotpCode by remember { mutableStateOf("") }

    val palette = PaletteColor.find(activeSecretColor)

    // Dynamic TOTP ticker: updates rolling PIN code and matrix cells along secret path
    LaunchedEffect(card.isTotp, card.totpSecret, card.totpPeriod, card.totpDigits, card.totpAlgorithm, card.pinPath) {
        if (card.isTotp && !card.totpSecret.isNullOrBlank()) {
            while (true) {
                val now = System.currentTimeMillis()
                totpRemainingSeconds = TotpManager.getRemainingSeconds(now, card.totpPeriod)
                val code = TotpManager.generateCode(
                    secret = card.totpSecret,
                    timeMillis = now,
                    periodSeconds = card.totpPeriod,
                    digits = card.totpDigits,
                    algorithm = card.totpAlgorithm
                )
                if (currentTotpCode != code) {
                    currentTotpCode = code
                    if (card.pinPath.isNotEmpty()) {
                        currentTiles = ArtistPathManager.mapDigitsToPath(
                            paintedPath = card.pinPath,
                            digitString = code,
                            secretColor = card.secretColor,
                            existingTiles = currentTiles
                        )
                    } else {
                        // Fallback for non-painted secret cells
                        val secretIndices = currentTiles.indices.filter { currentTiles[it].isPinTile }
                        if (secretIndices.isNotEmpty()) {
                            val mutable = currentTiles.toMutableList()
                            for (i in 0 until minOf(code.length, secretIndices.size)) {
                                val idx = secretIndices[i]
                                mutable[idx] = mutable[idx].copy(
                                    digit = code[i].toString(),
                                    colorId = card.secretColor,
                                    isPinTile = true
                                )
                            }
                            currentTiles = mutable
                        }
                    }
                }
                delay(1000L)
            }
        }
    }

    // Timed reveal window: 12-second countdown with automatic re-camouflage
    LaunchedEffect(isPeeking) {
        if (isPeeking) {
            countdownSeconds = 12
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            revealedCardSecret = null
        } else {
            countdownSeconds = 0
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(text = stringResource(R.string.delete_card_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(stringResource(R.string.delete_card_message, card.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteCard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIconBadge(
                            category = card.category,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isPeeking) {
                                        "${card.cols}×${card.rows} • ${stringResource(palette.nameRes)}"
                                    } else {
                                        stringResource(R.string.detail_camouflage_suffix, card.cols, card.rows)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (card.folder.isNotBlank()) {
                                    Text(
                                        text = "• 📁 ${card.folder}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEditCard) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit_matrix))
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete_matrix),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isHeightConstrained = maxHeight < 640.dp

            if (isHeightConstrained) {
                // Scrollable layout for smaller screens or landscape orientation
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopSecurityBar(
                        isPeeking = isPeeking,
                        palette = palette,
                        isTotp = card.isTotp,
                        totpRemainingSeconds = totpRemainingSeconds,
                        onRefreshDecoys = {
                            currentTiles = DecoyRandomizer.randomizeDecoys(
                                currentTiles,
                                card.secretColor,
                                decoyLength = if (card.isTotp || card.pinPath.size >= 6) 6 else null
                            )
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp, max = 460.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridView(
                            cols = card.cols,
                            rows = card.rows,
                            tiles = currentTiles,
                            isPeeking = isPeeking,
                            secretColorId = activeSecretColor
                        )
                    }

                    RuleAndRevealCard(
                        card = card.copy(ruleHint = activeRuleHint, secretColor = activeSecretColor),
                        palette = palette,
                        isPeeking = isPeeking,
                        countdownSeconds = countdownSeconds,
                        isTotp = card.isTotp,
                        totpRemainingSeconds = totpRemainingSeconds,
                        currentTotpCode = currentTotpCode,
                        onCopyTotpCode = {
                            clipboardManager.setText(AnnotatedString(currentTotpCode))
                            Toast.makeText(context, context.getString(R.string.totp_code_copied), Toast.LENGTH_SHORT).show()
                        },
                        onToggleReveal = {
                            if (isPeeking) {
                                // Tap again to extinguish immediately
                                revealedCardSecret = null
                            } else {
                                // Tap to trigger biometric auth
                                onBiometricRevealRequested(encryptedCardSecret) { decryptedSecret ->
                                    revealedCardSecret = decryptedSecret
                                }
                            }
                        }
                    )
                }
            } else {
                // Adaptive layout for standard phone viewports
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopSecurityBar(
                        isPeeking = isPeeking,
                        palette = palette,
                        isTotp = card.isTotp,
                        totpRemainingSeconds = totpRemainingSeconds,
                        onRefreshDecoys = {
                            currentTiles = DecoyRandomizer.randomizeDecoys(
                                currentTiles,
                                card.secretColor,
                                decoyLength = if (card.isTotp || card.pinPath.size >= 6) 6 else null
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // THE MATRIX GRID
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridView(
                            cols = card.cols,
                            rows = card.rows,
                            tiles = currentTiles,
                            isPeeking = isPeeking,
                            secretColorId = activeSecretColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RuleAndRevealCard(
                        card = card.copy(ruleHint = activeRuleHint, secretColor = activeSecretColor),
                        palette = palette,
                        isPeeking = isPeeking,
                        countdownSeconds = countdownSeconds,
                        isTotp = card.isTotp,
                        totpRemainingSeconds = totpRemainingSeconds,
                        currentTotpCode = currentTotpCode,
                        onCopyTotpCode = {
                            clipboardManager.setText(AnnotatedString(currentTotpCode))
                            Toast.makeText(context, context.getString(R.string.totp_code_copied), Toast.LENGTH_SHORT).show()
                        },
                        onToggleReveal = {
                            if (isPeeking) {
                                // Tap again to extinguish immediately
                                revealedCardSecret = null
                            } else {
                                // Tap to trigger biometric auth
                                onBiometricRevealRequested(encryptedCardSecret) { decryptedSecret ->
                                    revealedCardSecret = decryptedSecret
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopSecurityBar(
    isPeeking: Boolean,
    palette: PaletteColor,
    isTotp: Boolean = false,
    totpRemainingSeconds: Int = 0,
    onRefreshDecoys: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPeeking) palette.color else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isPeeking) palette.textColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isTotp) {
                        stringResource(R.string.totp_setup_title)
                    } else {
                        stringResource(R.string.detail_steganographic_camouflage)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPeeking) {
                        stringResource(R.string.detail_pattern_revealed)
                    } else if (isTotp) {
                        stringResource(R.string.totp_countdown_label, totpRemainingSeconds)
                    } else {
                        stringResource(R.string.detail_shoulder_surf_protected)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isTotp) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (totpRemainingSeconds <= 5) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            }
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (totpRemainingSeconds <= 5) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${totpRemainingSeconds}s",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totpRemainingSeconds <= 5) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onRefreshDecoys,
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_refresh_decoys), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RuleAndRevealCard(
    card: CardEntity,
    palette: PaletteColor,
    isPeeking: Boolean,
    countdownSeconds: Int,
    isTotp: Boolean = false,
    totpRemainingSeconds: Int = 0,
    currentTotpCode: String = "",
    onCopyTotpCode: (() -> Unit)? = null,
    onToggleReveal: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTotp) {
                            stringResource(R.string.totp_setup_title)
                        } else {
                            stringResource(R.string.detail_secret_rule_title)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge: Concealed when not peeking; reveals color only during reveal window
                if (isPeeking) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.color)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isTotp) {
                                stringResource(R.string.totp_active_badge, totpRemainingSeconds)
                            } else {
                                stringResource(R.string.detail_tokens_badge, stringResource(palette.nameRes))
                            },
                            color = palette.textColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isTotp) {
                                stringResource(R.string.totp_active_badge, totpRemainingSeconds)
                            } else {
                                stringResource(R.string.detail_encrypted_badge)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Description: Does NOT interpolate the color name unless isPeeking is true
            Text(
                text = if (isPeeking) {
                    if (card.ruleHint.isNotBlank()) {
                        card.ruleHint
                    } else if (isTotp) {
                        stringResource(R.string.totp_setup_desc)
                    } else {
                        stringResource(R.string.detail_rule_default_peeking, stringResource(palette.nameRes))
                    }
                } else {
                    if (card.ruleHint.isNotBlank()) {
                        card.ruleHint
                    } else if (isTotp) {
                        stringResource(R.string.totp_setup_desc)
                    } else {
                        stringResource(R.string.detail_rule_default_concealed)
                    }
                },
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )

            // When peeking a TOTP card, show the clear-text rolling code + quick copy button
            if (isPeeking && isTotp && currentTotpCode.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.totp_countdown_label, totpRemainingSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = currentTotpCode.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (onCopyTotpCode != null) {
                        OutlinedButton(
                            onClick = onCopyTotpCode,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.totp_copy_code), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // TIMED BIOMETRIC REVEAL BUTTON (Tap to Reveal + Tap to Extinguish)
            val peekerContainerColor by animateColorAsState(
                targetValue = if (isPeeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "peeker_bg"
            )
            val peekerContentColor by animateColorAsState(
                targetValue = if (isPeeking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                label = "peeker_content"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(peekerContainerColor)
                    .border(
                        width = 1.5.dp,
                        color = if (isPeeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggleReveal() }
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPeeking) Icons.Default.VisibilityOff else Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = peekerContentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPeeking) {
                                stringResource(R.string.reveal_btn_peeking_title, countdownSeconds)
                            } else {
                                stringResource(R.string.reveal_btn_concealed_title)
                            },
                            color = peekerContentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isPeeking) {
                                stringResource(R.string.reveal_btn_peeking_subtitle)
                            } else {
                                stringResource(R.string.reveal_btn_concealed_subtitle)
                            },
                            color = if (isPeeking) peekerContentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
