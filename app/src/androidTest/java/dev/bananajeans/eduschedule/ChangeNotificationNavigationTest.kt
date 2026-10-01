package dev.bananajeans.eduschedule

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ChangeNotificationNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun coldWarmAndRecreatedNotificationsOpenComparisonAndBackClosesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Preferences(context)
        val host = prefs.host
        val home = prefs.home
        val hidden = prefs.hiddenGroups
        val week = prefs.cycleWeek
        val language = prefs.language
        try {
            prefs.host = "example.edupage.org"
            prefs.home = "test-class"
            prefs.language = AppLanguage.ENGLISH
            fun notification() = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CHANGE_REPORT_ID, UUID.randomUUID().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ActivityScenario.launch<MainActivity>(notification()).use { scenario ->
                compose.onNodeWithText("Timetable changes").assertIsDisplayed()
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("This comparison is no longer available").fetchSemanticsNodes().isNotEmpty()
                }
                scenario.recreate()
                compose.onNodeWithText("Timetable changes").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("Timetable changes").assertDoesNotExist()
                scenario.recreate()
                compose.onNodeWithText("Timetable changes").assertDoesNotExist()
                context.startActivity(notification())
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("Timetable changes").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("Timetable changes").assertDoesNotExist()
            }
        } finally {
            if (host.isNotBlank()) prefs.host = host
            else context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().remove("host").apply()
            prefs.home = home
            prefs.hiddenGroups = hidden
            prefs.cycleWeek = week
            prefs.language = language
        }
    }
}
