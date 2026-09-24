# Pin Vault

[![Version](https://img.shields.io/badge/Version-0.99%20(Pre--release)-blue.svg)](https://github.com/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-orange.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero--Permission-success.svg)](README.md)
[![Security](https://img.shields.io/badge/Display%20Security-FLAG__SECURE-red.svg)](README.md)

> **Pin Vault** is an open-source, privacy-first Android application that protects debit card, credit card, and banking PINs using **visual steganography**. Instead of displaying your sensitive codes in cleartext, Pin Vault hides them inside randomized grids of colored number tiles that only you know how to decipher.

> **Heritage & Inspiration**: Pin Vault was inspired by an earlier, orphaned Android application named *Pin Keeper*, which stopped working under modern versions of Android. Pin Vault was re-engineered from scratch for contemporary Android devices (Android 8.0+ through Android 15+) using Jetpack Compose, Material 3, and hardware biometric authentication.

---

## 🌟 The Problem & The Solution

### The Shoulder-Surfing Problem
Whether you are standing at an ATM, checking out in a crowded supermarket, or boarding a flight, opening a traditional password manager or notes app reveals your 4-to-6 digit PIN in cleartext. Anyone glancing over your shoulder or reviewing security camera footage can easily steal your PIN.

### The Visual Steganography Solution
Pin Vault replaces plain numbers with a **steganographic matrix**. When you look at the screen, onlookers and cameras see an innocent, colorful grid of random digits. Only you know:
1. **Your Secret Color** (concealed on-screen during normal view)
2. **Your Starting Tile** (e.g. Top-left corner, Row 2 Column 3)
3. **Your Traversal Path** (e.g. Diagonally down, Knight's move, L-shape)

Even if someone stares directly at your phone screen while you read your PIN, they cannot determine which numbers are real and which are decoys.

```
Example 6x6 Matrix View:
┌───┬───┬───┬───┬───┬───┐
│ 4*│ 8 │ 2 │ 7 │ 1 │ 3 │   <-- * Secret Tile 1 (Blue) at (0,0): "4"
├───┼───┼───┼───┼───┼───┤
│ 9 │ 7*│ 5 │ 3 │ 6 │ 2 │   <-- * Secret Tile 2 (Blue) at (1,1): "7"
├───┼───┼───┼───┼───┼───┤
│ 3 │ 1 │ 1*│ 4 │ 8 │ 9 │   <-- * Secret Tile 3 (Blue) at (2,2): "1"
├───┼───┼───┼───┼───┼───┤
│ 6 │ 2 │ 9 │ 9*│ 0 │ 5 │   <-- * Secret Tile 4 (Blue) at (3,3): "9"
├───┼───┼───┼───┼───┼───┤
│ 8 │ 4 │ 6 │ 2 │ 7 │ 1 │   <-- Decoy linear line of uniform color
├───┼───┼───┼───┼───┼───┤
│ 1 │ 5 │ 8 │ 3 │ 4 │ 6 │
└───┴───┴───┴───┴───┴───┘
Deciphered PIN: 4 - 7 - 1 - 9
Observers see: A vibrant matrix of digits with decoy lines in multiple directions.
```

---

## ✨ Key Features

- 🛡️ **Visual Steganography Engine**: Configure custom matrix dimensions (from 4×4 up to 8×8) tailored for cards, doors, safes, and phone banking codes.
- 📐 **Linear Decoy PINs**: The algorithm automatically synthesizes straight lines (horizontal, vertical, diagonal) of identical colors across the matrix to create plausible false trails that mislead onlookers.
- 🧩 **Strict Non-Adjacency Defense**: Decoy tiles sharing your secret color are strategically dispersed so they never touch your genuine PIN tiles, preventing clustering analysis.
- 👁️ **Emergency Biometric Peeker (Hold-to-Reveal)**: Forgot your traversal rule? Press and hold the biometric peeker button and verify with fingerprint, face unlock, or device PIN. While held, your genuine PIN tiles glow with their true color and decoys dim. Releasing immediately re-scrambles and disguises the matrix.
- 🔒 **100% Offline & Zero Network Permissions**: Pin Vault does not declare the `android.permission.INTERNET` permission. No telemetry, no external SDKs, no cloud servers, and no tracking.
- 📦 **Encrypted Vault Migration**: Safely transfer your vault between phones without cloud reliance. Exports are protected with **PBKDF2** (100,000 rounds) and **AES-256-GCM** authenticated encryption into a portable `.pinvault` file.
- 🚫 **Screen Capture Defense (`FLAG_SECURE`)**: The app prevents screenshots, screen recording, and hides window contents when switching tasks in Android Recents.
- 🎨 **Modern Jetpack Compose UI**: Dynamic Material 3 design, custom circular number tokens (`1:1` aspect-ratio locked), search filtering, card categorization, and dark theme support.

---

## 🔐 Cryptography & Security Specifications

| Layer | Specification | Details |
|---|---|---|
| **Network Permission** | None (`0` network sockets) | Fully offline application |
| **Backup Encryption** | AES-256-GCM | 128-bit authentication tag, random 12-byte IV per export |
| **Key Derivation** | PBKDF2 with HMAC-SHA256 | 100,000 iterations with 16-byte cryptographically secure salt |
| **Biometric Auth** | Android BiometricPrompt | Hardware-backed Keymaster / StrongBox TEE integration |
| **Display Protection** | WindowManager `FLAG_SECURE` | Screenshot, screen recording & OS recents card blocking |
| **Local Storage** | Sandboxed Private Storage | Files saved exclusively to `context.filesDir` |

---

## 🚀 Getting Started & Building from Source

### Prerequisites
- **JDK**: Version 17 or higher
- **Android SDK**: Compile SDK 36, Minimum SDK 26 (Android 8.0 Oreo)
- **Gradle**: 8.x / 9.x (Gradle Wrapper included)

### Clone the Repository
```bash
git clone https://github.com/hughk/pin-vault.git
cd pin-vault
```

### Build Debug APK
```bash
# On Linux / macOS:
./gradlew assembleDebug

# On Windows:
.\gradlew.bat assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/PinVault-v0.99-debug.apk
```

### Run Unit Tests
```bash
# On Linux / macOS:
./gradlew testDebugUnitTest

# On Windows:
.\gradlew.bat testDebugUnitTest
```
Pin Vault includes comprehensive unit test suites verifying decoy non-adjacency, linear decoy generation, and cryptographic roundtrips.

---

## 📱 How to Use Pin Vault

1. **Add a Card**:
   - Tap the `+` button on the Vault screen.
   - Enter a card name (e.g. *Barclays Visa Debit*) and choose a category.
   - Select grid dimensions (e.g. *6×7*).
   - Enter your PIN digits and choose your **Secret Color**.
   - Tap the grid tiles to place your PIN in your chosen pattern.
   - Enter a subtle **Rule Hint** (e.g. *Diagonal down-right from top-left*).
   - Tap **Save Card**.
2. **View Your Matrix**:
   - Open your card from the vault list.
   - The matrix renders with circular number tokens. Your secret color is hidden to ensure nobody looking at your screen can identify your digits.
   - Follow your personal mental path to read your PIN.
3. **Emergency Reveal**:
   - If you need immediate confirmation, press and hold the **Press & Hold with Biometric Reveal** button.
   - Authenticate via fingerprint or device credential.
   - Your secret tiles illuminate. Release your finger to immediately re-mask the matrix.
4. **Backup & Transfer**:
   - Tap the **Sync** icon on the Vault screen.
   - Switch to **Export**, choose a passphrase, and share or copy the `.pinvault` payload.
   - On your new device, open Pin Vault -> **Import**, paste the payload, enter the passphrase, and restore.

---

## 🗺️ Roadmap to v1.0

Pin Vault is currently at **Version 0.99 (Beta / Pre-release)**. The core engine, visual steganography, decoy algorithms, and cryptographic export are complete and thoroughly tested.

Before declaring version 1.0 production ready:
- [x] Visual steganography matrix with dynamic rows/columns
- [x] Strict non-adjacency decoy algorithm
- [x] Linear decoy generation (horizontal, vertical, diagonal)
- [x] Emergency biometric hold-to-reveal peeker
- [x] Offline AES-256-GCM + PBKDF2 backup & migration
- [x] Adaptive app icon and screen capture defense
- [x] In-app Help & User Guide
- [x] In-app About dialog with author credits and contact
- [ ] Community feedback on device compatibility and screen densities
- [ ] Additional localization / multi-language support

---

## 👤 Author & Feedback

**Hugh Kennedy**
- 📧 Contact: [hughk.projects@gmail.com](mailto:hughk.projects@gmail.com)
- 🐛 Issues & Feature Requests: Please open an issue on the GitHub repository.

Feedback, bug reports, and suggestions are warmly welcomed!

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see the [LICENSE](LICENSE) file for details.
