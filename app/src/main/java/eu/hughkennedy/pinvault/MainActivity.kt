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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Privacy Protection: Prevent screenshots and conceal preview in Android Recents
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        repository = VaultRepository(applicationContext)

        enableEdgeToEdge()
        setContent {
            PinVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PinVaultAppRoot(
                        repository = repository,
                        onBiometricAuth = { title, onSuccess ->
                            BiometricAuthHelper.promptBiometric(
                                activity = this@MainActivity,
                                title = title,
                                onSuccess = onSuccess,
                                onError = { }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PinVaultAppRoot(
    repository: VaultRepository,
    onBiometricAuth: (String, () -> Unit) -> Unit
) {
    val cards by repository.cards.collectAsState()
    var isLocked by remember { mutableStateOf(false) } // starts unlocked for instant convenience or can lock
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Vault) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (isLocked) {
        LockScreen(
            onUnlockSuccess = { isLocked = false },
            onBiometricRequested = {
                onBiometricAuth("Unlock Pin Vault") {
                    isLocked = false
                }
            },
            onVerifyPin = { pin -> repository.verifyMasterPin(pin) }
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
                        onBiometricAuthRequested = { revealCallback ->
                            onBiometricAuth("Reveal Secret Pattern") {
                                revealCallback()
                            }
                        }
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
