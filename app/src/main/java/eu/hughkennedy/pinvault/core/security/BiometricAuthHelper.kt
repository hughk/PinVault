package eu.hughkennedy.pinvault.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun promptBiometric(
        activity: FragmentActivity,
        title: String? = null,
        subtitle: String? = null,
        negativeButtonText: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptTitle = title ?: activity.getString(eu.hughkennedy.pinvault.R.string.biometric_prompt_verify_title)
        val promptSubtitle = subtitle ?: activity.getString(eu.hughkennedy.pinvault.R.string.biometric_prompt_verify_subtitle)
        val promptNegative = negativeButtonText ?: activity.getString(eu.hughkennedy.pinvault.R.string.action_cancel)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError(activity.getString(eu.hughkennedy.pinvault.R.string.biometric_failed))
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText(promptNegative)
            .build()

        prompt.authenticate(promptInfo)
    }
}
