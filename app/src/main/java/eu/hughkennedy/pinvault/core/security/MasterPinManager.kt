package eu.hughkennedy.pinvault.core.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Manages Master PIN security with key stretching, unique per-device salting, constant-time verification,
 * brute-force rate limiting (wall-clock + monotonic clock), and seamless upwards migration for existing PIN hashes.
 */
class MasterPinManager(
    private val prefs: SharedPreferences,
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() },
    private val monotonicClockMillis: () -> Long = { defaultMonotonicMillis() }
) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    companion object {
        private const val PREFS_NAME = "pin_keeper_prefs"
        private const val PREF_MASTER_PIN_HASH = "master_pin_hash"
        private const val PREF_MASTER_PIN_SALT = "master_pin_salt"
        private const val PREF_MASTER_PIN_KDF = "master_pin_kdf"
        private const val PREF_FAILED_ATTEMPTS = "master_pin_failed_attempts"
        private const val PREF_LOCKED_UNTIL = "master_pin_locked_until"
        private const val PREF_LOCKED_UNTIL_MONOTONIC = "master_pin_locked_until_monotonic"

        private const val KDF_PBKDF2 = "PBKDF2WithHmacSHA256"
        private const val LEGACY_STATIC_SALT = "pin_keeper_local_salt"
        const val DEFAULT_INITIAL_PIN = "1234"

        private fun defaultMonotonicMillis(): Long {
            return try {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                if (elapsed > 0L) elapsed else System.nanoTime() / 1_000_000L
            } catch (_: Throwable) {
                System.nanoTime() / 1_000_000L
            }
        }
    }

    /**
     * Checks if a Master PIN has ever been set or migrated.
     */
    fun isMasterPinSet(): Boolean {
        return prefs.contains(PREF_MASTER_PIN_HASH)
    }

    /**
     * Returns remaining lockout seconds if the user is currently locked out due to failed attempts.
     * Combines wall-clock time and monotonic elapsed time so advancing the OS system clock cannot bypass lockout.
     */
    fun getRemainingLockoutSeconds(): Long {
        val lockedUntilWall = prefs.getLong(PREF_LOCKED_UNTIL, 0L)
        val wallDiff = (lockedUntilWall - wallClockMillis()) / 1000L

        val lockedUntilMono = prefs.getLong(PREF_LOCKED_UNTIL_MONOTONIC, 0L)
        val nowMono = monotonicClockMillis()
        // Enforce monotonic lockout when within a 1-hour window (handles device reboots gracefully)
        val monoDiff = if (lockedUntilMono > 0L && (lockedUntilMono - nowMono) <= 3_600_000L) {
            (lockedUntilMono - nowMono) / 1000L
        } else {
            0L
        }

        val maxRemaining = maxOf(wallDiff, monoDiff)
        return if (maxRemaining > 0) maxRemaining else 0L
    }

    fun isLockedOut(): Boolean = getRemainingLockoutSeconds() > 0

    /**
     * Sets or updates the Master PIN using PBKDF2WithHmacSHA256 with a unique per-device 16-byte salt.
     */
    fun setMasterPin(pin: String) {
        val salt = CryptoManager.generateRandomBytes(16)
        val pinChars = pin.toCharArray()
        val derivedKey = try {
            CryptoManager.deriveKey(pinChars, salt)
        } finally {
            CryptoManager.wipe(pinChars)
        }

        val saltBase64 = CryptoManager.toBase64(salt)
        val hashBase64 = CryptoManager.toBase64(derivedKey.encoded)

        prefs.edit()
            .putString(PREF_MASTER_PIN_SALT, saltBase64)
            .putString(PREF_MASTER_PIN_HASH, hashBase64)
            .putString(PREF_MASTER_PIN_KDF, KDF_PBKDF2)
            .putInt(PREF_FAILED_ATTEMPTS, 0)
            .putLong(PREF_LOCKED_UNTIL, 0L)
            .putLong(PREF_LOCKED_UNTIL_MONOTONIC, 0L)
            .apply()
    }

    /**
     * Verifies the entered PIN against the stored hash.
     * Supports transparent upwards migration:
     * - If no PIN was set, checks against default 1234 and migrates to PBKDF2.
     * - If legacy single-round SHA-256 was used, verifies using constant-time comparison and auto-upgrades to PBKDF2.
     * Enforces rate limiting lockout on failure.
     */
    fun verifyMasterPin(pin: String): Boolean {
        if (isLockedOut()) {
            return false
        }

        val pinBytes = pin.toByteArray(Charsets.UTF_8)

        // Case 1: First launch / Uninitialized PIN
        if (!isMasterPinSet()) {
            val defaultBytes = DEFAULT_INITIAL_PIN.toByteArray(Charsets.UTF_8)
            val matches = MessageDigest.isEqual(pinBytes, defaultBytes)
            if (matches) {
                // Auto-migrate default PIN to modern PBKDF2
                setMasterPin(DEFAULT_INITIAL_PIN)
                onVerificationSuccess()
                return true
            } else {
                onVerificationFailure()
                return false
            }
        }

        val storedHash = prefs.getString(PREF_MASTER_PIN_HASH, "") ?: ""
        val kdf = prefs.getString(PREF_MASTER_PIN_KDF, null)

        // Case 2: Modern PBKDF2
        if (kdf == KDF_PBKDF2) {
            val saltBase64 = prefs.getString(PREF_MASTER_PIN_SALT, "") ?: ""
            if (saltBase64.isBlank() || storedHash.isBlank()) {
                onVerificationFailure()
                return false
            }
            val salt = try {
                CryptoManager.fromBase64(saltBase64)
            } catch (_: Exception) {
                onVerificationFailure()
                return false
            }

            val pinChars = pin.toCharArray()
            val derived = try {
                CryptoManager.deriveKey(pinChars, salt)
            } finally {
                CryptoManager.wipe(pinChars)
            }

            val expectedBytes = try {
                CryptoManager.fromBase64(storedHash)
            } catch (_: Exception) {
                ByteArray(0)
            }

            val matches = MessageDigest.isEqual(derived.encoded, expectedBytes)
            if (matches) {
                onVerificationSuccess()
                return true
            } else {
                onVerificationFailure()
                return false
            }
        }

        // Case 3: Legacy single-round SHA-256 with static salt
        val legacyComputedHex = sha256("$pin:$LEGACY_STATIC_SALT")
        val matches = MessageDigest.isEqual(
            legacyComputedHex.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )

        if (matches) {
            // Transparently migrate legacy hash to PBKDF2 with unique salt
            setMasterPin(pin)
            onVerificationSuccess()
            return true
        } else {
            onVerificationFailure()
            return false
        }
    }

    private fun onVerificationSuccess() {
        prefs.edit()
            .putInt(PREF_FAILED_ATTEMPTS, 0)
            .putLong(PREF_LOCKED_UNTIL, 0L)
            .putLong(PREF_LOCKED_UNTIL_MONOTONIC, 0L)
            .apply()
    }

    private fun onVerificationFailure() {
        val attempts = prefs.getInt(PREF_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(PREF_FAILED_ATTEMPTS, attempts)

        val lockoutMillis = when {
            attempts >= 10 -> 300_000L // 5 minutes
            attempts >= 8  -> 60_000L  // 1 minute
            attempts >= 5  -> 30_000L  // 30 seconds
            else -> 0L
        }

        if (lockoutMillis > 0) {
            editor.putLong(PREF_LOCKED_UNTIL, wallClockMillis() + lockoutMillis)
            editor.putLong(PREF_LOCKED_UNTIL_MONOTONIC, monotonicClockMillis() + lockoutMillis)
        }
        editor.apply()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
