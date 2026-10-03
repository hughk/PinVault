package eu.hughkennedy.pinvault.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.R
import eu.hughkennedy.pinvault.core.engine.ArtistPathManager
import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.engine.PaintedCell
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import eu.hughkennedy.pinvault.core.totp.TotpManager
import eu.hughkennedy.pinvault.core.totp.TotpUriData
import eu.hughkennedy.pinvault.ui.components.ColorPickerRow
import eu.hughkennedy.pinvault.ui.components.MatrixGridView
import eu.hughkennedy.pinvault.ui.components.QrScannerDialog
import kotlinx.coroutines.delay

enum class MatrixDesignMode {
    ARTIST,
    MANUAL
}

/** Action to run once the user has confirmed filling unset PIN digits with random ones. */
enum class PendingFillAction {
    SAVE,
    RANDOMIZE
}

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
    val context = LocalContext.current
    val isNew = initialCard == null

    var name by remember { mutableStateOf(initialCard?.name ?: "") }
    var category by remember { mutableStateOf(initialCard?.category ?: CardCategory.CREDIT) }
    var folder by remember { mutableStateOf(initialCard?.folder ?: "") }
    var cols by remember { mutableStateOf(initialCard?.cols ?: 6) }
    var rows by remember { mutableStateOf(initialCard?.rows ?: 7) }
    var secretColor by remember { mutableStateOf(initialCard?.secretColor ?: "blue") }
    var ruleHint by remember { mutableStateOf(initialCard?.ruleHint ?: "") }

    var designMode by remember { mutableStateOf(MatrixDesignMode.ARTIST) }

    var isTotp by remember { mutableStateOf(initialCard?.isTotp ?: false) }
    var totpSecret by remember { mutableStateOf(initialCard?.totpSecret ?: "") }
    var totpIssuer by remember { mutableStateOf(initialCard?.totpIssuer ?: "") }
    var totpPeriod by remember { mutableIntStateOf(initialCard?.totpPeriod ?: 30) }
    var totpDigits by remember { mutableIntStateOf(initialCard?.totpDigits ?: 6) }
    var totpAlgorithm by remember { mutableStateOf(initialCard?.totpAlgorithm ?: "SHA1") }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var totpRemainingSeconds by remember { mutableIntStateOf(30) }

    // Pending confirmations (bugs 5 and 7)
    var pendingCategory by remember { mutableStateOf<CardCategory?>(null) }
    var showRemoveTotpConfirm by remember { mutableStateOf(false) }
    var pendingFillAction by remember { mutableStateOf<PendingFillAction?>(null) }

    // Initialize existing PIN tiles if editing an existing card
    val initialPinTiles = if (initialCard != null && initialCard.pinPath.isNotEmpty()) {
        initialCard.pinPath.mapNotNull { cell -> initialCard.tiles.find { it.row == cell.row && it.col == cell.col } }
    } else {
        initialCard?.tiles?.filter { it.isPinTile && !it.isBlank } ?: emptyList()
    }

    var strokes by remember {
        mutableStateOf<List<List<PaintedCell>>>(
            if (initialPinTiles.isNotEmpty()) {
                listOf(initialPinTiles.map { PaintedCell(it.row, it.col) })
            } else {
                emptyList()
            }
        )
    }

    var activeStroke by remember { mutableStateOf<List<PaintedCell>>(emptyList()) }

    var artistPinString by remember {
        mutableStateOf(
            if (initialCard?.isTotp == true && !initialCard.totpSecret.isNullOrBlank()) {
                TotpManager.generateCode(
                    secret = initialCard.totpSecret,
                    periodSeconds = initialCard.totpPeriod,
                    digits = initialCard.totpDigits,
                    algorithm = initialCard.totpAlgorithm
                )
            } else if (initialPinTiles.isNotEmpty()) {
                initialPinTiles.joinToString("") { it.digit }
            } else {
                ""
            }
        )
    }

    // Flat ordered sequence of all painted cells across all strokes
    val paintedPath = remember(strokes, activeStroke) {
        val completed = strokes.flatten()
        if (activeStroke.isNotEmpty()) {
            completed + activeStroke
        } else {
            completed
        }
    }

    var tiles by remember {
        mutableStateOf(
            initialCard?.tiles ?: DecoyRandomizer.createBlankMatrix(cols, rows, secretColor)
        )
    }

    // Dynamic TOTP ticker: continuously updates rolling PIN and matrix when TOTP is active
    LaunchedEffect(isTotp, totpSecret, totpPeriod, totpDigits, totpAlgorithm) {
        if (isTotp && totpSecret.isNotBlank()) {
            while (true) {
                val now = System.currentTimeMillis()
                totpRemainingSeconds = TotpManager.getRemainingSeconds(now, totpPeriod)
                val code = TotpManager.generateCode(
                    secret = totpSecret,
                    timeMillis = now,
                    periodSeconds = totpPeriod,
                    digits = totpDigits,
                    algorithm = totpAlgorithm
                )
                if (artistPinString != code) {
                    artistPinString = code
                    if (paintedPath.isNotEmpty()) {
                        tiles = ArtistPathManager.mapDigitsToPath(
                            paintedPath = paintedPath,
                            digitString = code,
                            secretColor = secretColor,
                            existingTiles = tiles
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    var selectedTileForEdit by remember { mutableStateOf<TileData?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var gridSizeDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // TOTP is only "active" when a secret actually exists.
    val totpActive = isTotp && totpSecret.isNotBlank()

    val msgTotpSecretRequired = stringResource(R.string.editor_totp_secret_required)
    val msgPinTooLong = stringResource(R.string.editor_pin_too_long)

    fun committedPath(): List<PaintedCell> = strokes.flatten()

    /** Number of PIN positions that do not have a digit yet. */
    fun missingPinDigits(): Int = when {
        totpActive -> 0
        designMode == MatrixDesignMode.ARTIST ->
            (committedPath().size - artistPinString.length).coerceAtLeast(0)
        else -> tiles.count { it.isPinTile && it.digit == "?" }
    }

    fun pinTooLong(): Boolean = !totpActive &&
        designMode == MatrixDesignMode.ARTIST &&
        committedPath().isNotEmpty() &&
        artistPinString.length > committedPath().size

    /** Fills unset PIN digits with secure random digits. PIN positions are never moved. */
    fun fillMissingPinDigits() {
        if (designMode == MatrixDesignMode.ARTIST) {
            val path = committedPath()
            val filled = ArtistPathManager.fillMissingDigits(artistPinString, path.size)
            artistPinString = filled
            tiles = ArtistPathManager.mapDigitsToPath(path, filled, secretColor, tiles)
        } else {
            val rnd = java.security.SecureRandom()
            tiles = tiles.map {
                if (it.isPinTile && it.digit == "?") {
                    it.copy(digit = rnd.nextInt(10).toString(), colorId = secretColor)
                } else {
                    it
                }
            }
        }
    }

    fun tilesSyncedWithPath(): List<TileData> =
        if (designMode == MatrixDesignMode.ARTIST) {
            ArtistPathManager.mapDigitsToPath(committedPath(), artistPinString, secretColor, tiles)
        } else {
            tiles
        }

    fun randomizeDecoysNow() {
        tiles = DecoyRandomizer.randomizeDecoys(
            tiles = tilesSyncedWithPath(),
            secretColor = secretColor,
            decoyLength = if (totpActive) 6 else null
        )
    }

    fun saveNow() {
        val finalizedTiles = DecoyRandomizer.randomizeDecoys(
            tiles = tilesSyncedWithPath(),
            secretColor = secretColor,
            decoyLength = if (totpActive) 6 else null
        )
        tiles = finalizedTiles
        val path = if (designMode == MatrixDesignMode.ARTIST) {
            committedPath()
        } else {
            // Manual mode: keep the explicit order the user entered the digits in
            ArtistPathManager.syncPathWithPinTiles(committedPath(), finalizedTiles)
                .filter { cell -> finalizedTiles.any { it.row == cell.row && it.col == cell.col && !it.isBlank } }
        }
        val updatedCard = (initialCard ?: CardEntity(name = name)).copy(
            name = name.trim(),
            category = category,
            folder = folder.trim(),
            cols = cols,
            rows = rows,
            secretColor = secretColor,
            ruleHint = ruleHint.trim(),
            tiles = finalizedTiles,
            lastModified = System.currentTimeMillis(),
            isTotp = isTotp,
            totpSecret = if (isTotp) totpSecret.trim() else null,
            totpDigits = totpDigits,
            totpPeriod = totpPeriod,
            totpAlgorithm = totpAlgorithm,
            totpIssuer = if (isTotp) totpIssuer.trim() else null,
            pinPath = path
        )
        onSaveCard(updatedCard)
    }

    fun runAction(action: PendingFillAction) {
        when (action) {
            PendingFillAction.SAVE -> saveNow()
            PendingFillAction.RANDOMIZE -> randomizeDecoysNow()
        }
    }

    /** Validates, asks for consent before generating random PIN digits, then runs [action]. */
    fun requestAction(action: PendingFillAction) {
        if (action == PendingFillAction.SAVE) {
            if (name.isBlank()) return
            if (isTotp && totpSecret.isBlank()) {
                Toast.makeText(context, msgTotpSecretRequired, Toast.LENGTH_LONG).show()
                return
            }
        }
        if (pinTooLong()) {
            Toast.makeText(context, msgPinTooLong, Toast.LENGTH_LONG).show()
            return
        }
        if (missingPinDigits() > 0) {
            pendingFillAction = action
        } else {
            runAction(action)
        }
    }

    fun applyCategory(cat: CardCategory) {
        category = cat
        if (cat != CardCategory.AUTHENTICATOR) {
            isTotp = false
            totpSecret = ""
            totpIssuer = ""
        }
    }

    fun removeTotp() {
        isTotp = false
        totpSecret = ""
        totpIssuer = ""
        if (category == CardCategory.AUTHENTICATOR) {
            category = initialCard?.category?.takeIf { it != CardCategory.AUTHENTICATOR } ?: CardCategory.CREDIT
        }
    }

    // Bug 5: never silently destroy a saved TOTP secret
    pendingCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingCategory = null },
            title = { Text(stringResource(R.string.editor_totp_discard_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.editor_totp_discard_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        applyCategory(target)
                        pendingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.editor_totp_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategory = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showRemoveTotpConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveTotpConfirm = false },
            title = { Text(stringResource(R.string.editor_totp_discard_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.editor_totp_discard_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        removeTotp()
                        showRemoveTotpConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.editor_totp_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveTotpConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Bug 7: random digits are only generated with the user's consent
    pendingFillAction?.let { action ->
        val total = if (designMode == MatrixDesignMode.ARTIST) committedPath().size else tiles.count { it.isPinTile }
        val missing = missingPinDigits()
        AlertDialog(
            onDismissRequest = { pendingFillAction = null },
            title = { Text(stringResource(R.string.editor_fill_digits_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.editor_fill_digits_message, total - missing, total, missing)) },
            confirmButton = {
                Button(onClick = {
                    pendingFillAction = null
                    fillMissingPinDigits()
                    runAction(action)
                }) { Text(stringResource(R.string.editor_fill_digits_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFillAction = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDeleteConfirmDialog && initialCard != null && onDeleteCard != null) {
        val fallbackName = stringResource(R.string.this_matrix_fallback)
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
            title = { Text(stringResource(R.string.delete_card_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_card_message, name.ifBlank { fallbackName })) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteCard(initialCard.id)
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
                    Text(
                        text = if (isNew) stringResource(R.string.editor_title_create) else stringResource(R.string.editor_title_edit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestAction(PendingFillAction.SAVE) }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
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
                        label = { Text(stringResource(R.string.editor_field_name_label)) },
                        placeholder = { Text(stringResource(R.string.editor_field_name_placeholder)) },
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
                                value = stringResource(category.titleRes),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.editor_field_category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                CardCategory.values().forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(cat.titleRes)) },
                                        onClick = {
                                            categoryDropdownExpanded = false
                                            if (cat != category) {
                                                if (cat != CardCategory.AUTHENTICATOR && isTotp && totpSecret.isNotBlank()) {
                                                    pendingCategory = cat
                                                } else {
                                                    applyCategory(cat)
                                                }
                                            }
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
                                label = { Text(stringResource(R.string.editor_field_grid_size_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gridSizeDropdownExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = gridSizeDropdownExpanded,
                                onDismissRequest = { gridSizeDropdownExpanded = false }
                            ) {
                                listOf(Pair(5, 6), Pair(6, 7), Pair(7, 8)).forEach { (c, r) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_grid_tiles_count, c, r, c * r)) },
                                        onClick = {
                                            cols = c
                                            rows = r
                                            strokes = emptyList()
                                            activeStroke = emptyList()
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
                            label = { Text(stringResource(R.string.editor_field_folder_label)) },
                            placeholder = { Text(stringResource(R.string.editor_field_folder_placeholder)) },
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
                                    text = stringResource(R.string.editor_suggestions_label),
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

                    // Dynamic TOTP 2FA Section (only visible for AUTHENTICATOR category or existing TOTP card)
                    if (category == CardCategory.AUTHENTICATOR || isTotp) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTotp) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = null,
                                            tint = if (isTotp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.totp_setup_title),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    if (isTotp) {
                                        TextButton(
                                            onClick = {
                                                if (totpSecret.isNotBlank()) {
                                                    showRemoveTotpConfirm = true
                                                } else {
                                                    removeTotp()
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.totp_remove_totp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (!isTotp) {
                                    Text(
                                        text = stringResource(R.string.totp_setup_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = { showQrScannerDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_scan_qr), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    // TOTP Active Status Banner
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.totp_secret_imported, totpIssuer.ifBlank { name }),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Rolling PIN: $artistPinString • ${totpRemainingSeconds}s remaining",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        OutlinedButton(
                                            onClick = { showQrScannerDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Re-scan", fontSize = 11.sp)
                                        }
                                    }
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
                        text = stringResource(R.string.editor_secret_color_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.editor_secret_color_subtitle),
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

            // Interactive Matrix Designer Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Mode Selector Toggle: Artist Fingerpaint vs Manual Tap
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                if (designMode != MatrixDesignMode.ARTIST) {
                                    val currentPath = strokes.flatten() + activeStroke
                                    val synced = ArtistPathManager.syncPathWithPinTiles(
                                        previousPath = currentPath,
                                        tiles = tiles
                                    )
                                    strokes = if (synced.isNotEmpty()) listOf(synced) else emptyList()
                                    activeStroke = emptyList()
                                    val syncedDigits = ArtistPathManager.pinStringFromPath(synced, tiles)
                                    if (syncedDigits.isNotEmpty()) {
                                        artistPinString = syncedDigits
                                    }
                                    designMode = MatrixDesignMode.ARTIST
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (designMode == MatrixDesignMode.ARTIST) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (designMode == MatrixDesignMode.ARTIST) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            elevation = if (designMode == MatrixDesignMode.ARTIST) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.editor_mode_artist), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (designMode != MatrixDesignMode.MANUAL) {
                                    tiles = tilesSyncedWithPath()
                                    designMode = MatrixDesignMode.MANUAL
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (designMode == MatrixDesignMode.MANUAL) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (designMode == MatrixDesignMode.MANUAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            elevation = if (designMode == MatrixDesignMode.MANUAL) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.editor_mode_manual), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (designMode == MatrixDesignMode.ARTIST) {
                        // Artist Mode Instructions & Digit Input
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.editor_artist_instructions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // PIN String Input Field
                            OutlinedTextField(
                                value = artistPinString,
                                onValueChange = { input ->
                                    if (!isTotp) {
                                        val filtered = input.filter { it.isDigit() }
                                        artistPinString = filtered
                                        tiles = ArtistPathManager.mapDigitsToPath(
                                            paintedPath = paintedPath,
                                            digitString = filtered,
                                            secretColor = secretColor,
                                            existingTiles = tiles
                                        )
                                    }
                                },
                                readOnly = isTotp,
                                label = {
                                    Text(
                                        if (isTotp) "Dynamic TOTP PIN (${totpRemainingSeconds}s)"
                                        else stringResource(R.string.editor_artist_pin_label)
                                    )
                                },
                                placeholder = { Text(stringResource(R.string.editor_artist_pin_placeholder)) },
                                leadingIcon = {
                                    Icon(
                                        if (isTotp) Icons.Default.Timer else Icons.Default.Pin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (!isTotp) {
                                        if (artistPinString.isNotEmpty()) {
                                            IconButton(onClick = {
                                                artistPinString = ""
                                                tiles = ArtistPathManager.mapDigitsToPath(
                                                    paintedPath = paintedPath,
                                                    digitString = "",
                                                    secretColor = secretColor,
                                                    existingTiles = tiles
                                                )
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_cancel))
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                val currentCells = strokes.flatten() + activeStroke
                                                val count = if (currentCells.isNotEmpty()) currentCells.size else 4
                                                val rnd = java.security.SecureRandom()
                                                val generated = (1..count).map { rnd.nextInt(10) }.joinToString("")
                                                artistPinString = generated
                                                tiles = ArtistPathManager.mapDigitsToPath(
                                                    paintedPath = currentCells,
                                                    digitString = generated,
                                                    secretColor = secretColor,
                                                    existingTiles = tiles
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Casino,
                                                    contentDescription = stringResource(R.string.action_randomize_decoys),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Status Indicator
                            val statusText = when {
                                paintedPath.isEmpty() -> stringResource(R.string.editor_artist_status_empty)
                                artistPinString.isEmpty() -> stringResource(R.string.editor_artist_status_enter_pin, paintedPath.size)
                                artistPinString.length < paintedPath.size -> stringResource(R.string.editor_artist_status_partial, artistPinString.length, paintedPath.size)
                                artistPinString.length > paintedPath.size -> stringResource(R.string.editor_artist_status_need_more_tiles, artistPinString.length - paintedPath.size)
                                else -> stringResource(R.string.editor_artist_status_complete, paintedPath.size)
                            }

                            val statusColor = when {
                                artistPinString.isNotEmpty() && artistPinString.length == paintedPath.size -> MaterialTheme.colorScheme.primary
                                artistPinString.isNotEmpty() && artistPinString.length != paintedPath.size -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Artist Action Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Undo Last Stroke
                            OutlinedButton(
                                onClick = {
                                    if (strokes.isNotEmpty()) {
                                        strokes = strokes.dropLast(1)
                                        val remainingCells = strokes.flatten()
                                        tiles = ArtistPathManager.mapDigitsToPath(
                                            paintedPath = remainingCells,
                                            digitString = artistPinString,
                                            secretColor = secretColor,
                                            existingTiles = tiles
                                        )
                                    }
                                },
                                enabled = strokes.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.editor_artist_undo_stroke), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Clear Path
                            OutlinedButton(
                                onClick = {
                                    strokes = emptyList()
                                    activeStroke = emptyList()
                                    tiles = DecoyRandomizer.createBlankMatrix(cols, rows, secretColor)
                                },
                                enabled = paintedPath.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.editor_artist_clear_path), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Randomize Decoys
                            OutlinedButton(
                                onClick = { requestAction(PendingFillAction.RANDOMIZE) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_randomize_decoys), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Manual Mode Header with Randomize
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.editor_grid_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = { requestAction(PendingFillAction.RANDOMIZE) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_randomize_decoys), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    }

                    // Matrix View inside Editor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .height(440.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridView(
                            cols = cols,
                            rows = rows,
                            tiles = tiles,
                            isArtistMode = (designMode == MatrixDesignMode.ARTIST),
                            paintedPath = paintedPath,
                            strokes = strokes,
                            activeStroke = activeStroke,
                            secretColorId = secretColor,
                            onTileClick = { clickedTile ->
                                selectedTileForEdit = clickedTile
                            },
                            onCellTraversed = { cell ->
                                val lastCell = activeStroke.lastOrNull()
                                val cellsToAdd = if (lastCell != null && lastCell != cell) {
                                    ArtistPathManager.interpolateBetween(lastCell, cell)
                                } else {
                                    listOf(cell)
                                }

                                val alreadyPresent = (strokes.flatten() + activeStroke).toSet()
                                val newCells = cellsToAdd.filter { !alreadyPresent.contains(it) }

                                if (newCells.isNotEmpty()) {
                                    val updatedActive = activeStroke + newCells
                                    activeStroke = updatedActive
                                    val currentFull = strokes.flatten() + updatedActive
                                    tiles = ArtistPathManager.mapDigitsToPath(
                                        paintedPath = currentFull,
                                        digitString = artistPinString,
                                        secretColor = secretColor,
                                        existingTiles = tiles
                                    )
                                }
                            },
                            onStrokeFinished = {
                                if (activeStroke.isNotEmpty()) {
                                    strokes = strokes + listOf(activeStroke)
                                    activeStroke = emptyList()
                                }
                            },
                            onCellRemoved = { cellToRemove ->
                                strokes = ArtistPathManager.removeCellFromStrokes(strokes, cellToRemove)
                                activeStroke = activeStroke.filter { it != cellToRemove }
                                val remainingPath = strokes.flatten() + activeStroke
                                tiles = ArtistPathManager.mapDigitsToPath(
                                    paintedPath = remainingPath,
                                    digitString = artistPinString,
                                    secretColor = secretColor,
                                    existingTiles = tiles
                                )
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.editor_unassigned_tiles_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            // Secret Rule Note
            OutlinedTextField(
                value = ruleHint,
                onValueChange = { ruleHint = it },
                label = { Text(stringResource(R.string.editor_field_rule_label)) },
                placeholder = { Text(stringResource(R.string.editor_field_rule_placeholder)) },
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
                    Text(stringResource(R.string.action_delete_card_matrix))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal to set digit on a specific tile (used in Manual Mode)
    if (selectedTileForEdit != null) {
        val targetTile = selectedTileForEdit!!
        AlertDialog(
            onDismissRequest = { selectedTileForEdit = null },
            title = {
                Text(
                    text = stringResource(R.string.editor_tile_dialog_title, targetTile.row + 1, targetTile.col + 1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.editor_tile_dialog_assign, stringResource(PaletteColor.find(secretColor).nameRes)),
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
                        Text(stringResource(R.string.editor_tile_dialog_reset), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedTileForEdit = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showQrScannerDialog) {
        QrScannerDialog(
            onDismiss = { showQrScannerDialog = false },
            onTotpParsed = { data ->
                isTotp = true
                totpSecret = data.secret
                totpIssuer = data.issuer.ifBlank { data.label }
                totpPeriod = data.period
                totpDigits = data.digits
                totpAlgorithm = data.algorithm
                category = CardCategory.AUTHENTICATOR
                if (name.isBlank() && totpIssuer.isNotBlank()) {
                    name = totpIssuer
                }
                val code = TotpManager.generateCode(
                    secret = data.secret,
                    timeMillis = System.currentTimeMillis(),
                    periodSeconds = data.period,
                    digits = data.digits,
                    algorithm = data.algorithm
                )
                artistPinString = code
                if (paintedPath.isNotEmpty()) {
                    tiles = ArtistPathManager.mapDigitsToPath(
                        paintedPath = paintedPath,
                        digitString = code,
                        secretColor = secretColor,
                        existingTiles = tiles
                    )
                }
                showQrScannerDialog = false
            }
        )
    }
}