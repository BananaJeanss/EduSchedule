package dev.bananajeans.eduschedule

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(output?.let { File(it) } ?: context.getExternalFilesDir(null)!!,
            "change-screenshots").apply { mkdirs() }
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
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Lesson removed"))
        compose.onNodeWithText("Lesson removed").assertIsDisplayed()
        screenshot("changes-removed-dark")
    }
    @Test fun longDetailsRemainReadableAtLargeFontSize() {
        val before = lesson.copy(subject = "Art, design and technology", room = "North building, second floor, studio 101",
            teacher = "Teacher A and Teacher B")
        val after = before.copy(room = "South building, ground floor, studio 202", teacher = "Teacher C and Teacher D")
        val report = ChangeReport("id", "Example school", "9.X", "2026-10-01T08:00:00Z", "2026-09-14", "A",
            listOf(LessonChange(before, after)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                EduTheme(dynamic = false) { ChangesContent(report, "Europe/Tallinn") }
            }
        }
        compose.onNodeWithText(before.room).performScrollTo().assertIsDisplayed()
        screenshot("changes-large-font-before")
        compose.onNodeWithText(after.room).performScrollTo().assertIsDisplayed()
        screenshot("changes-large-font-now")
    }
    @Test fun missingReportHasClearFallback() {
        compose.setContent { EduTheme(dynamic = false) { ChangesContent(null, "Europe/Tallinn") } }
        compose.onNodeWithText("This comparison is no longer available").assertIsDisplayed()
    }
}
