package com.kanishk.splits

import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.MemberBalance
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
    fun `each member's own spend is their share, not what they fronted`() {
        val summary = summarise(
            listOf(a, b, c),
            listOf(
                expense("e1", 3_000, "a", listOf(Split("a", 1_000), Split("b", 1_000), Split("c", 1_000))),
                expense("e2", 600, "b", listOf(Split("a", 300), Split("b", 300))),
            ),
        )

        assertEquals(1_300, summary.spentBy("a"))
        assertEquals(1_300, summary.spentBy("b"))
        assertEquals(1_000, summary.spentBy("c"))
        assertEquals(3_000, summary.paidBy("a"))
        assertEquals(600, summary.paidBy("b"))
        assertEquals(0, summary.paidBy("c"))

        // The whole point of splitting spend per person: it has to add back up to the total.
        assertEquals(summary.totalSpentMinor, summary.balances.sumOf { it.spentMinor })
        assertEquals(summary.totalSpentMinor, summary.balances.sumOf { it.paidMinor })
    }

    @Test
    fun `settlements move balances without touching anyone's spend`() {
        val summary = summarise(
            listOf(a, b),
            listOf(
                expense("e1", 1_000, "a", listOf(Split("a", 500), Split("b", 500))),
                expense("s1", 500, "b", listOf(Split("a", 500)), kind = ExpenseKind.REIMBURSEMENT),
            ),
        )

        assertEquals(0, summary.balanceOf("a"))
        assertEquals(500, summary.spentBy("a"))
        assertEquals(500, summary.spentBy("b"))
        assertEquals(1_000, summary.paidBy("a"))
        assertEquals(0, summary.paidBy("b"))
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
    fun `the case plain greedy gets wrong is settled in three payments`() {
        // +3 and -3 cancel on their own; greedy matching never spots that and spends four.
        val balances = listOf(
            MemberBalance("a", 4), MemberBalance("b", 3), MemberBalance("c", -1),
            MemberBalance("d", -3), MemberBalance("e", -3),
        )
        assertEquals(3, suggestSettlements(balances).size)
    }

    @Test
    fun `suggested payments are always the true minimum`() {
        // Brute force is only tractable for tiny groups, which is exactly why the shipped
        // algorithm cannot be brute force — so check the two agree everywhere they overlap.
        var seed = 12_345L
        fun next(bound: Int): Int {
            seed = (seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L)
            return ((seed ushr 33).toInt() and Int.MAX_VALUE) % bound
        }

        repeat(3_000) {
            val size = 3 + next(5)
            val amounts = MutableList(size - 1) { (next(13) - 6).toLong() }
            amounts += -amounts.sum()
            if (amounts.all { it == 0L }) return@repeat

            val balances = amounts.mapIndexed { i, v -> MemberBalance("m$i", v) }
            val suggested = suggestSettlements(balances)

            assertEquals(
                minimumTransfers(amounts.filter { it != 0L }),
                suggested.size,
                "not minimal for $amounts",
            )

            // Minimal is worthless if it does not actually settle the group.
            val net = balances.associate { it.memberId to it.netMinor }.toMutableMap()
            suggested.forEach {
                net[it.fromMemberId] = net.getValue(it.fromMemberId) + it.amountMinor
                net[it.toMemberId] = net.getValue(it.toMemberId) - it.amountMinor
                assertTrue(it.amountMinor > 0, "non-positive transfer in $amounts")
            }
            assertTrue(net.values.all { it == 0L }, "left over for $amounts: $net")
        }
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

/**
 * Reference implementation for the test only: the minimum number of transfers is the number
 * of people minus the largest number of disjoint groups whose balances each sum to zero.
 * Exponential and unashamedly so — it exists to catch the fast version cheating.
 */
private fun minimumTransfers(amounts: List<Long>): Int {
    val n = amounts.size
    if (n == 0) return 0
    val full = (1 shl n) - 1
    val sum = LongArray(1 shl n)
    for (mask in 1..full) {
        val low = mask.countTrailingZeroBits()
        sum[mask] = sum[mask and (mask - 1)] + amounts[low]
    }
    val best = IntArray(1 shl n) { -1 }
    best[0] = 0
    for (mask in 1..full) {
        if (sum[mask] != 0L) continue
        val low = mask and (-mask)
        val rest = mask xor low
        var sub = rest
        while (true) {
            val group = sub or low
            if (sum[group] == 0L) {
                val remainder = best[mask xor group]
                if (remainder >= 0 && remainder + 1 > best[mask]) best[mask] = remainder + 1
            }
            if (sub == 0) break
            sub = (sub - 1) and rest
        }
    }
    return n - best[full]
}
