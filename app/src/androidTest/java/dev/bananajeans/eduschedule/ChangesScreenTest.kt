package dev.bananajeans.eduschedule

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ChangesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lesson = ChangeLesson("a", "Math", 0, "1", "08:45", "09:30", "Teacher A", "101", "", emptyList(), "")
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "change-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun changedLessonShowsReadableBeforeAndNow() {
        val report = ChangeReport("id", "School", "9.X", "2026-10-01T08:00:00Z", "2026-09-14", "",
            listOf(LessonChange(lesson, lesson.copy(room = "202"))))
        compose.setContent { EduTheme(dynamic = false) { ChangesContent(report, "Europe/Tallinn") } }
        compose.onNodeWithText("Lesson changed").assertIsDisplayed()
        compose.onNodeWithText("Before").assertIsDisplayed()
        compose.onNodeWithText("101").performScrollTo().assertIsDisplayed()
        screenshot("changes-before-light")
        compose.onNodeWithText("Now").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("202").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Room · changed").assertCountEquals(2)
        screenshot("changes-now-light")
    }
    @Test fun darkThemeAddedAndRemovedLessonsAreExplicit() {
        val report = ChangeReport("id", "School", "9.X", "2026-10-01T08:00:00Z", "2026-09-14", "",
            listOf(LessonChange(null, lesson), LessonChange(lesson.copy(subject = "Art"), null)))
        compose.setContent { EduTheme(theme = "Dark", dynamic = false) { ChangesContent(report, "Europe/Tallinn") } }
        compose.onNodeWithText("Lesson added").assertIsDisplayed()
        screenshot("changes-added-dark")
        compose.onNodeWithText("Lesson removed").performScrollTo().assertIsDisplayed()
        screenshot("changes-removed-dark")
    }
    @Test fun missingReportHasClearFallback() {
        compose.setContent { EduTheme(dynamic = false) { ChangesContent(null, "Europe/Tallinn") } }
        compose.onNodeWithText("This comparison is no longer available").assertIsDisplayed()
    }
}
