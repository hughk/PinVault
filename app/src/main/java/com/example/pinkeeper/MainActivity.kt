package com.example.pinkeeper

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
import com.example.pinkeeper.core.model.CardEntity
import com.example.pinkeeper.core.repository.VaultRepository
import com.example.pinkeeper.core.security.BiometricAuthHelper
import com.example.pinkeeper.theme.PinVaultTheme
import com.example.pinkeeper.ui.components.AboutDialog
import com.example.pinkeeper.ui.components.BackupRestoreDialog
import com.example.pinkeeper.ui.screens.HelpScreen
import com.example.pinkeeper.ui.screens.LockScreen
import com.example.pinkeeper.ui.screens.MatrixDetailScreen
import com.example.pinkeeper.ui.screens.MatrixEditorScreen
import com.example.pinkeeper.ui.screens.VaultScreen

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
                MatrixEditorScreen(
                    initialCard = initialCard,
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
