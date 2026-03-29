package ltd.evilcorp.nao

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

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
}
