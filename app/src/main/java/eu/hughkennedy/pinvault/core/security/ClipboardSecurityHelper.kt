package eu.hughkennedy.pinvault.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles secure copying of sensitive credentials (TOTP codes, backup JSON) to the Android clipboard.
 * - Flags clipboard content as sensitive on Android 13+ (API 33+) so OS preview overlays do not leak secrets on screen.
 * - Automatically wipes the copied secret from the clipboard after a configurable timeout (default: 30s) if unchanged.
 */
object ClipboardSecurityHelper {

    fun copySensitiveText(
        context: Context,
        label: String,
        text: String,
        autoClearSeconds: Long = 30L,
        scope: CoroutineScope? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)

        // Schedule auto-wipe if a coroutine scope is provided
        scope?.launch {
            delay(autoClearSeconds * 1000L)
            try {
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0)?.text?.toString()
                    if (currentText == text) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboard.clearPrimaryClip()
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore any security/permission exceptions during background clipboard access
            }
        }
    }
}
