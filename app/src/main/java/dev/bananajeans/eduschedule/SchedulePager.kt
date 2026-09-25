package dev.bananajeans.eduschedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val CENTER_PAGE = Int.MAX_VALUE / 2

/** Native paging supplies drag tracking, adjacent-page reveal, velocity and snap-back motion. */
@Composable
internal fun DatePager(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    stepDays: Long = 1,
    content: @Composable (LocalDate) -> Unit
) {
    require(stepDays > 0)
    val anchorEpoch = rememberSaveable(stepDays) { date.toEpochDay() }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    fun pageFor(value: LocalDate) =
        (CENTER_PAGE + ChronoUnit.DAYS.between(anchor, value) / stepDays).toInt()
    fun dateFor(page: Int) = anchor.plusDays((page.toLong() - CENTER_PAGE) * stepDays)
    val pager = rememberPagerState(initialPage = pageFor(date), pageCount = { Int.MAX_VALUE })
    val selectedDate by rememberUpdatedState(date)
    val select by rememberUpdatedState(onDateChange)
    var programmaticScroll by remember { mutableStateOf(false) }

    // Arrows, Today and the date picker keep the same pager rather than recreating its pages.
    LaunchedEffect(date, stepDays) {
        val target = pageFor(date)
        if (pager.settledPage != target) {
            programmaticScroll = true
            try { pager.animateScrollToPage(target) }
            finally { programmaticScroll = false }
        }
    }
    LaunchedEffect(pager, anchorEpoch, stepDays) {
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }
            .filter { !it.second }
            .map { it.first }
            .distinctUntilChanged()
            .collect { page ->
                val settledDate = dateFor(page)
                if (!programmaticScroll && settledDate != selectedDate) select(settledDate)
            }
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        pageSpacing = 12.dp,
        key = { dateFor(it).toEpochDay() },
        verticalAlignment = Alignment.Top
    ) { page -> content(dateFor(page)) }
}

@Composable
internal fun SchedulePagePlaceholder(date: LocalDate, loading: Boolean, error: String?, onRetry: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale)),
            style = MaterialTheme.typography.titleMedium)
        if (loading) CircularProgressIndicator()
        else {
            Text(error ?: stringResource(R.string.not_available_offline))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
