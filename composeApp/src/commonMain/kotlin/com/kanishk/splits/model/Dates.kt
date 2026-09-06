package com.kanishk.splits.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Year plus calendar month. Used for expense-list paging and monthly insights. */
data class MonthYear(val year: Int, val month: Month) : Comparable<MonthYear> {
    override fun compareTo(other: MonthYear): Int =
        compareValuesBy(this, other, { it.year }, { it.month.ordinal })

    fun plusMonths(delta: Int): MonthYear {
        val index = year * 12 + month.ordinal + delta
        return MonthYear(index.floorDiv(12), Month.entries[index.mod(12)])
    }

    fun minusMonths(delta: Int): MonthYear = plusMonths(-delta)
}

/** How many calendar months the expenses list reveals at a time. */
const val ExpensePageMonths = 3

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalTime::class)
private fun localDateOf(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

@OptIn(ExperimentalTime::class)
fun startOfDayMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

fun todayLocal(): LocalDate = localDateOf(nowMillis())

fun localDateAt(epochMillis: Long): LocalDate = localDateOf(epochMillis)

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val MONTHS_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun monthName(month: Month): String = MONTHS[month.ordinal]

fun monthYearOf(epochMillis: Long): MonthYear {
    val date = localDateOf(epochMillis)
    return MonthYear(date.year, date.month)
}

fun todayMonthYear(): MonthYear = monthYearOf(nowMillis())

fun startOfMonthMillis(monthYear: MonthYear): Long =
    startOfDayMillis(LocalDate(monthYear.year, monthYear.month, 1))

/**
 * Oldest timestamp still in view after [pagesLoaded] windows of [ExpensePageMonths].
 *
 * Page 1 on a day in September is July 1st of that year — the current month and the two
 * before it. Each extra page walks another three calendar months into the past.
 */
fun expensePageCutoffMillis(
    nowMillis: Long,
    pagesLoaded: Int,
    monthsPerPage: Int = ExpensePageMonths,
): Long {
    val pages = pagesLoaded.coerceAtLeast(1)
    val oldest = monthYearOf(nowMillis).minusMonths(pages * monthsPerPage - 1)
    return startOfMonthMillis(oldest)
}

/** "September 2026" — the insights header and month picker. */
fun formatMonthYear(monthYear: MonthYear): String =
    "${MONTHS_FULL[monthYear.month.ordinal]} ${monthYear.year}"

fun abbreviatedMonth(month: Month): String = monthName(month)

/** Section headers on the expense list: "Today", "Yesterday", "12 Aug", "12 Aug 2024". */
fun formatDayHeader(epochMillis: Long, now: Long = nowMillis()): String {
    val date = localDateOf(epochMillis)
    val today = localDateOf(now)
    val daysApart = today.toEpochDays() - date.toEpochDays()
    return when {
        daysApart == 0L -> "Today"
        daysApart == 1L -> "Yesterday"
        date.year == today.year -> "${date.day} ${monthName(date.month)}"
        else -> "${date.day} ${monthName(date.month)} ${date.year}"
    }
}

/** Compact form used inside rows and the editor: "12 Aug 2025". */
fun formatDate(epochMillis: Long): String {
    val date = localDateOf(epochMillis)
    return "${date.day} ${monthName(date.month)} ${date.year}"
}

/** "2 days ago" style, for the group card subtitle. */
fun formatRelative(epochMillis: Long, now: Long = nowMillis()): String {
    val deltaMillis = now - epochMillis
    val minutes = deltaMillis / 60_000
    val hours = minutes / 60
    val days = localDateOf(now).toEpochDays() - localDateOf(epochMillis).toEpochDays()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 && days == 0L -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> formatDate(epochMillis)
    }
}

/** Day-granularity key used to group expenses into sections. */
fun dayKey(epochMillis: Long): Long = localDateOf(epochMillis).toEpochDays()
