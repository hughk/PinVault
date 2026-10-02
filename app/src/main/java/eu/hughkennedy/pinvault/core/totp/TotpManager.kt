package eu.hughkennedy.pinvault.core.totp

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Data extracted from an otpauth://totp/ URI.
 */
data class TotpUriData(
    val secret: String,
    val label: String,
    val issuer: String,
    val account: String,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1"
)

/**
 * High-performance, RFC 6238 compliant Time-based One-Time Password (TOTP) engine.
 * Fully offline, self-contained, and cryptographically verified.
 */
object TotpManager {

    /**
     * Parses an otpauth://totp/ URI (from a QR code) or raw Base32 secret string.
     */
    fun parseOtpAuthUri(rawInput: String): TotpUriData? {
        val trimmed = rawInput.trim()

        // Handle full otpauth:// URI
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            try {
                val uriWithoutScheme = trimmed.substring("otpauth://".length)
                val typeEnd = uriWithoutScheme.indexOf('/')
                if (typeEnd < 0) return null

                val type = uriWithoutScheme.substring(0, typeEnd).lowercase()
                if (type != "totp") return null // PinVault focuses on time-based dynamic OTP

                val rest = uriWithoutScheme.substring(typeEnd + 1)
                val questionMark = rest.indexOf('?')
                val rawLabel = if (questionMark >= 0) rest.substring(0, questionMark) else rest
                val queryString = if (questionMark >= 0) rest.substring(questionMark + 1) else ""

                val label = try {
                    URLDecoder.decode(rawLabel, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    rawLabel
                }

                val queryParams = mutableMapOf<String, String>()
                for (param in queryString.split('&')) {
                    val parts = param.split('=', limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].lowercase()
                        val value = try {
                            URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                        } catch (_: Exception) {
                            parts[1]
                        }
                        queryParams[key] = value
                    }
                }

                val rawSecret = queryParams["secret"] ?: return null
                val cleanSecret = rawSecret.replace("\\s|-".toRegex(), "").uppercase()
                if (cleanSecret.isEmpty() || !isValidBase32(cleanSecret)) return null

                var issuerParam = queryParams["issuer"] ?: ""
                var account = label

                // If label is in format "Issuer:Account"
                if (label.contains(':')) {
                    val parts = label.split(':', limit = 2)
                    if (issuerParam.isEmpty()) {
                        issuerParam = parts[0].trim()
                    }
                    account = parts[1].trim()
                }

                val digits = queryParams["digits"]?.toIntOrNull() ?: 6
                val period = queryParams["period"]?.toIntOrNull() ?: 30
                val algorithm = queryParams["algorithm"]?.uppercase() ?: "SHA1"

                return TotpUriData(
                    secret = cleanSecret,
                    label = label,
                    issuer = issuerParam,
                    account = account,
                    digits = if (digits in 6..8) digits else 6,
                    period = if (period in 10..300) period else 30,
                    algorithm = algorithm
                )
            } catch (_: Exception) {
                return null
            }
        }

        // If the user entered/scanned a raw Base32 secret string directly
        val clean = trimmed.replace("\\s|-".toRegex(), "").uppercase()
        if (clean.length >= 8 && isValidBase32(clean)) {
            return TotpUriData(
                secret = clean,
                label = "Authenticator",
                issuer = "",
                account = "",
                digits = 6,
                period = 30,
                algorithm = "SHA1"
            )
        }

        return null
    }

    /**
     * Generates the numeric TOTP string for the given secret at [timeMillis]
     * following RFC 6238 and RFC 4226.
     */
    fun generateCode(
        secret: String,
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        val secretBytes = decodeBase32(secret) ?: return "0".repeat(digits)
        val timeStep = (timeMillis / 1000L) / periodSeconds

        val counterBytes = ByteBuffer.allocate(8).putLong(timeStep).array()

        val macAlgorithm = when (algorithm.uppercase()) {
            "SHA256", "HMACSHA256" -> "HmacSHA256"
            "SHA512", "HMACSHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }

        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(secretBytes, macAlgorithm))
        val hash = mac.doFinal(counterBytes)

        // Dynamic truncation (RFC 4226 §5.4)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val modulo = 10.0.pow(digits.toDouble()).toInt()
        val otp = binary % modulo
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Returns remaining seconds in the current TOTP cycle (e.g. 1 to [periodSeconds]).
     */
    fun getRemainingSeconds(
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30
    ): Int {
        val currentSec = (timeMillis / 1000L) % periodSeconds
        val remaining = periodSeconds - currentSec
        return if (remaining <= 0) periodSeconds else remaining.toInt()
    }

    /**
     * Returns countdown progress from 1.0f (just refreshed) down to 0.0f (about to expire).
     */
    fun getProgress(
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30
    ): Float {
        val rem = getRemainingSeconds(timeMillis, periodSeconds)
        return (rem.toFloat() / periodSeconds.toFloat()).coerceIn(0f, 1f)
    }

    fun isValidBase32(str: String): Boolean {
        if (str.isEmpty()) return false
        val clean = str.trimEnd('=')
        return clean.all { c ->
            (c in 'A'..'Z') || (c in '2'..'7')
        }
    }

    /**
     * RFC 4648 standard Base32 decoder.
     */
    fun decodeBase32(encoded: String): ByteArray? {
        val clean = encoded.replace("\\s|-".toRegex(), "").trimEnd('=').uppercase()
        if (clean.isEmpty()) return null

        var buffer = 0
        var bitsLeft = 0
        val result = mutableListOf<Byte>()

        for (ch in clean) {
            val value = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in '2'..'7' -> ch - '2' + 26
                else -> return null
            }

            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                val b = ((buffer shr bitsLeft) and 0xff).toByte()
                result.add(b)
            }
        }

        return result.toByteArray()
    }
}
