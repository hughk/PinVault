package eu.hughkennedy.pinvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.repository.VaultRepository
import eu.hughkennedy.pinvault.core.security.BiometricAuthHelper
import eu.hughkennedy.pinvault.core.security.BiometricKeyManager
import eu.hughkennedy.pinvault.theme.PinVaultTheme
import eu.hughkennedy.pinvault.ui.components.AboutDialog
import eu.hughkennedy.pinvault.ui.components.BackupRestoreDialog
import eu.hughkennedy.pinvault.ui.screens.HelpScreen
import eu.hughkennedy.pinvault.ui.screens.LockScreen
import eu.hughkennedy.pinvault.ui.screens.MatrixDetailScreen
import eu.hughkennedy.pinvault.ui.screens.MatrixEditorScreen
import eu.hughkennedy.pinvault.ui.screens.VaultScreen

sealed class AppScreen {
    object Vault : AppScreen()
    data class Detail(val cardId: String) : AppScreen()
    data class Editor(val cardId: String? = null) : AppScreen()
    object Help : AppScreen()
}

class MainActivity : FragmentActivity() {

    private lateinit var repository: VaultRepository
    private var backgroundTimestamp: Long = 0L
    private val isLockedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Privacy Protection: Prevent screenshots and conceal preview in Android Recents.
        // In debug builds, FLAG_SECURE is omitted so developers and testers can capture screenshots.
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        repository = VaultRepository(applicationContext)
        // Lock vault on launch if a Master PIN has been set
        isLockedState.value = repository.isMasterPinSet()

        enableEdgeToEdge()
        setContent {
            PinVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PinVaultAppRoot(
                        repository = repository,
                        isLockedState = isLockedState,
                        onBiometricUnlock = { onSuccess ->
                            BiometricAuthHelper.promptBiometricUnlock(
                                activity = this@MainActivity,
                                onSuccess = onSuccess,
                                onError = { }
                            )
                        },
                        onBiometricReveal = { encryptedSecret, onRevealed ->
                            BiometricAuthHelper.promptBiometricReveal(
                                activity = this@MainActivity,
                                encryptedCardSecretBase64 = encryptedSecret,
                                onSuccess = onRevealed,
                                onError = { }
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundTimestamp > 0L && repository.isMasterPinSet()) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= 30_000L) {
                isLockedState.value = true
            }
        }
    }
}

@Composable
fun PinVaultAppRoot(
    repository: VaultRepository,
    isLockedState: androidx.compose.runtime.MutableState<Boolean>,
    onBiometricUnlock: (() -> Unit) -> Unit,
    onBiometricReveal: (String, (BiometricKeyManager.DecryptedCardSecret) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cards by repository.cards.collectAsState()
    var isLocked by isLockedState
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Vault) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (isLocked) {
        LockScreen(
            onUnlockSuccess = { isLocked = false },
            onBiometricRequested = {
                onBiometricUnlock {
                    isLocked = false
                }
            },
            onVerifyPin = { pin -> repository.verifyMasterPin(pin) },
            getRemainingLockoutSeconds = { repository.getRemainingPinLockoutSeconds() }
        )
    } else {
        when (val screen = currentScreen) {
            is AppScreen.Vault -> {
                VaultScreen(
                    cards = cards,
                    onCardSelected = { cardId -> currentScreen = AppScreen.Detail(cardId) },
                    onEditCard = { cardId -> currentScreen = AppScreen.Editor(cardId) },
                    onDeleteCard = { cardId -> repository.deleteCard(cardId) },
                    onAddCard = { currentScreen = AppScreen.Editor(null) },
                    onLockApp = { isLocked = true },
                    onOpenBackup = { showBackupDialog = true },
                    onOpenHelp = { currentScreen = AppScreen.Help },
                    onOpenAbout = { showAboutDialog = true }
                )
            }
            is AppScreen.Detail -> {
                val card = repository.getCardById(screen.cardId)
                if (card == null) {
                    currentScreen = AppScreen.Vault
                } else {
                    MatrixDetailScreen(
                        card = card,
                        onBack = { currentScreen = AppScreen.Vault },
                        onEditCard = { currentScreen = AppScreen.Editor(card.id) },
                        onDeleteCard = {
                            repository.deleteCard(card.id)
                            currentScreen = AppScreen.Vault
                        },
                        onBiometricRevealRequested = onBiometricReveal
                    )
                }
            }
            is AppScreen.Editor -> {
                val initialCard = screen.cardId?.let { repository.getCardById(it) }
                val existingFolders = cards.map { it.folder.trim() }.filter { it.isNotBlank() }.distinct()
                MatrixEditorScreen(
                    initialCard = initialCard,
                    existingFolders = existingFolders,
                    onSaveCard = { savedCard ->
                        if (initialCard == null) {
                            repository.addCard(savedCard)
                        } else {
                            repository.updateCard(savedCard)
                        }
                        currentScreen = AppScreen.Detail(savedCard.id)
                    },
                    onDeleteCard = { cardId ->
                        repository.deleteCard(cardId)
                        currentScreen = AppScreen.Vault
                    },
                    onCancel = {
                        currentScreen = if (initialCard != null) AppScreen.Detail(initialCard.id) else AppScreen.Vault
                    }
                )
            }
            is AppScreen.Help -> {
                HelpScreen(
                    onBack = { currentScreen = AppScreen.Vault },
                    onOpenAbout = { showAboutDialog = true }
                )
            }
        }

        if (showBackupDialog) {
            BackupRestoreDialog(
                cards = cards,
                onDismiss = { showBackupDialog = false },
                onImportSuccess = { importedCards, replace ->
                    repository.restoreBackup(importedCards, replace)
                }
            )
        }

        if (showAboutDialog) {
            AboutDialog(
                onDismiss = { showAboutDialog = false },
                onOpenHelp = {
                    showAboutDialog = false
                    currentScreen = AppScreen.Help
                }
            )
        }
    }
}
