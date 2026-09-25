package dev.bananajeans.eduschedule
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.LocalTime
class UiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun classPickerIsSearchableAndSelectionWorks() {
        var selected: Selection? = null
        val table = Timetable(Revision("1","Test",LocalDate.of(2026,9,14)),mapOf(ScheduleKind.CLASS to listOf(Entity("a","11.A"),Entity("b","12.B"))),emptyList(),emptyMap(),listOf("A"),"School")
        compose.setContent { EduTheme(dynamic=false) { BrowseScreen(table,null,"") { selected = it } } }
        compose.onNodeWithText("Search classes").performTextInput("11")
        compose.onNodeWithText("12.B").assertDoesNotExist()
        compose.onNodeWithText("11.A").performClick()
        assert(selected == Selection(ScheduleKind.CLASS,"a"))
    }
    @Test fun daySwipesMoveOneDayInEitherDirection() {
        val initial = LocalDate.of(2026, 9, 14)
        var date by androidx.compose.runtime.mutableStateOf(initial)
        compose.setContent {
            EduTheme(dynamic = false) {
                DatePager(date, { date = it }) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize()
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(initial.plusDays(1), date) }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(initial, date) }
    }
    @Test fun emptyDayHasExplicitState() {
        compose.setContent { EduTheme(dynamic=false) { DayScreen(emptyList(),LocalDate.of(2026,9,14),"Europe/Tallinn","Test") {} } }
        compose.onNodeWithText("No published lessons for this selection.").assertIsDisplayed()
    }
    @Test fun splitGroupBlockShowsTimeOnceAndAllOptions() {
        fun lesson(id: String, subject: String, group: String) = Lesson(
            id, subject, 0, "1", LocalTime.of(8,45), LocalTime.of(10,0),
            listOf("class"), listOf(), listOf(), listOf(group), group, "", "", "9.X", "1"
        )
        val block = LessonBlock(listOf(lesson("a","Language A","Group 1"), lesson("b","Language B","Group 2")))
        compose.setContent { EduTheme(dynamic=false) { LessonBlockCard(block) {} } }
        compose.onAllNodesWithText("08:45").assertCountEquals(1)
        compose.onNodeWithText("Language A").assertIsDisplayed()
        compose.onNodeWithText("Language B").assertIsDisplayed()
        compose.onNodeWithText("2 groups").assertIsDisplayed()
    }
    @Test fun currentTimeMarkerIsReadable() {
        compose.setContent { EduTheme(dynamic=false) { CurrentTimeMarker(LocalTime.of(12,34)) } }
        compose.onNodeWithText("12:34 now").assertIsDisplayed()
    }
}

