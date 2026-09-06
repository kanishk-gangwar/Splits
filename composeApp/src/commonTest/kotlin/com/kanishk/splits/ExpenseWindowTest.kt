package com.kanishk.splits

import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.ExpensePageMonths
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.MonthYear
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.expensePageCutoffMillis
import com.kanishk.splits.model.formatMonthYear
import com.kanishk.splits.model.hasOccurredBefore
import com.kanishk.splits.model.inMonth
import com.kanishk.splits.model.monthInsights
import com.kanishk.splits.model.monthYearOf
import com.kanishk.splits.model.occurredOnOrAfter
import com.kanishk.splits.model.startOfDayMillis
import com.kanishk.splits.model.startOfMonthMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun member(id: String) = Member(id, "g", id, 0, null)

private fun expense(
    id: String,
    date: LocalDate,
    amount: Long = 1_000,
    paidBy: String = "a",
    sharedWith: List<String> = listOf("a", "b"),
    kind: ExpenseKind = ExpenseKind.EXPENSE,
) = Expense(
    id = id,
    groupId = "g",
    title = id,
    amountMinor = amount,
    paidByMemberId = paidBy,
    kind = kind,
    categoryId = null,
    note = null,
    occurredAt = startOfDayMillis(date),
    createdAt = 0,
    updatedAt = 0,
    splits = sharedWith.map { Split(it, amount / sharedWith.size) },
)

class ExpenseWindowTest {

    @Test
    fun `month arithmetic wraps years`() {
        val jan = MonthYear(2026, Month.JANUARY)
        assertEquals(MonthYear(2025, Month.DECEMBER), jan.minusMonths(1))
        assertEquals(MonthYear(2026, Month.APRIL), jan.plusMonths(3))
        assertEquals(jan, jan.minusMonths(12).plusMonths(12))
    }

    @Test
    fun `first page is the current month and the two before it`() {
        // 15 Sep 2026 → July, August, September. June is the first month left out.
        val now = startOfDayMillis(LocalDate(2026, 9, 15))
        val cutoff = expensePageCutoffMillis(now, pagesLoaded = 1)
        assertEquals(MonthYear(2026, Month.JULY), monthYearOf(cutoff))
        assertEquals(startOfMonthMillis(MonthYear(2026, Month.JULY)), cutoff)
    }

    @Test
    fun `each extra page walks another three calendar months back`() {
        val now = startOfDayMillis(LocalDate(2026, 9, 15))
        assertEquals(
            MonthYear(2026, Month.APRIL),
            monthYearOf(expensePageCutoffMillis(now, pagesLoaded = 2)),
        )
        assertEquals(
            MonthYear(2026, Month.JANUARY),
            monthYearOf(expensePageCutoffMillis(now, pagesLoaded = 3)),
        )
        assertEquals(ExpensePageMonths, 3)
    }

    @Test
    fun `the list keeps only expenses inside the loaded window`() {
        val now = startOfDayMillis(LocalDate(2026, 9, 15))
        val cutoff = expensePageCutoffMillis(now, pagesLoaded = 1)
        val all = listOf(
            expense("sep", LocalDate(2026, 9, 6)),
            expense("jul", LocalDate(2026, 7, 1)),
            expense("jun", LocalDate(2026, 6, 30)),
        )

        assertEquals(listOf("sep", "jul"), all.occurredOnOrAfter(cutoff).map { it.id })
        assertTrue(all.hasOccurredBefore(cutoff))
        assertFalse(all.take(2).hasOccurredBefore(cutoff))
    }

    @Test
    fun `inMonth keeps only that calendar month`() {
        val september = MonthYear(2026, Month.SEPTEMBER)
        val all = listOf(
            expense("sep", LocalDate(2026, 9, 1)),
            expense("aug", LocalDate(2026, 8, 31)),
            expense("oct", LocalDate(2026, 10, 1)),
        )
        assertEquals(listOf("sep"), all.inMonth(september).map { it.id })
    }

    @Test
    fun `insights total and member spends are the month's real expenses`() {
        val a = member("a")
        val b = member("b")
        val september = MonthYear(2026, Month.SEPTEMBER)
        val insights = monthInsights(
            members = listOf(a, b),
            expenses = listOf(
                expense(
                    "dinner",
                    LocalDate(2026, 9, 6),
                    amount = 3_000,
                    paidBy = "a",
                    sharedWith = listOf("a", "b"),
                ),
                expense(
                    "last-month",
                    LocalDate(2026, 8, 20),
                    amount = 9_000,
                    paidBy = "b",
                    sharedWith = listOf("a", "b"),
                ),
                expense(
                    "settle",
                    LocalDate(2026, 9, 7),
                    amount = 500,
                    paidBy = "b",
                    sharedWith = listOf("a"),
                    kind = ExpenseKind.REIMBURSEMENT,
                ),
            ),
            monthYear = september,
        )

        assertEquals(3_000, insights.totalSpentMinor)
        assertEquals(1_500, insights.members.first { it.member.id == "a" }.spentMinor)
        assertEquals(3_000, insights.members.first { it.member.id == "a" }.paidMinor)
        assertEquals(1_500, insights.members.first { it.member.id == "b" }.spentMinor)
        assertEquals(0, insights.members.first { it.member.id == "b" }.paidMinor)
        assertEquals(listOf("dinner"), insights.members.first { it.member.id == "a" }.expenses.map { it.id })
        assertEquals(listOf("dinner"), insights.members.first { it.member.id == "b" }.expenses.map { it.id })
        assertEquals("September 2026", formatMonthYear(september))
    }

    @Test
    fun `the device owner is listed first even when someone else spent more`() {
        val insights = monthInsights(
            members = listOf(member("aarav"), member("bhavna")),
            expenses = listOf(
                expense(
                    "taxi",
                    LocalDate(2026, 9, 6),
                    amount = 1_000,
                    paidBy = "aarav",
                    sharedWith = listOf("aarav"),
                ),
                expense(
                    "rent",
                    LocalDate(2026, 9, 6),
                    amount = 9_000,
                    paidBy = "bhavna",
                    sharedWith = listOf("bhavna"),
                ),
            ),
            monthYear = MonthYear(2026, Month.SEPTEMBER),
            myMemberId = "aarav",
        )
        assertEquals(listOf("aarav", "bhavna"), insights.members.map { it.member.id })
    }

    @Test
    fun `a month with nothing in it still lists every member at zero`() {
        val insights = monthInsights(
            members = listOf(member("a"), member("b")),
            expenses = listOf(expense("aug", LocalDate(2026, 8, 1))),
            monthYear = MonthYear(2026, Month.SEPTEMBER),
        )
        assertEquals(0, insights.totalSpentMinor)
        assertEquals(2, insights.members.size)
        assertTrue(insights.members.all { it.spentMinor == 0L && it.expenses.isEmpty() })
    }
}
