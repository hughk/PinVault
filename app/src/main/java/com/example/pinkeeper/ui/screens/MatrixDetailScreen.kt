package com.example.pinkeeper.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pinkeeper.core.engine.DecoyRandomizer
import com.example.pinkeeper.core.model.CardEntity
import com.example.pinkeeper.core.model.PaletteColor
import com.example.pinkeeper.ui.components.MatrixGridView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixDetailScreen(
    card: CardEntity,
    onBack: () -> Unit,
    onEditCard: () -> Unit,
    onBiometricAuthRequested: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTiles by remember(card) { mutableStateOf(card.tiles) }
    var isPeeking by remember { mutableStateOf(false) }
    var isHoldingDown by remember { mutableStateOf(false) }

    val palette = PaletteColor.find(card.secretColor)

    // Failsafe 6-second timeout for peeking mode to prevent accidental exposure
    LaunchedEffect(isPeeking) {
        if (isPeeking) {
            delay(6000)
            isPeeking = false
            isHoldingDown = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Conceal the color unless actively in peeking mode
                        Text(
                            text = if (isPeeking) {
                                "${card.cols}×${card.rows} Matrix • ${palette.displayName}"
                            } else {
                                "${card.cols}×${card.rows} Camouflage Matrix"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                    RuleAndPeekerCard(
                        card = card,
                        palette = palette,
                        isPeeking = isPeeking,
                        onPressStart = {
                            isHoldingDown = true
                            onBiometricAuthRequested {
                                if (isHoldingDown) {
                                    isPeeking = true
                                }
                            }
                        },
                        onPressEnd = {
                            isHoldingDown = false
                            isPeeking = false
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

                    // THE MATRIX GRID (with dynamic two-way scrolling if edges overflow)
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

                    RuleAndPeekerCard(
                        card = card,
                        palette = palette,
                        isPeeking = isPeeking,
                        onPressStart = {
                            isHoldingDown = true
                            onBiometricAuthRequested {
                                if (isHoldingDown) {
                                    isPeeking = true
                                }
                            }
                        },
                        onPressEnd = {
                            isHoldingDown = false
                            isPeeking = false
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
            // Shield icon: Neutral styling when camouflage is active; secret color only when peeking
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPeeking) palette.color else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isPeeking) palette.textColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
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
private fun RuleAndPeekerCard(
    card: CardEntity,
    palette: PaletteColor,
    isPeeking: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
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

                // Badge: Concealed when not peeking; reveals color only during biometric hold
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
                    if (card.ruleHint.isNotBlank()) card.ruleHint else "Tokens are camouflaged. Press and hold below to reveal sequence."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // EMERGENCY PATTERN PEEKER (Touch & Hold with Biometric Reveal)
            val peekerContainerColor by animateColorAsState(
                targetValue = if (isPeeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "peeker_bg"
            )
            val peekerContentColor by animateColorAsState(
                targetValue = if (isPeeking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "peeker_content"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(peekerContainerColor)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPressStart()
                                tryAwaitRelease()
                                onPressEnd()
                            }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (isPeeking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPeeking) "Pattern Revealed • Release to Re-Camouflage" else "Press & Hold for Biometric Reveal",
                        color = peekerContentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
