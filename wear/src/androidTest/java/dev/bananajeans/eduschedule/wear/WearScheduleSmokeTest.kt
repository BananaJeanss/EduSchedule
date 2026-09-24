package dev.bananajeans.eduschedule.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearScheduleSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<WearActivity>()

    @Test fun unpairedWatchExplainsPhoneSetup() {
        rule.onNodeWithText("Set up EduSchedule on your phone.").assertIsDisplayed()
        rule.onNodeWithText("Sync now").assertIsDisplayed()
    }
}
