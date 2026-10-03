# Dynamic TOTP Authenticator

Starting in **v0.997**, Pin Vault integrates a full **Time-Based One-Time Password (TOTP)** authenticator compliant with **RFC 6238**. 

Unlike standard authenticator apps (Google Authenticator, Microsoft Authenticator) that display rolling 6-digit codes in clear text on screen, Pin Vault **camouflages your rolling 2FA codes into your steganographic matrix**.

---

## ⏱️ How It Works

1. **Secret Key Ingestion**:
   - Scan standard 2FA QR codes (`otpauth://totp/...`) using the integrated CameraX scanner.
   - Or paste a Base32 secret key manually into the editor.

2. **Real-Time Rolling Computation**:
   - The app derives the standard 6-digit passcode every 30 seconds using HMAC-SHA1 (or SHA256/SHA512).
   - The computed digits are continuously injected into your designated matrix path in real time.

3. **Live Progress Indicator**:
   - A circular progress ring and countdown timer indicate remaining validity seconds for the current window.
   - When the 30-second epoch expires, the 6 digits instantly change on your secret path, while remaining camouflaged within the matrix.

---

## 🛡️ Security Advantages

- **Zero Cleartext Exposure**: Even if someone takes a picture of your phone screen while you are logging into a bank or email service, they only see a matrix of numbers—not which 6 digits represent your current 2FA token.
- **Hardware-Sealed TOTP Seeds**: The TOTP secret key is sealed using hardware-backed Android KeyStore RSA-OAEP encryption.
- **Offline & Zero-Permission**: No internet access is required or permitted. Time synchronization relies on the device's secure system clock.
