package dev.bananajeans.eduschedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SchedulePagerTest {
    @get:Rule val compose = createComposeRule()
    private val initial = LocalDate.of(2026, 9, 14)

    @Test fun draggingRevealsNeighborBeforeCommittingAndCanSnapBack() {
        var date by mutableStateOf(initial)
        compose.setContent {
            EduTheme(dynamic = false) {
                DatePager(date, { date = it }, Modifier.testTag("pager")) { pageDate ->
                    Box(Modifier.fillMaxSize().testTag("page-$pageDate")) { Text(pageDate.toString()) }
                }
            }
        }
        val current = compose.onNodeWithTag("page-$initial")
        val before = current.getUnclippedBoundsInRoot().left
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.75f, height * 0.5f))
            moveTo(Offset(width * 0.50f, height * 0.5f), delayMillis = 300)
        }
        compose.runOnIdle { assertEquals(initial, date) }
        assertTrue(current.getUnclippedBoundsInRoot().left < before)
        compose.onNodeWithTag("page-${initial.plusDays(1)}").assertIsDisplayed()
        compose.onNodeWithTag("pager").performTouchInput {
            moveTo(Offset(width * 0.74f, height * 0.5f), delayMillis = 300)
            advanceEventTime(300)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(initial, date) }
        assertEquals(before.value, current.getUnclippedBoundsInRoot().left.value, 1f)
    }

    @Test fun externalDateChangesAndRapidSwipesKeepPagerAndSelectionTogether() {
        var date by mutableStateOf(initial)
        compose.setContent {
            EduTheme(dynamic = false) {
                DatePager(date, { date = it }, Modifier.testTag("pager")) { pageDate ->
                    Box(Modifier.fillMaxSize().testTag("page-$pageDate")) { Text(pageDate.toString()) }
                }
            }
        }
        compose.runOnIdle { date = initial.plusDays(30) }
        compose.waitForIdle()
        compose.onNodeWithTag("page-${initial.plusDays(30)}").assertIsDisplayed()
        repeat(2) { compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }; compose.waitForIdle() }
        compose.runOnIdle { assertEquals(initial.plusDays(32), date) }
    }

    @Test fun weekColumnsScrollBeforeThePagerRevealsTheNextWeek() {
        var date by mutableStateOf(initial)
        val scroll = ScrollState(0)
        compose.setContent {
            EduTheme(dynamic = false) {
                DatePager(date, { date = it }, Modifier.testTag("pager"), stepDays = 7) { pageDate ->
                    val child = if (pageDate == initial) scroll else remember { ScrollState(0) }
                    Row(Modifier.fillMaxSize().testTag("week-$pageDate").horizontalScroll(child, overscrollEffect = null)) {
                        repeat(5) { Box(Modifier.width(272.dp).fillMaxHeight()) { Text("$pageDate: $it") } }
                    }
                }
            }
        }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(initial, date); assertTrue(scroll.value > 0) }
        compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(initial.plusWeeks(1), date) }
        compose.onNodeWithTag("week-${initial.plusWeeks(1)}").assertIsDisplayed()
    }
}
