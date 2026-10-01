package dev.bananajeans.eduschedule.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bananajeans.eduschedule.sync.*
import java.time.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearScheduleSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<WearActivity>()

    @Before fun clearPreviousSnapshot() {
        rule.activity.deleteFile("schedule.json")
        rule.activity.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
    }

    @Test fun unpairedWatchExplainsPhoneSetup() {
        rule.onNodeWithText("Set up EduSchedule on your phone.").assertIsDisplayed()
        rule.onNodeWithTag("wear-list").performScrollToNode(hasTestTag("sync"))
        rule.onNodeWithText("Sync now").assertIsDisplayed()
    }

    @Test fun cachedDayCanBeReadWithoutPhone() {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val snapshot = WearSnapshot(WearSettings("example.edupage.org", "9.A", ZoneId.of("UTC"),
            "en", "Dark", "Default", "#ffffff", "#ffffff", "#000000", false, 0),
            listOf(WearDay(today, listOf(WearLesson("lesson", listOf("Math"), LocalTime.of(9, 0),
                LocalTime.of(10, 0), "101", "", "")), Instant.now())), Instant.now())
        rule.activity.runOnUiThread {
            check(WearCache.accept(rule.activity, WearSnapshotCodec.encode(snapshot)))
            rule.activity.recreate()
        }
        // Past lessons are collapsed on launch; reveal them independent of the test clock.
        rule.onNodeWithTag("wear-list").performScrollToNode(hasText("Show earlier lessons") or hasText("Math"))
        if (rule.onAllNodesWithText("Show earlier lessons").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Show earlier lessons").performClick()
        }
        rule.onNodeWithTag("wear-list").performScrollToNode(hasText("Math"))
        rule.onNodeWithText("Math").assertIsDisplayed()
        rule.onNodeWithTag("wear-list").performScrollToNode(hasTestTag("week"))
        rule.onNodeWithTag("week").performClick()
        rule.onNodeWithText("Week").assertIsDisplayed()
    }
}
