package com.kanishk.splits

import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.suggestSettlements
import com.kanishk.splits.model.summarise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun member(id: String) = Member(id, "g", id, 0, null)

private fun expense(
    id: String,
    amount: Long,
    paidBy: String,
    splits: List<Split>,
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
    occurredAt = 0,
    createdAt = 0,
    updatedAt = 0,
    splits = splits,
)

class BalancesTest {

    private val a = member("a")
    private val b = member("b")
    private val c = member("c")

    @Test
    fun `a simple expense lends the payer the others' shares`() {
        val summary = summarise(
            listOf(a, b),
            listOf(expense("e1", 1_000, "a", listOf(Split("a", 500), Split("b", 500)))),
        )

        assertEquals(1_000, summary.totalSpentMinor)
        assertEquals(500, summary.balanceOf("a"))
        assertEquals(-500, summary.balanceOf("b"))
    }

    @Test
    fun `a reimbursement changes balances but never the group total`() {
        // b owes a 500, then hands it over.
        val summary = summarise(
            listOf(a, b),
            listOf(
                expense("e1", 1_000, "a", listOf(Split("a", 500), Split("b", 500))),
                expense("e2", 500, "b", listOf(Split("a", 500)), ExpenseKind.REIMBURSEMENT),
            ),
        )

        // The settlement is money moving, not money spent.
        assertEquals(1_000, summary.totalSpentMinor)
        assertEquals(0, summary.balanceOf("a"))
        assertEquals(0, summary.balanceOf("b"))
        assertTrue(summary.settlements.isEmpty())
    }

    @Test
    fun `a group of reimbursements alone spends nothing`() {
        val summary = summarise(
            listOf(a, b),
            listOf(expense("e1", 5_000, "a", listOf(Split("b", 5_000)), ExpenseKind.REIMBURSEMENT)),
        )

        assertEquals(0, summary.totalSpentMinor)
        assertEquals(5_000, summary.balanceOf("a"))
        assertEquals(-5_000, summary.balanceOf("b"))
    }

    @Test
    fun `balances across a whole group always net to zero`() {
        val summary = summarise(
            listOf(a, b, c),
            listOf(
                expense("e1", 9_000, "a", listOf(Split("a", 3_000), Split("b", 3_000), Split("c", 3_000))),
                expense("e2", 4_500, "b", listOf(Split("b", 1_500), Split("c", 3_000))),
                expense("e3", 1_000, "c", listOf(Split("a", 1_000)), ExpenseKind.REIMBURSEMENT),
            ),
        )

        assertEquals(0, summary.balances.sumOf { it.netMinor })
        assertEquals(13_500, summary.totalSpentMinor)
    }

    @Test
    fun `settle up suggestions clear every debt`() {
        val summary = summarise(
            listOf(a, b, c),
            listOf(
                expense("e1", 9_000, "a", listOf(Split("a", 3_000), Split("b", 3_000), Split("c", 3_000))),
                expense("e2", 3_000, "b", listOf(Split("c", 3_000))),
            ),
        )

        // Apply every suggested transfer and confirm nobody is left owing anything.
        val net = summary.balances.associate { it.memberId to it.netMinor }.toMutableMap()
        summary.settlements.forEach { transfer ->
            net[transfer.fromMemberId] = net.getValue(transfer.fromMemberId) + transfer.amountMinor
            net[transfer.toMemberId] = net.getValue(transfer.toMemberId) - transfer.amountMinor
        }
        assertTrue(net.values.all { it == 0L }, "left over: $net")
    }

    @Test
    fun `settlements never exceed one fewer than the number of people`() {
        val balances = summarise(
            listOf(a, b, c, member("d"), member("e")),
            listOf(
                expense(
                    "e1", 10_000, "a",
                    listOf(
                        Split("a", 2_000), Split("b", 2_000), Split("c", 2_000),
                        Split("d", 2_000), Split("e", 2_000),
                    ),
                ),
            ),
        ).balances

        assertTrue(suggestSettlements(balances).size <= 4)
    }

    @Test
    fun `an already settled group suggests nothing`() {
        val summary = summarise(
            listOf(a, b),
            listOf(expense("e1", 1_000, "a", listOf(Split("a", 1_000)))),
        )
        assertTrue(summary.settlements.isEmpty())
        assertEquals(0, summary.balanceOf("b"))
    }
}
