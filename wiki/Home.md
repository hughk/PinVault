# Welcome to the Pin Vault Wiki

**Pin Vault** is an open-source, privacy-first Android application engineered to safeguard debit card, credit card, and banking PINs using **visual steganography**. Instead of displaying sensitive codes in plaintext, Pin Vault hides them inside randomized grids of colored number tiles that only you know how to decipher.

---

## 📚 Wiki Navigation

- **[[Home]]**: Overview, architecture, and core philosophy.
- **[[Visual-Steganography-Engine]]**: How matrix camouflage, linear decoy generation, and color trails work.
- **[[Artist-Fingerpaint-Mode]]**: Creating custom PIN paths using fluid touch gestures with numbered directional chevrons.
- **[[TOTP-Authenticator]]**: Scanning 2FA QR codes and embedding rolling 6-digit security codes into steganographic grids.
- **[[Hardware-Biometric-Security-&-OAEP]]**: Hardware KeyStore RSA encryption, biometric authentication, and OAEP-SHA256 security.
- **[[Data-Storage-&-Backups]]**: Offline-only SQLite database, zero-permission policy, and AES-GCM encrypted JSON backups.

---

## 🛡️ Core Philosophy: Zero Cleartext & Shoulder-Surf Shielding

Conventional password managers and digital wallets display PIN numbers directly on screen upon authentication. In public environments—such as retail checkout counters, ATMs, transit stations, and crowded offices—this exposes your secret codes to:
1. **Shoulder Surfing**: Observers standing nearby looking over your shoulder.
2. **CCTV & Security Cameras**: High-resolution surveillance cameras positioned overhead.
3. **Screen Grabbers / Spyware**: Malicious background processes attempting display scraping.

Pin Vault eliminates these attack vectors:
- **Visual Camouflage**: Even when the vault is unlocked, the PIN is never spelled out as a contiguous string. Observers see a colorful grid filled with randomized numbers and decoy lines.
- **Android Display Shielding**: `FLAG_SECURE` is active throughout the app, blocking Android OS screenshots, recent apps switcher thumbnails, and untrusted display mirroring.
- **Zero Internet Permissions**: Pin Vault requests zero network permissions (`android.permission.INTERNET` is absent from `AndroidManifest.xml`). Data never leaves your physical device.

---

## 🚀 Version 0.997 Release Highlights

The current **v0.997 (Beta)** release introduces major architectural enhancements:
- 👆 **Artist Fingerpaint Mode**: Draw custom geometric shapes across the matrix with directional numbered chevrons.
- ⏱️ **Dynamic TOTP Authenticator**: Scan 2FA QR codes to camouflage live rolling 6-digit one-time passcodes.
- 🔐 **RSA-OAEP Hardware Encryption**: Upgraded cryptographic sealing to `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` in the Android KeyStore / TEE, eliminating chosen-ciphertext padding oracle vulnerabilities.
