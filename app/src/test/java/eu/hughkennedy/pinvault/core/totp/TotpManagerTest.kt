package eu.hughkennedy.pinvault.core.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpManagerTest {

    // RFC 6238 Appendix B test secret: "12345678901234567890" in Base32
    private val rfcSecretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun testBase32Decoding() {
        val decoded = TotpManager.decodeBase32("JBSWY3DPEE======")
        assertNotNull(decoded)
        val decodedString = String(decoded!!, Charsets.US_ASCII)
        assertEquals("Hello!", decodedString)

        // Lowercase and dashes/spaces should be sanitized
        val decodedMessy = TotpManager.decodeBase32("jbsw y3dp-ee==")
        assertNotNull(decodedMessy)
        assertEquals("Hello!", String(decodedMessy!!, Charsets.US_ASCII))

        // JBSWY3DPEHPK3PXP is a 10-byte binary secret
        val secretBytes = TotpManager.decodeBase32("JBSWY3DPEHPK3PXP")
        assertNotNull(secretBytes)
        assertEquals(10, secretBytes!!.size)
        assertEquals(0x48.toByte(), secretBytes[0]) // 'H'
        assertEquals(0x65.toByte(), secretBytes[1]) // 'e'

        // Invalid characters
        assertNull(TotpManager.decodeBase32("INVALID189!#"))
    }

    @Test
    fun testRfc6238TestVectorsSha1() {
        // Test vectors from RFC 6238 Table 1 (8 digits):
        // Time: 59s -> 94287082
        assertEquals(
            "94287082",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 59L * 1000L,
                periodSeconds = 30,
                digits = 8,
                algorithm = "SHA1"
            )
        )
        // 6 digits (last 6 digits: 287082)
        assertEquals(
            "287082",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 59L * 1000L,
                periodSeconds = 30,
                digits = 6,
                algorithm = "SHA1"
            )
        )

        // Time: 1111111109s -> 07081804 (6-digit: 081804)
        assertEquals(
            "07081804",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1111111109L * 1000L,
                periodSeconds = 30,
                digits = 8,
                algorithm = "SHA1"
            )
        )
        assertEquals(
            "081804",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1111111109L * 1000L,
                periodSeconds = 30,
                digits = 6,
                algorithm = "SHA1"
            )
        )

        // Time: 1111111111s -> 14050471 (6-digit: 050471)
        assertEquals(
            "14050471",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1111111111L * 1000L,
                periodSeconds = 30,
                digits = 8,
                algorithm = "SHA1"
            )
        )
        assertEquals(
            "050471",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1111111111L * 1000L,
                periodSeconds = 30,
                digits = 6,
                algorithm = "SHA1"
            )
        )

        // Time: 1234567890s -> 89005924 (6-digit: 005924)
        assertEquals(
            "89005924",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1234567890L * 1000L,
                periodSeconds = 30,
                digits = 8,
                algorithm = "SHA1"
            )
        )
        assertEquals(
            "005924",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 1234567890L * 1000L,
                periodSeconds = 30,
                digits = 6,
                algorithm = "SHA1"
            )
        )

        // Time: 2000000000s -> 69279037 (6-digit: 279037)
        assertEquals(
            "69279037",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 2000000000L * 1000L,
                periodSeconds = 30,
                digits = 8,
                algorithm = "SHA1"
            )
        )
        assertEquals(
            "279037",
            TotpManager.generateCode(
                secret = rfcSecretBase32,
                timeMillis = 2000000000L * 1000L,
                periodSeconds = 30,
                digits = 6,
                algorithm = "SHA1"
            )
        )
    }

    @Test
    fun testParseStandardOtpAuthUri() {
        val uri = "otpauth://totp/GitHub:hughk?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30"
        val data = TotpManager.parseOtpAuthUri(uri)

        assertNotNull(data)
        assertEquals("JBSWY3DPEHPK3PXP", data!!.secret)
        assertEquals("GitHub", data.issuer)
        assertEquals("hughk", data.account)
        assertEquals(6, data.digits)
        assertEquals(30, data.period)
    }

    @Test
    fun testParseEncodedOtpAuthUri() {
        val uri = "otpauth://totp/Amazon%20Web%20Services:admin%40company.com?secret=JBSWY3DPEHPK3PXP&issuer=Amazon%20Web%20Services"
        val data = TotpManager.parseOtpAuthUri(uri)

        assertNotNull(data)
        assertEquals("JBSWY3DPEHPK3PXP", data!!.secret)
        assertEquals("Amazon Web Services", data.issuer)
        assertEquals("admin@company.com", data.account)
        assertEquals(6, data.digits)
        assertEquals(30, data.period)
    }

    @Test
    fun testParseRawBase32Secret() {
        val data = TotpManager.parseOtpAuthUri("JBSWY3DPEHPK3PXP")
        assertNotNull(data)
        assertEquals("JBSWY3DPEHPK3PXP", data!!.secret)
        assertEquals(6, data.digits)
    }

    @Test
    fun testCountdownTiming() {
        // At 1000s, 1000 % 30 = 10s elapsed -> 20s remaining
        val rem = TotpManager.getRemainingSeconds(1000L * 1000L, 30)
        assertEquals(20, rem)

        val progress = TotpManager.getProgress(1000L * 1000L, 30)
        assertEquals(20f / 30f, progress, 0.01f)
    }
}
