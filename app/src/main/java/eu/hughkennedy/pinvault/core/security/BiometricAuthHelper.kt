package eu.hughkennedy.pinvault.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import eu.hughkennedy.pinvault.R
import javax.crypto.Cipher

/**
 * Biometric authentication helper integrated with Android KeyStore-backed cryptography.
 *
 * Rather than relying on simple boolean callbacks (which can be bypassed by Frida or hooking),
 * this helper initializes an unauthenticated Cipher from an Android KeyStore key requiring
 * user authentication and encapsulates it inside a BiometricPrompt.CryptoObject.
 *
 * When onAuthenticationSucceeded executes, the authenticated Cipher is required to decrypt
 * sensitive credentials (such as the master vault token or card secret data).
 * If biometric authentication was not genuine (e.g. hooked), the TEE hardware rejects the
 * decryption, preventing unauthorized access.
 */
object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Presents a biometric prompt backed by a KeyStore-initialized CryptoObject.
     * onSuccess is provided with the authenticated Cipher instance to decrypt sensitive data.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        title: String? = null,
        subtitle: String? = null,
        negativeButtonText: String? = null,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptTitle = title ?: activity.getString(R.string.biometric_prompt_verify_title)
        val promptSubtitle = subtitle ?: activity.getString(R.string.biometric_prompt_verify_subtitle)
        val promptNegative = negativeButtonText ?: activity.getString(R.string.action_cancel)

        val cipher: Cipher = try {
            BiometricKeyManager.createDecryptionCipher()
        } catch (e: Exception) {
            onError("Keystore initialization error: ${e.message}")
            return
        }

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    onError(activity.getString(R.string.biometric_failed))
                    return
                }
                try {
                    onSuccess(authenticatedCipher)
                } catch (e: Exception) {
                    onError("Keystore cryptographic verification failed: ${e.message}")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError(activity.getString(R.string.biometric_failed))
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText(promptNegative)
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }

    /**
     * Unlocks the application by decrypting and verifying the master vault token using
     * the KeyStore-authenticated Cipher.
     */
    fun promptBiometricUnlock(
        activity: FragmentActivity,
        title: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val encryptedToken = BiometricKeyManager.ensureMasterToken(activity.applicationContext)
        promptBiometric(
            activity = activity,
            title = title ?: activity.getString(R.string.biometric_prompt_unlock),
            onSuccess = { authenticatedCipher ->
                val decryptedToken = BiometricKeyManager.decryptData(authenticatedCipher, encryptedToken)
                if (BiometricKeyManager.verifyMasterToken(activity.applicationContext, decryptedToken)) {
                    onSuccess()
                } else {
                    onError("Invalid master token verification")
                }
            },
            onError = onError
        )
    }

    /**
     * Reveals sensitive card pattern secrets by decrypting the card credentials using
     * the KeyStore-authenticated Cipher.
     */
    fun promptBiometricReveal(
        activity: FragmentActivity,
        encryptedCardSecretBase64: String,
        title: String? = null,
        onSuccess: (BiometricKeyManager.DecryptedCardSecret) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        promptBiometric(
            activity = activity,
            title = title ?: activity.getString(R.string.biometric_prompt_reveal),
            onSuccess = { authenticatedCipher ->
                val decryptedSecret = BiometricKeyManager.decryptCardSecret(authenticatedCipher, encryptedCardSecretBase64)
                onSuccess(decryptedSecret)
            },
            onError = onError
        )
    }
}
