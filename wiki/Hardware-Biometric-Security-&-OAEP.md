# Hardware Biometric Security & OAEP

Pin Vault implements hardware-enforced cryptographic security using the **Android KeyStore**, **Trusted Execution Environment (TEE) / StrongBox**, and **BiometricPrompt**.

---

## 🏛️ Architecture Overview

The cryptographic bridge between biometric verification and memory protection is managed by `BiometricKeyManager.kt`:

1. **Hardware-Backed Key Generation**:
   - An RSA 2048-bit key pair (`pin_vault_biometric_key`) is generated inside the Android KeyStore.
   - The private key is bound to biometric authentication (`setUserAuthenticationRequired(true)`).
   - In Android 11+ (API 30+), keys are bound with `AUTH_BIOMETRIC_STRONG` (Class 3 Hardware Biometrics).
   - New biometric enrollments immediately invalidate the key (`setInvalidatedByBiometricEnrollment(true)`), preventing unauthorized access if a new fingerprint is added.

2. **Sealing Secrets with Public Key Encryption**:
   - The public key is accessible without biometric prompts, allowing the application to seal card hints, master tokens, and TOTP seeds at any time.

3. **Hardware-Enforced Decryption**:
   - To decrypt secrets, Android requires an authenticated `BiometricPrompt.CryptoObject` initialized with the KeyStore private key.
   - Decryption can **only** occur in hardware TEE/StrongBox after the user successfully passes biometric verification.
   - Frida, Xposed, or runtime memory hooks cannot bypass this hardware gate: calling `cipher.doFinal()` without genuine biometric authentication fails with `UserNotAuthenticatedException`.

---

## 🔒 RSA-OAEP Migration (Fixing PKCS#1 v1.5 Vulnerabilities)

In **v0.997**, Pin Vault migrated from legacy PKCS#1 v1.5 padding to **OAEP (Optimal Asymmetric Encryption Padding)** with SHA-256:

### Why PKCS#1 v1.5 Was Replaced
- **Padding Oracle Vulnerabilities**: PKCS#1 v1.5 padding is deterministic and vulnerable to chosen-ciphertext padding oracle attacks (such as Bleichenbacher's attack and ROBOT). If an adversary can observe subtle distinctions in decryption errors, they can gradually decrypt ciphertexts without knowing the private key.
- **NIST & Android Security Guidelines**: NIST SP 800-131A and modern Android Security Guidelines mandate deprecating PKCS#1 v1.5 and NoPadding in favor of OAEP.

### The OAEP Implementation
```kotlin
const val RSA_CIPHER_MODE = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
```
- **Digest**: SHA-256 (`KeyProperties.DIGEST_SHA256`).
- **MGF1 Digest**: SHA-1 (standard hardware default for Android KeyStore).
- **Padding**: `KeyProperties.ENCRYPTION_PADDING_RSA_OAEP`.

### Seamless Migration & Backward Compatibility
To prevent decryption errors for users upgrading from earlier releases:
1. `createDecryptionCipher()` intercepts `InvalidKeyException`, safely purging obsolete KeyStore keys and generating a fresh OAEP hardware key.
2. `ensureMasterToken()` tracks `PREF_CIPHER_MODE` (`biometric_cipher_mode`) in SharedPreferences. Upon detecting a cipher transition, it automatically reseals the master token with OAEP.
