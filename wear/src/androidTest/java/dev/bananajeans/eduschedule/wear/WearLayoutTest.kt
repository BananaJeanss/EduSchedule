package dev.bananajeans.eduschedule.wear

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import dev.bananajeans.eduschedule.sync.*
import java.io.File
import java.time.*
import org.junit.Rule
import org.junit.Test

class WearLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 1)
    private val clock = Instant.parse("2026-10-01T09:10:00Z")
    private fun snapshot(language: String = "en", palette: String = "Default", theme: String = "Dark"): WearSnapshot = WearSnapshot(
        WearSettings("Example school", "9.X", ZoneId.of("UTC"), language, theme, palette, "#426437", "#546B91", "#F9FAF4", false, 0),
        listOf(WearDay(date, listOf(
            WearLesson("past", listOf("Art"), LocalTime.of(8, 0), LocalTime.of(8, 45), "100", "", ""),
            WearLesson("current", listOf("Math"), LocalTime.of(9, 0), LocalTime.of(9, 45), "101", "Example teacher", "Group A"),
            WearLesson("long", listOf("Art, design and technology"), LocalTime.of(10, 0), LocalTime.of(10, 45), "North building, studio 202", "", "")
        ), clock), WearDay(date.plusDays(1), emptyList(), clock)), clock)
    private fun scrollTo(tag: String) = rule.onNodeWithTag("wear-list").performScrollToNode(hasTestTag(tag))
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(output?.let(::File) ?: context.getExternalFilesDir(null)!!, "wear-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun currentLessonIsGlanceableAndPastLessonsCanBeExpanded() {
        rule.setContent { WearSchedule(snapshot(), {}, clock) }
        screenshot("day-on-open-dark")
        scrollTo("lesson-current")
        rule.onNodeWithText("Now").assertIsDisplayed()
        rule.onNodeWithText("Math").assertIsDisplayed()
        rule.onNodeWithText("09:00–09:45").assertIsDisplayed()
        screenshot("day-current-dark")
        rule.onNodeWithTag("lesson-past").assertDoesNotExist()
        scrollTo("earlier")
        rule.onNodeWithTag("earlier").performClick()
        scrollTo("lesson-past")
        rule.onNodeWithText("Art").assertIsDisplayed()
        screenshot("day-earlier-dark")
    }
    @Test fun todayResetsWeekSelectionAndDetailsReturnToTheSelectedDate() {
        rule.setContent { WearSchedule(snapshot(), {}, clock) }
        scrollTo("week")
        rule.onNodeWithTag("week").performClick()
        screenshot("week-dark")
        scrollTo("date-${date.plusDays(1)}")
        rule.onNodeWithTag("date-${date.plusDays(1)}").performClick()
        rule.onNodeWithText("No published lessons.").assertIsDisplayed()
        scrollTo("today")
        rule.onNodeWithTag("today").performClick()
        scrollTo("lesson-current")
        rule.onNodeWithTag("lesson-current").performClick()
        rule.onNodeWithText("Math").assertIsDisplayed()
        screenshot("detail-dark")
        scrollTo("back")
        rule.onNodeWithTag("back").performClick()
        scrollTo("lesson-current")
        rule.onNodeWithText("Math").assertIsDisplayed()
    }
    @Test fun longEstonianContentScrollsAtLargeFontSize() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                WearSchedule(snapshot("et", "Catppuccin"), {}, clock)
            }
        }
        scrollTo("lesson-long")
        rule.onNodeWithText("Art, design and technology").assertIsDisplayed()
        screenshot("estonian-large-font-day")
        rule.onNodeWithTag("lesson-long").performClick()
        rule.onNodeWithText("Art, design and technology").assertIsDisplayed()
        screenshot("estonian-large-font-detail")
        scrollTo("back")
        rule.onNodeWithText("Tagasi").assertIsDisplayed()
    }
    @Test fun lightOceanStatusUsesSyncedAppearance() {
        rule.setContent { WearSchedule(snapshot(palette = "Ocean", theme = "Light"), {}, clock) }
        scrollTo("lesson-current")
        screenshot("day-ocean-light")
        scrollTo("status")
        rule.onNodeWithTag("status").performClick()
        screenshot("status-ocean-light")
        scrollTo("sync")
        rule.onNodeWithText("Sync now").assertIsDisplayed()
    }
    @Test fun previousDatesShowLessonsWithoutExpandingEarlierLessons() {
        val past = snapshot().copy(days = snapshot().days.map { it.copy(date = it.date.minusDays(1)) })
        rule.setContent { WearSchedule(past, {}, clock) }
        scrollTo("week")
        rule.onNodeWithTag("week").performClick()
        scrollTo("date-${date.minusDays(1)}")
        rule.onNodeWithTag("date-${date.minusDays(1)}").performClick()
        scrollTo("lesson-current")
        rule.onNodeWithText("Math").assertIsDisplayed()
        rule.onNodeWithTag("earlier").assertDoesNotExist()
        screenshot("previous-day-dark")
    }
    @Test fun emptyWatchOffersSetupAndSync() {
        rule.setContent { WearSchedule(null, {}, clock) }
        rule.onNodeWithText("Set up EduSchedule on your phone.").assertIsDisplayed()
        screenshot("setup-dark")
        scrollTo("sync")
        rule.onNodeWithTag("sync").assertIsDisplayed()
    }
}
