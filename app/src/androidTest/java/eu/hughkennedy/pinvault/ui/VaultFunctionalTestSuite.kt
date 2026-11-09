package eu.hughkennedy.pinvault.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.hughkennedy.pinvault.MainActivity
import eu.hughkennedy.pinvault.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultFunctionalTestSuite {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testVaultScreenDisplaysHeaderAndCards() {
        val context = composeTestRule.activity
        val vaultTitle = context.getString(R.string.vault_title)
        val addCardDesc = context.getString(R.string.action_add_card)

        // Verify Vault title and Add Card FAB are displayed
        composeTestRule.onNodeWithText(vaultTitle).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addCardDesc).assertIsDisplayed()
    }

    @Test
    fun testCategoryFilterInteraction() {
        val context = composeTestRule.activity
        val creditCategory = context.getString(R.string.category_credit)

        // Find and click Credit Card filter chip (matching the exact chip label with count)
        composeTestRule.onNode(hasText("$creditCategory (", substring = true) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        // Reset filter back to All
        composeTestRule.onNode(hasText("All (", substring = true) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSearchFilterFunctionality() {
        val context = composeTestRule.activity
        val searchPlaceholder = context.getString(R.string.search_placeholder)

        // Click search bar and input query
        val searchNode = composeTestRule.onNodeWithText(searchPlaceholder)
        searchNode.performTextInput("Barclays")
        composeTestRule.waitForIdle()

        // Verify Barclays is visible
        composeTestRule.onNodeWithText("Barclays Visa Debit").assertIsDisplayed()

        // Input non-existent query
        composeTestRule.onNodeWithText("Barclays").performTextReplacement("NonExistentXYZ")
        composeTestRule.waitForIdle()

        // Verify empty state is displayed
        val emptyMsg = context.getString(R.string.vault_empty_no_search, "NonExistentXYZ")
        composeTestRule.onNodeWithText(emptyMsg).assertIsDisplayed()

        // Clear search
        val clearSearchDesc = context.getString(R.string.search_clear)
        composeTestRule.onNodeWithContentDescription(clearSearchDesc).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testNavigateToDetailScreenAndBack() {
        val context = composeTestRule.activity
        val detailSubtitle = context.getString(R.string.detail_steganographic_camouflage)
        val backDesc = context.getString(R.string.action_back)

        // Click on Barclays Visa Debit card
        composeTestRule.onNodeWithText("Barclays Visa Debit").performClick()
        composeTestRule.waitForIdle()

        // Verify detail screen opened and rule hint is concealed by default
        composeTestRule.onNodeWithText(detailSubtitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.detail_rule_default_concealed)).assertIsDisplayed()

        // Press back navigation icon to return to Vault
        composeTestRule.onNodeWithContentDescription(backDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify back on Vault screen
        composeTestRule.onNodeWithText(context.getString(R.string.vault_title)).assertIsDisplayed()
    }

    @Test
    fun testMasterPinDialogOpenAndCancel() {
        val context = composeTestRule.activity
        val moreOptionsDesc = context.getString(R.string.action_more_options)
        val masterPinMenuText = context.getString(R.string.action_set_master_pin_menu)
        val cancelText = context.getString(R.string.action_cancel)

        // Open top bar overflow menu
        composeTestRule.onNodeWithContentDescription(moreOptionsDesc).performClick()
        composeTestRule.waitForIdle()

        // Click Set / Change Master PIN
        composeTestRule.onNodeWithText(masterPinMenuText).performClick()
        composeTestRule.waitForIdle()

        // Verify Master PIN dialog is displayed and dismiss it
        composeTestRule.onNodeWithText(context.getString(R.string.master_pin_new_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testNavigateToEditorAndCancel() {
        val context = composeTestRule.activity
        val addCardDesc = context.getString(R.string.action_add_card)
        val editorCreateTitle = context.getString(R.string.editor_title_create)
        val cancelDesc = context.getString(R.string.action_cancel)

        // Click Add Card FAB
        composeTestRule.onNodeWithContentDescription(addCardDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify Editor screen displayed
        composeTestRule.onNodeWithText(editorCreateTitle).assertIsDisplayed()

        // Click Cancel / Back
        composeTestRule.onNodeWithContentDescription(cancelDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify back on Vault screen
        composeTestRule.onNodeWithText(context.getString(R.string.vault_title)).assertIsDisplayed()
    }

    @Test
    fun testHelpScreenNavigation() {
        val context = composeTestRule.activity
        val helpDesc = context.getString(R.string.action_help_guide_tooltip)
        val backDesc = context.getString(R.string.action_back)

        // Click Help icon in top bar
        composeTestRule.onNodeWithContentDescription(helpDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify Help screen
        composeTestRule.onNodeWithText("Help & Guide", substring = true).assertIsDisplayed()

        // Navigate back to Vault via top bar Back button
        composeTestRule.onNodeWithContentDescription(backDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify Vault title is displayed
        composeTestRule.onNodeWithText(context.getString(R.string.vault_title)).assertIsDisplayed()
    }

    @Test
    fun testCreateNewCardSaveAndVerify() {
        val context = composeTestRule.activity
        val addCardDesc = context.getString(R.string.action_add_card)
        val nameLabel = context.getString(R.string.editor_field_name_label)
        val saveDesc = context.getString(R.string.action_save)
        val backDesc = context.getString(R.string.action_back)
        val testCardName = "Automated Test PIN"

        // Open Editor via FAB
        composeTestRule.onNodeWithContentDescription(addCardDesc).performClick()
        composeTestRule.waitForIdle()

        // Enter Card Name
        composeTestRule.onNodeWithText(nameLabel).performTextInput(testCardName)
        composeTestRule.waitForIdle()

        // Click Save
        composeTestRule.onNodeWithContentDescription(saveDesc).performClick()
        composeTestRule.waitForIdle()

        // Handle random digits consent dialog if prompted
        val fillConfirmText = context.getString(R.string.editor_fill_digits_confirm)
        if (composeTestRule.onAllNodes(hasText(fillConfirmText)).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText(fillConfirmText).performClick()
            composeTestRule.waitForIdle()
        }

        // Verify detail screen displays the new card title
        composeTestRule.onNodeWithText(testCardName).assertIsDisplayed()

        // Navigate back to Vault
        composeTestRule.onNodeWithContentDescription(backDesc).performClick()
        composeTestRule.waitForIdle()

        // Filter or search for newly created card to ensure it's displayed in Vault list
        val searchPlaceholder = context.getString(R.string.search_placeholder)
        composeTestRule.onNodeWithText(searchPlaceholder).performTextInput("Automated")
        composeTestRule.waitForIdle()

        // Verify new card is displayed in Vault list
        composeTestRule.onNodeWithText(testCardName).assertIsDisplayed()
    }

    @Test
    fun testDeleteCardWithConfirmation() {
        val context = composeTestRule.activity
        val deleteMatrixDesc = context.getString(R.string.action_delete_matrix)
        val confirmDeleteText = context.getString(R.string.action_delete)
        val cancelText = context.getString(R.string.action_cancel)
        val targetCardName = "Automated Test PIN"

        // Check if our test card is present, otherwise click on Barclays
        val cardName = if (composeTestRule.onAllNodes(hasText(targetCardName)).fetchSemanticsNodes().isNotEmpty()) {
            targetCardName
        } else {
            "Amex Gold Corporate"
        }

        // Open Detail screen
        composeTestRule.onNodeWithText(cardName).performClick()
        composeTestRule.waitForIdle()

        // Click Delete icon in top bar
        composeTestRule.onNodeWithContentDescription(deleteMatrixDesc).performClick()
        composeTestRule.waitForIdle()

        // Verify delete confirmation dialog appears, dismiss with Cancel first to verify safety
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()

        // Card should still be displayed on detail screen
        composeTestRule.onNodeWithText(cardName).assertIsDisplayed()

        // Re-open Delete dialog and confirm deletion
        composeTestRule.onNodeWithContentDescription(deleteMatrixDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(confirmDeleteText).performClick()
        composeTestRule.waitForIdle()

        // Verify user is back on Vault screen
        composeTestRule.onNodeWithText(context.getString(R.string.vault_title)).assertIsDisplayed()
    }
}
