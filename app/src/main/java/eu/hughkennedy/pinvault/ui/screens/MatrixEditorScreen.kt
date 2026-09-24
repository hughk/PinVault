package eu.hughkennedy.pinvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import eu.hughkennedy.pinvault.ui.components.ColorPickerRow
import eu.hughkennedy.pinvault.ui.components.MatrixGridView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixEditorScreen(
    initialCard: CardEntity?,
    existingFolders: List<String> = emptyList(),
    onSaveCard: (CardEntity) -> Unit,
    onDeleteCard: ((String) -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNew = initialCard == null

    var name by remember { mutableStateOf(initialCard?.name ?: "") }
    var category by remember { mutableStateOf(initialCard?.category ?: CardCategory.CREDIT) }
    var folder by remember { mutableStateOf(initialCard?.folder ?: "") }
    var cols by remember { mutableStateOf(initialCard?.cols ?: 6) }
    var rows by remember { mutableStateOf(initialCard?.rows ?: 7) }
    var secretColor by remember { mutableStateOf(initialCard?.secretColor ?: "blue") }
    var ruleHint by remember { mutableStateOf(initialCard?.ruleHint ?: "") }

    var tiles by remember {
        mutableStateOf(
            initialCard?.tiles ?: DecoyRandomizer.createBlankMatrix(cols, rows, secretColor)
        )
    }

    var selectedTileForEdit by remember { mutableStateOf<TileData?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var gridSizeDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog && initialCard != null && onDeleteCard != null) {
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
            title = { Text("Delete PIN Matrix?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${name.ifBlank { "this matrix" }}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteCard(initialCard.id)
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
                    Text(
                        text = if (isNew) "Create PIN Matrix" else "Edit PIN Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isBlank()) return@IconButton
                            // Auto-randomize any remaining '?' tiles
                            val finalizedTiles = DecoyRandomizer.randomizeDecoys(tiles, secretColor)
                            val updatedCard = (initialCard ?: CardEntity(name = name)).copy(
                                name = name.trim(),
                                category = category,
                                folder = folder.trim(),
                                cols = cols,
                                rows = rows,
                                secretColor = secretColor,
                                ruleHint = ruleHint.trim(),
                                tiles = finalizedTiles,
                                lastModified = System.currentTimeMillis()
                            )
                            onSaveCard(updatedCard)
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Metadata Inputs
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Card / Account Name") },
                        placeholder = { Text("e.g. Barclays Visa Debit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category Dropdown
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = category.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                CardCategory.values().forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.displayName) },
                                        onClick = {
                                            category = cat
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Grid Dimensions Dropdown
                        ExposedDropdownMenuBox(
                            expanded = gridSizeDropdownExpanded,
                            onExpandedChange = { gridSizeDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${cols}×${rows}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Grid Size") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gridSizeDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = gridSizeDropdownExpanded,
                                onDismissRequest = { gridSizeDropdownExpanded = false }
                            ) {
                                listOf(Pair(5, 6), Pair(6, 7), Pair(7, 8)).forEach { (c, r) ->
                                    DropdownMenuItem(
                                        text = { Text("${c}×${r} (${c * r} tiles)") },
                                        onClick = {
                                            cols = c
                                            rows = r
                                            tiles = DecoyRandomizer.createBlankMatrix(c, r, secretColor)
                                            gridSizeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Folder / Group Input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Folder / Group (Optional)") },
                            placeholder = { Text("e.g. Personal, Work, Banking, Travel") },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick folder suggestion chips
                        val suggested = existingFolders.filter { it.isNotBlank() && !it.equals(folder, ignoreCase = true) }
                        if (suggested.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Suggestions:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                suggested.take(6).forEach { f ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { folder = f },
                                        label = { Text(f, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Secret Color Picker
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Secret Token Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your actual PIN digits will be camouflaged using this secret color.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ColorPickerRow(
                        selectedColorId = secretColor,
                        onColorSelected = { newColor ->
                            secretColor = newColor
                            tiles = tiles.map { tile ->
                                if (tile.isPinTile) tile.copy(colorId = newColor) else tile
                            }
                        }
                    )
                }
            }

            // Interactive Matrix Designer
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PIN Placement Grid",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap tiles to place your real PIN digits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                tiles = DecoyRandomizer.randomizeDecoys(tiles, secretColor)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Randomize '?' Decoys", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Matrix View inside Editor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridView(
                            cols = cols,
                            rows = rows,
                            tiles = tiles,
                            onTileClick = { clickedTile ->
                                selectedTileForEdit = clickedTile
                            }
                        )
                    }

                    Text(
                        text = "Unassigned '?' tiles will be filled with decoy numbers and non-adjacent colors automatically upon save.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Secret Rule Note
            OutlinedTextField(
                value = ruleHint,
                onValueChange = { ruleHint = it },
                label = { Text("Secret Rule Hint (Stored Encrypted)") },
                placeholder = { Text("e.g. Read blue tiles diagonally starting from top-left") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Delete Card Button (if editing existing card)
            if (!isNew && onDeleteCard != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Card Matrix")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal to set digit on a specific tile
    if (selectedTileForEdit != null) {
        val targetTile = selectedTileForEdit!!
        AlertDialog(
            onDismissRequest = { selectedTileForEdit = null },
            title = {
                Text(
                    text = "Set Tile (Row ${targetTile.row + 1}, Col ${targetTile.col + 1})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Assign a PIN digit in ${PaletteColor.find(secretColor).displayName}:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // 0-9 Keypad
                    val numRows = listOf(
                        listOf("0", "1", "2", "3", "4"),
                        listOf("5", "6", "7", "8", "9")
                    )

                    numRows.forEach { rowDigits ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowDigits.forEach { digit ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            tiles = tiles.map {
                                                if (it.row == targetTile.row && it.col == targetTile.col) {
                                                    it.copy(digit = digit, colorId = secretColor, isPinTile = true)
                                                } else {
                                                    it
                                                }
                                            }
                                            selectedTileForEdit = null
                                        }
                                ) {
                                    Text(
                                        text = digit,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            tiles = tiles.map {
                                if (it.row == targetTile.row && it.col == targetTile.col) {
                                    it.copy(digit = "?", colorId = secretColor, isPinTile = false)
                                } else {
                                    it
                                }
                            }
                            selectedTileForEdit = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to '?' Placeholder", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedTileForEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
