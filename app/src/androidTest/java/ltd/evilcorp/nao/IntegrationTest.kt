package ltd.evilcorp.nao

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "nao.json").delete()
    }

    @Test
    fun testOtpAuthIntent() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data =
                Uri.parse("otpauth://totp/Example:user@example.com?secret=AAAAAAAAAAAAAAAA&issuer=Example&period=29")
            setClassName("ltd.evilcorp.nao", "ltd.evilcorp.nao.MainActivity")
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            // Check if the sheet is shown with the correct data.
            composeTestRule.onNodeWithText("Add New TOTP").assertIsDisplayed()
            composeTestRule.onNodeWithText("Example").assertIsDisplayed()

            // Depending on what device the tests run on, some of these may be
            // below the fold and require scrolling before being visible.
            composeTestRule.onNodeWithText("user@example.com").assertExists()
            composeTestRule.onNodeWithText("AAAAAAAAAAAAAAAA").assertExists()
            composeTestRule.onNodeWithText("29").assertExists()

            // Click save.
            composeTestRule.onNodeWithText("Save").performClick()

            // Check if it's added to the list.
            composeTestRule.onNodeWithText("Example").assertIsDisplayed()
            composeTestRule.onNodeWithText("user@example.com").assertIsDisplayed()
        }
    }

    @Test
    fun testManualOtpAuthInput() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Click the add button.
            composeTestRule.onNodeWithContentDescription("Add").performClick()

            // Check if the sheet is shown.
            composeTestRule.onNodeWithText("Add New TOTP").assertIsDisplayed()

            // Fill in the data.
            composeTestRule.onNodeWithText("Name").performTextInput("something something")
            composeTestRule.onNodeWithText("Extra Info").performTextInput("robin@example.com")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("ABCDEFGHIJKLMNOP")

            // Period is almost always 30, so it's hidden behind the 'advanced' option.
            composeTestRule.onNodeWithText("Show advanced").performClick()
            composeTestRule.onNodeWithText("Period (seconds)").performTextReplacement("60")

            // Click save.
            composeTestRule.onNodeWithText("Save").performClick()

            // Check if it's added to the list.
            composeTestRule.onNodeWithText("something something").assertIsDisplayed()
            composeTestRule.onNodeWithText("robin@example.com").assertIsDisplayed()
        }
    }

    @Test
    fun testDisallowDuplicateEntry() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Add an entry.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("Duplicate")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("ABCDEFGHIJKLMNOP")
            composeTestRule.onNodeWithText("Save").performClick()

            // Try to add the same entry again.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("Duplicate")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("ABCDEFGHIJKLMNOP")

            // Swipe the BottomSheet up to expand it, or the Save-button might not be in view.
            composeTestRule.onNodeWithText("Name").onParent().performTouchInput {
                swipeUp()
            }

            // The Save-button should now be disabled and say "Already added".
            composeTestRule.onNodeWithText("Save").assertDoesNotExist()
            composeTestRule.onNodeWithText("Already added").assertIsDisplayed()
            composeTestRule.onNodeWithText("Already added").assertIsNotEnabled()
        }
    }

    @Test
    fun testExport() {
        Intents.init()
        try {
            val label = "ExportTest"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("otpauth://totp/$label:user@example.com?secret=AAAAAAAAAAAAAAAA")
                setClassName("ltd.evilcorp.nao", "ltd.evilcorp.nao.MainActivity")
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val exportFile = File(context.cacheDir, "exported.json")
            if (exportFile.exists()) exportFile.delete()
            val exportUri = Uri.fromFile(exportFile)

            val resultData = Intent().apply {
                data = exportUri
            }
            val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)

            ActivityScenario.launch<MainActivity>(intent).use {
                // Add the entry.
                composeTestRule.onNodeWithText("Save").performClick()
                composeTestRule.onNodeWithText(label).assertIsDisplayed()

                // Open menu.
                composeTestRule.onNodeWithContentDescription("More").performClick()
                // Click Export.
                composeTestRule.onNodeWithText("Export JSON").performClick()

                // Wait for success snackbar.
                composeTestRule.waitUntil(5000) {
                    composeTestRule
                        .onAllNodesWithText("Exported successfully")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }

                // Verify file content.
                val content = exportFile.readText()
                assertTrue(content.contains(label))
                assertTrue(content.contains("AAAAAAAAAAAAAAAA"))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testImport() {
        Intents.init()
        try {
            val label = "ImportTestItem"
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val importFile = File(context.cacheDir, "to_import.json")
            val json =
                """
                [
                  {
                    "name": "$label",
                    "extraInfo": "imported@example.com",
                    "secret": "AAAAAAAAAAAAAAAA",
                    "periodSeconds": 30,
                    "digest": "sha1"
                  }
                ]
                """.trimIndent()
            importFile.writeText(json)
            val importUri = Uri.fromFile(importFile)

            val resultData = Intent().apply {
                data = importUri
            }
            val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)

            ActivityScenario.launch(MainActivity::class.java).use {
                // Open menu.
                composeTestRule.onNodeWithContentDescription("More").performClick()
                // Click Import.
                composeTestRule.onNodeWithText("Import JSON").performClick()

                // Wait for success snackbar.
                composeTestRule.waitUntil(5000) {
                    composeTestRule
                        .onAllNodesWithText("Imported 1 new items")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }

                // Verify the item is in the list.
                composeTestRule.onNodeWithText(label).assertIsDisplayed()
                composeTestRule.onNodeWithText("imported@example.com").assertIsDisplayed()
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testImportDuplicates() {
        Intents.init()
        try {
            val label = "DuplicateItem"
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val importFile = File(context.cacheDir, "duplicates.json")
            val json =
                """
                [
                  {
                    "name": "$label",
                    "extraInfo": "user@example.com",
                    "secret": "AAAAAAAAAAAAAAAA",
                    "periodSeconds": 30,
                    "digest": "sha1"
                  }
                ]
                """.trimIndent()
            importFile.writeText(json)
            val importUri = Uri.fromFile(importFile)

            val resultData = Intent().apply { data = importUri }
            val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)

            ActivityScenario.launch(MainActivity::class.java).use {
                // Add the item manually first.
                composeTestRule.onNodeWithContentDescription("Add").performClick()
                composeTestRule.onNodeWithText("Name").performTextInput(label)
                composeTestRule.onNodeWithText("Extra Info").performTextInput("user@example.com")
                composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("AAAAAAAAAAAAAAAA")
                composeTestRule.onNodeWithText("Save").performClick()

                // Now import the same item.
                composeTestRule.onNodeWithContentDescription("More").performClick()
                composeTestRule.onNodeWithText("Import JSON").performClick()

                // Verify snackbar says 0 new items.
                composeTestRule.waitUntil(5000) {
                    composeTestRule
                        .onAllNodesWithText("Imported 0 new items")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testImportInvalidJson() {
        Intents.init()
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val importFile = File(context.cacheDir, "invalid.json")
            importFile.writeText("This is not JSON")
            val importUri = Uri.fromFile(importFile)

            val resultData = Intent().apply { data = importUri }
            val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)

            ActivityScenario.launch(MainActivity::class.java).use {
                // Open menu.
                composeTestRule.onNodeWithContentDescription("More").performClick()
                // Click Import.
                composeTestRule.onNodeWithText("Import JSON").performClick()

                // Wait for failure snackbar.
                composeTestRule.waitUntil(5000) {
                    composeTestRule
                        .onAllNodesWithText("Import failed")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testStatePersistence() {
        val label = "PersistenceTest"
        val user = "bee@bbbthats3bees.be"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("otpauth://totp/$label:$user?secret=BBBBBBBBBBBBBBBB&issuer=$label")
            setClassName("ltd.evilcorp.nao", "ltd.evilcorp.nao.MainActivity")
        }

        // Add the TOTP entry.
        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.onNodeWithText("Save").performClick()
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }

        // Re-open the app, and check that entry is still there.
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
            composeTestRule.onNodeWithText(user).assertIsDisplayed()
        }
    }

    @Test
    fun testDelete() {
        val label = "DeleteMe"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("otpauth://totp/$label:user@example.com?secret=AAAAAAAAAAAAAAAA")
            setClassName("ltd.evilcorp.nao", "ltd.evilcorp.nao.MainActivity")
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            // Add the entry first.
            composeTestRule.onNodeWithText("Save").performClick()
            composeTestRule.onNodeWithText(label).assertIsDisplayed()

            // Long-press the entry to open the actions sheet.
            composeTestRule.onNodeWithText(label).performTouchInput {
                longClick()
            }

            // Click "Delete" in the actions sheet.
            composeTestRule.onNodeWithText("Delete").performClick()

            // Confirm deletion in the dialog.
            // There are now two "Delete" texts on screen (sheet + dialog).
            // The one in the dialog is the one we want.
            composeTestRule.onAllNodesWithText("Delete").onLast().performClick()

            // Verify the entry is gone.
            composeTestRule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test
    fun testEditEntry() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Add an entry.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("Original Name")
            composeTestRule.onNodeWithText("Extra Info").performTextInput("original@info.com")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("AAAAAAAAAAAAAAAA")
            composeTestRule.onNodeWithText("Save").performClick()

            // Long-press to edit.
            composeTestRule.onNodeWithText("Original Name").performTouchInput { longClick() }
            composeTestRule.onNodeWithText("Edit").performClick()

            // Check if sheet shows "Edit TOTP".
            composeTestRule.onNodeWithText("Edit TOTP").assertIsDisplayed()

            // Modify values. Disambiguate from the list by targeting the editable field.
            composeTestRule.onNode(hasText("Original Name") and hasSetTextAction()).performTextReplacement("Updated Name")
            composeTestRule.onNode(hasText("original@info.com") and hasSetTextAction()).performTextReplacement("updated@info.com")

            // Save changes.
            composeTestRule.onNodeWithText("Save").performClick()

            // Verify updates.
            composeTestRule.onNodeWithText("Updated Name").assertIsDisplayed()
            composeTestRule.onNodeWithText("updated@info.com").assertIsDisplayed()
            composeTestRule.onNodeWithText("Original Name").assertDoesNotExist()
        }
    }

    @Test
    fun testEditDuplicateEntry() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Add first entry.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("Entry 1")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("AAAAAAAAAAAAAAAA")
            composeTestRule.onNodeWithText("Save").performClick()

            // Add second entry.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("Entry 2")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("BBBBBBBBBBBBBBBB")
            composeTestRule.onNodeWithText("Save").performClick()

            // Edit Entry 2 to match Entry 1.
            composeTestRule.onNodeWithText("Entry 2").performTouchInput { longClick() }
            composeTestRule.onNodeWithText("Edit").performClick()

            composeTestRule.onNode(hasText("Entry 2") and hasSetTextAction()).performTextReplacement("Entry 1")
            composeTestRule.onNode(hasText("BBBBBBBBBBBBBBBB") and hasSetTextAction()).performTextReplacement("AAAAAAAAAAAAAAAA")

            // Swipe up to see the button if needed.
            composeTestRule.onNodeWithText("Name").onParent().performTouchInput {
                swipeUp()
            }

            // Verify duplicate error.
            composeTestRule.onNodeWithText("Already added").assertIsDisplayed()
            composeTestRule.onNodeWithText("Already added").assertIsNotEnabled()
        }
    }

    @Test
    fun testEditNoChangesDisallowed() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Add an entry.
            composeTestRule.onNodeWithContentDescription("Add").performClick()
            composeTestRule.onNodeWithText("Name").performTextInput("No Change")
            composeTestRule.onNodeWithText("Secret (Base32)").performTextInput("AAAAAAAAAAAAAAAA")
            composeTestRule.onNodeWithText("Save").performClick()

            // Edit it but change nothing.
            composeTestRule.onNodeWithText("No Change").performTouchInput { longClick() }
            composeTestRule.onNodeWithText("Edit").performClick()

            // Swipe up to see the button.
            composeTestRule.onNodeWithText("Name").onParent().performTouchInput {
                swipeUp()
            }

            // Verify that we can't save the entry with no changes made.
            composeTestRule.onNodeWithText("Already added").assertIsDisplayed()
            composeTestRule.onNodeWithText("Already added").assertIsNotEnabled()
        }
    }
}
