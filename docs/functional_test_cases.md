# Pin Vault - Functional Test Specifications

This document defines the functional end-to-end and integration test specifications for the Pin Vault Android application. All test cases are automated and executed on-device / on-emulator using the AndroidX JUnit4 test runner and Jetpack Compose Testing APIs in [`VaultFunctionalTestSuite.kt`](file:///c:/Users/hughk/SynologyDrive/Projects/Pin%20Keeper/app/src/androidTest/java/eu/hughkennedy/pinvault/ui/VaultFunctionalTestSuite.kt).

---

## Test Suite Overview

* **Test Suite Class:** `eu.hughkennedy.pinvault.ui.VaultFunctionalTestSuite`
* **Test Framework:** Jetpack Compose UI Test (`createAndroidComposeRule<MainActivity>()`), JUnit 4, AndroidX Test Runner
* **Execution Platform:** Android Emulator (`Pixel_9_Pro`, x86_64, API 35) or physical Android 8.0+ device
* **Target Package:** `eu.hughkennedy.pinvault`
* **Test Package:** `eu.hughkennedy.pinvault.test`

---

## Detailed Test Case Specifications

### TC-FNC-001: Vault Screen Header & Initial State Verification
* **Test Method:** `testVaultScreenDisplaysHeaderAndCards()`
* **Target Component:** `VaultScreen`, `PinVaultAppRoot`, `MainActivity`
* **Objective:** Verify that on application launch into an unlocked state, the Vault screen correctly renders the primary application title, the stored cards summary, the card matrix list, and the Floating Action Button (FAB) to add a new PIN card.
* **Pre-conditions:**
  * Application launched in unlocked state (`isLocked = false`).
  * Default database populated with sample card entries.
* **Test Steps:**
  1. Initialize `MainActivity` via `createAndroidComposeRule`.
  2. Resolve string resource `R.string.vault_title` ("Pin Vault") and `R.string.action_add_card` ("Add Card").
  3. Query semantics tree for node with text matching `vault_title`.
  4. Query semantics tree for node with content description matching `action_add_card`.
* **Expected Result:**
  * Both the `Pin Vault` title and the `Add Card` FAB are visible and displayed within the screen viewport.
* **Component Coverage:** TopAppBar title layout, FloatingActionButton rendering, Localized string resolution.

---

### TC-FNC-002: Category Filter Interaction & State Toggling
* **Test Method:** `testCategoryFilterInteraction()`
* **Target Component:** `VaultScreen`, `FilterChip`, `VaultFilter`
* **Objective:** Ensure that tapping category filter chips updates the filtered card list, applies selection state visual semantics, and allows resetting back to the "All" filter state without data corruption.
* **Pre-conditions:**
  * Vault screen displayed with default sample cards across multiple categories (`Credit`, `Bank`, `Crypto`, `Authenticator`).
* **Test Steps:**
  1. Resolve string resource for Credit Card category (`R.string.category_credit`).
  2. Locate the clickable filter chip matching `Credit Card (` with substring matching.
  3. Perform click action on the Credit Card filter chip.
  4. Wait for Compose UI idle state.
  5. Locate the "All (" filter chip and perform click action.
  6. Wait for Compose UI idle state.
* **Expected Result:**
  * Filter chips receive click events without exception.
  * The filtered card state transitions cleanly between category-filtered and all-cards view.
* **Component Coverage:** FlowRow horizontal chip group, StateFlow reactive card filtering, Chip selection semantics.

---

### TC-FNC-003: Search Filter & Empty Search Result Recovery
* **Test Method:** `testSearchFilterFunctionality()`
* **Target Component:** `VaultScreen`, `OutlinedTextField`, Search Query StateFlow
* **Objective:** Verify that real-time text input in the search field dynamically filters cards matching card names, accounts, or rules, displays a dedicated empty-state indicator when no matches exist, and properly clears filters when requested.
* **Pre-conditions:**
  * Vault screen open with sample cards (including "Barclays Visa Debit").
* **Test Steps:**
  1. Locate the search field using `R.string.search_placeholder` ("Search card, folder, account, or rule...").
  2. Inject text `"Barclays"` into the search text field.
  3. Wait for Compose UI idle state.
  4. Assert that "Barclays Visa Debit" is displayed.
  5. Replace text with a non-existent search query `"NonExistentXYZ"`.
  6. Wait for Compose UI idle state.
  7. Assert that the empty state text `R.string.vault_empty_no_search` ("No cards match \"NonExistentXYZ\"") is displayed.
  8. Click the clear search icon (`R.string.search_clear`).
  9. Wait for Compose UI idle state.
* **Expected Result:**
  * Searching for valid terms isolates the target card.
  * Searching for non-existent terms hides all cards and presents the empty state message.
  * Clearing the search query restores the full card listing.
* **Component Coverage:** Reactive search query binding, LazyColumn empty state, Text replacement semantics, Trailing clear icon action.

---

### TC-FNC-004: Matrix Detail Navigation & Back Stack Integrity
* **Test Method:** `testNavigateToDetailScreenAndBack()`
* **Target Component:** `MatrixDetailScreen`, `VaultScreen`, Navigation State
* **Objective:** Validate navigation from the Vault list into a card's Steganographic Camouflage Detail screen and return navigation back to the Vault screen using the TopAppBar back action.
* **Pre-conditions:**
  * Vault list populated with sample card "Barclays Visa Debit".
* **Test Steps:**
  1. Tap the card item with text `"Barclays Visa Debit"`.
  2. Wait for Compose UI idle state.
  3. Verify that the Detail screen header with subtitle `R.string.detail_steganographic_camouflage` ("Steganographic Camouflage") is displayed.
  4. Tap the TopAppBar back navigation icon (`R.string.action_back`).
  5. Wait for Compose UI idle state.
  6. Verify that the user has returned to the Vault screen (`R.string.vault_title`).
* **Expected Result:**
  * Card selection seamlessly navigates to `AppScreen.Detail`.
  * TopAppBar back navigation returns to `AppScreen.Vault` without finishing the parent Activity.
* **Component Coverage:** `CardItemRow` click event, `AppScreen.Detail` screen state transition, TopAppBar back navigation icon handling.

---

### TC-FNC-005: Help & Guide Screen Navigation
* **Test Method:** `testHelpScreenNavigation()`
* **Target Component:** `HelpScreen`, `VaultScreen`, TopAppBar Actions
* **Objective:** Verify that the user can open the in-app user guide and steganography explanation screen and return safely to the vault.
* **Pre-conditions:**
  * User is on the main Vault screen.
* **Test Steps:**
  1. Locate the TopAppBar Help icon using content description `R.string.action_help_guide_tooltip` ("Help & Guide").
  2. Perform click action on the Help icon.
  3. Wait for Compose UI idle state.
  4. Assert that the Help screen title containing "Help & Guide" is displayed.
  5. Click the TopAppBar back navigation icon (`R.string.action_back`).
  6. Wait for Compose UI idle state.
  7. Assert that the Vault screen title is displayed.
* **Expected Result:**
  * Screen transitions into `AppScreen.Help` and displays guide content.
  * Pressing back navigates back to `AppScreen.Vault`.
* **Component Coverage:** `TopAppBar` action icons, `AppScreen.Help` composable rendering, Back stack navigation.

---

### TC-FNC-006: Card Editor Dismissal / Cancellation Flow
* **Test Method:** `testNavigateToEditorAndCancel()`
* **Target Component:** `MatrixEditorScreen`, FloatingActionButton, Cancel Action
* **Objective:** Ensure opening the Card Editor to create a new card and dismissing it via the Cancel/Back button leaves the database unmodified and returns cleanly to the Vault screen.
* **Pre-conditions:**
  * Vault screen open.
* **Test Steps:**
  1. Click the Add Card FAB (`R.string.action_add_card`).
  2. Wait for Compose UI idle state.
  3. Verify that the Editor screen title `R.string.editor_title_create` ("Create PIN Matrix") is displayed.
  4. Click the TopAppBar Cancel navigation button (`R.string.action_cancel`).
  5. Wait for Compose UI idle state.
  6. Verify that the screen returns to `AppScreen.Vault` without creating phantom cards.
* **Expected Result:**
  * The Editor screen initializes in creation mode (`isNew = true`).
  * Cancel action immediately discards unsaved changes and returns to the previous screen.
* **Component Coverage:** FAB trigger, `MatrixEditorScreen` lifecycle, Cancel button event propagation.

---

### TC-FNC-007: End-to-End Card Creation with Hardware KeyStore Encryption
* **Test Method:** `testCreateNewCardSaveAndVerify()`
* **Target Component:** `MatrixEditorScreen`, `VaultRepository`, `VaultStorageEncryption`, `DecoyRandomizer`, `MatrixDetailScreen`
* **Objective:** Execute full end-to-end PIN card authoring: opening editor, entering card name, saving with automatic decoy fill, persisting to hardware KeyStore encrypted JSON storage (`vault_cards.json`), transitioning to Detail view, and confirming presence in the Vault card list.
* **Pre-conditions:**
  * Vault screen open.
  * Android KeyStore available (or test key provider active).
* **Test Steps:**
  1. Click Add Card FAB (`R.string.action_add_card`).
  2. Locate the card name text field (`R.string.editor_field_name_label`).
  3. Inject text `"Automated Test PIN"`.
  4. Click the TopAppBar Save icon (`R.string.action_save`).
  5. If the unassigned random digits confirmation dialog appears (`R.string.editor_fill_digits_confirm`), confirm the dialog.
  6. Wait for Compose UI idle state.
  7. Assert that the Detail screen is displayed with `"Automated Test PIN"` in the header.
  8. Click the back button (`R.string.action_back`).
  9. In the Vault search field, input `"Automated"`.
  10. Wait for Compose UI idle state.
  11. Assert that `"Automated Test PIN"` is present and displayed in the filtered Vault list.
* **Expected Result:**
  * The card is serialized and securely encrypted via AES-256-GCM.
  * Hardware KeyStore successfully generates a cryptographically random IV.
  * The newly created card persists across navigation transitions and is verifiable in both Detail and Vault screens.
* **Component Coverage:** `OutlinedTextField` input injection, `DecoyRandomizer` matrix generation, `VaultStorageEncryption.encrypt()` with KeyStore-generated IV, `VaultRepository.addCard()`, Reactive `StateFlow<List<CardEntity>>`, Viewport search scrolling.

---

### TC-FNC-008: Card Deletion with Dialog Confirmation & Reversion
* **Test Method:** `testDeleteCardWithConfirmation()`
* **Target Component:** `MatrixDetailScreen`, `AlertDialog`, `VaultRepository`
* **Objective:** Verify the safe deletion flow: opening the deletion confirmation dialog, ensuring cancellation preserves the card, and confirming deletion permanently purges the card from the encrypted database and returns to the Vault.
* **Pre-conditions:**
  * At least one card exists in the vault.
* **Test Steps:**
  1. Select a target card ("Automated Test PIN" if present, otherwise "Amex Gold Corporate").
  2. Tap the card to open `MatrixDetailScreen`.
  3. Tap the Delete Matrix icon (`R.string.action_delete_matrix`).
  4. Verify the deletion confirmation alert dialog appears.
  5. Tap the Cancel button (`R.string.action_cancel`) in the dialog.
  6. Verify the card remains open on the Detail screen.
  7. Tap the Delete Matrix icon again.
  8. Tap the Confirm Delete button (`R.string.action_delete`).
  9. Wait for Compose UI idle state.
  10. Assert that the user is returned to the Vault screen (`R.string.vault_title`).
* **Expected Result:**
  * The deletion dialog prevents accidental data loss when Cancel is selected.
  * Confirming deletion updates `VaultRepository`, re-encrypts the reduced list into storage, and returns to `VaultScreen`.
* **Component Coverage:** `MatrixDetailScreen` options, Safety confirmation dialog, `VaultRepository.deleteCard()`, Atomic database re-encryption.
