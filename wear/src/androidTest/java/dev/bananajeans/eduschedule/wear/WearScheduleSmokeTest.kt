package dev.bananajeans.eduschedule.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
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
        rule.activity.recreate()
        rule.waitForIdle()
    }

    @Test fun unpairedWatchExplainsPhoneSetup() {
        rule.onNodeWithText("Set up EduSchedule on your phone.").assertIsDisplayed()
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
        rule.onNodeWithText("09:00 Math").assertIsDisplayed()
        rule.onNodeWithText("Week").performClick()
        rule.onNodeWithText("Week").assertIsDisplayed()
    }
}
