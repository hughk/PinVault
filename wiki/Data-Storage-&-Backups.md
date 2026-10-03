# Data Storage & Backups

Pin Vault is built around a zero-trust, offline-first data persistence model.

---

## 🗄️ Local Database Architecture

- **SQLite & Room ORM**: All cards, categories, folders, and grid configurations are stored in an encrypted local database.
- **Zero Cloud Footprint**: The application does not include any third-party analytics, crash trackers, or cloud sync services.
- **Auto-Lock on Backgrounding**: Whenever the user navigates away from the app or locks the device, the UI immediately locks and clears unsealed secrets from memory.

---

## 💾 Encrypted Backup & Restore

Pin Vault allows users to safely backup and restore their vault across devices without relying on cloud services:

1. **AES-256-GCM Encryption**:
   - Backups are serialized into JSON and encrypted using AES-GCM with a 256-bit key.
   - The key is derived from a user-supplied master passphrase using **PBKDF2 with SHA-256** and high iteration counts.
   - Includes authenticated data tags to prevent ciphertext tampering.

2. **Android Storage Access Framework (SAF)**:
   - Backups are written directly to a location chosen by the user (USB drive, local downloads, or private SD card) via Android's file picker.
   - Pin Vault never requests broad storage permissions (`MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`).

3. **Android Backup Service Disabled**:
   - `android:allowBackup="false"` is explicitly set in `AndroidManifest.xml` to prevent unencrypted ADB or Google Cloud backup extraction.
