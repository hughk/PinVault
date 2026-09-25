package eu.hughkennedy.pinvault.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.PaletteColor
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
    onBiometricAuthRequested: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTiles by remember(card) { mutableStateOf(card.tiles) }
    var isPeeking by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val palette = PaletteColor.find(card.secretColor)

    // Timed reveal window: 12-second countdown with automatic re-camouflage
    LaunchedEffect(isPeeking) {
        if (isPeeking) {
            countdownSeconds = 12
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            isPeeking = false
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
                Text(text = "Delete PIN Matrix?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to permanently delete \"${card.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteCard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
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
                                        "${card.cols}×${card.rows} • ${palette.displayName}"
                                    } else {
                                        "${card.cols}×${card.rows} Camouflage"
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditCard) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Matrix")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Matrix",
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
                        onRefreshDecoys = {
                            currentTiles = DecoyRandomizer.randomizeDecoys(currentTiles, card.secretColor)
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
                            isPeeking = isPeeking
                        )
                    }

                    RuleAndRevealCard(
                        card = card,
                        palette = palette,
                        isPeeking = isPeeking,
                        countdownSeconds = countdownSeconds,
                        onToggleReveal = {
                            if (isPeeking) {
                                // Tap again to extinguish immediately
                                isPeeking = false
                            } else {
                                // Tap to trigger biometric auth
                                onBiometricAuthRequested {
                                    isPeeking = true
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
                        onRefreshDecoys = {
                            currentTiles = DecoyRandomizer.randomizeDecoys(currentTiles, card.secretColor)
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
                            isPeeking = isPeeking
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RuleAndRevealCard(
                        card = card,
                        palette = palette,
                        isPeeking = isPeeking,
                        countdownSeconds = countdownSeconds,
                        onToggleReveal = {
                            if (isPeeking) {
                                // Tap again to extinguish immediately
                                isPeeking = false
                            } else {
                                // Tap to trigger biometric auth
                                onBiometricAuthRequested {
                                    isPeeking = true
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
                    text = "Steganographic Camouflage",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPeeking) "Pattern Revealed" else "Shoulder-surf protected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Text("Refresh Decoys", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RuleAndRevealCard(
    card: CardEntity,
    palette: PaletteColor,
    isPeeking: Boolean,
    countdownSeconds: Int,
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
                        text = "Secret Decryption Rule:",
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
                            text = "${palette.displayName} Tokens",
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
                            text = "🔒 Encrypted Pattern",
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
                    if (card.ruleHint.isNotBlank()) card.ruleHint else "Read the ${palette.displayName} tokens according to your mental pattern."
                } else {
                    if (card.ruleHint.isNotBlank()) card.ruleHint else "Tokens are camouflaged. Tap below to verify biometric and reveal sequence."
                },
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )

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
                                "Pattern Revealed (${countdownSeconds}s) • Tap to Conceal"
                            } else {
                                "Tap for Biometric Reveal"
                            },
                            color = peekerContentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isPeeking) {
                                "Tap anywhere on this button to extinguish immediately"
                            } else {
                                "12s timed window • Tap again to extinguish"
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
