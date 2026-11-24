package com.kanishk.splits

import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.Payment
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.allocateProportionally
import com.kanishk.splits.model.involves
import com.kanishk.splits.model.summarise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun member(id: String) = Member(id, "g", id, 0, null)

private fun dinner(amount: Long, payers: List<Payment>, splits: List<Split>) = Expense(
    id = "e",
    groupId = "g",
    title = "Dinner",
    amountMinor = amount,
    paidByMemberId = payers.maxBy { it.paidMinor }.memberId,
    kind = ExpenseKind.EXPENSE,
    categoryId = null,
    note = null,
    occurredAt = 0,
    createdAt = 0,
    updatedAt = 0,
    splits = splits,
    payers = payers,
)

class MultiPayerTest {

    private val a = member("a")
    private val b = member("b")
    private val c = member("c")

    @Test
    fun `each payer is credited with what they put in`() {
        // 3,000 dinner: A paid 2,000 and B paid 1,000, shared equally by three.
        val expense = dinner(
            3_000,
            listOf(Payment("a", 2_000), Payment("b", 1_000)),
            listOf(Split("a", 1_000), Split("b", 1_000), Split("c", 1_000)),
        )
        val summary = summarise(listOf(a, b, c), listOf(expense))

        assertEquals(3_000, summary.totalSpentMinor)
        assertEquals(1_000, summary.balanceOf("a"))
        assertEquals(0, summary.balanceOf("b"))
        assertEquals(-1_000, summary.balanceOf("c"))
        assertEquals(2_000, summary.paidBy("a"))
        assertEquals(1_000, summary.paidBy("b"))
    }

    @Test
    fun `pairwise debts follow each payer's portion and still net to the balances`() {
        val expense = dinner(
            3_000,
            listOf(Payment("a", 2_000), Payment("b", 1_000)),
            listOf(Split("a", 1_000), Split("b", 1_000), Split("c", 1_000)),
        )
        val summary = summarise(listOf(a, b, c), listOf(expense))

        // C's 1,000 goes about two thirds to A and one third to B; B's the same way. Exactly
        // which row absorbs the odd paisa does not matter, as long as everything adds up.
        val cToA = summary.pairDebts.getValue("c" to "a")
        val cToB = summary.pairDebts.getValue("c" to "b")
        assertEquals(1_000, cToA + cToB)
        assertTrue(cToA in 666..667, "c owes a $cToA")
        assertTrue(summary.pairDebts.getValue("b" to "a") in 666..667)
        assertTrue(summary.pairDebts.getValue("a" to "b") in 333..334)

        val owedTo = summary.pairDebts.entries.groupBy({ it.key.second }, { it.value })
        val owedBy = summary.pairDebts.entries.groupBy({ it.key.first }, { it.value })
        for (id in listOf("a", "b", "c")) {
            val net = owedTo[id].orEmpty().sum() - owedBy[id].orEmpty().sum()
            assertEquals(summary.balanceOf(id), net, "pairwise debts must add up for $id")
        }
    }

    @Test
    fun `an expense without payers is the old single-payer entry`() {
        val single = dinner(1_000, listOf(Payment("a", 1_000)), listOf(Split("b", 1_000)))
            .copy(payers = emptyList())
        assertEquals(listOf(Payment("a", 1_000)), single.contributions)
        assertEquals(1_000, summarise(listOf(a, b), listOf(single)).balanceOf("a"))
    }

    @Test
    fun `a payer who owes no share is still involved`() {
        val expense = dinner(
            1_000,
            listOf(Payment("a", 500), Payment("c", 500)),
            listOf(Split("a", 500), Split("b", 500)),
        )
        assertTrue(expense.involves("c"))
    }

    @Test
    fun `proportional allocation always adds back up`() {
        assertEquals(listOf(34L, 33L, 33L), allocateProportionally(100, listOf(1, 1, 1)))
        assertEquals(listOf(667L, 333L), allocateProportionally(1_000, listOf(2_000, 1_000)))
        assertEquals(listOf(7L), allocateProportionally(7, listOf(3)))
        val big = allocateProportionally(9_000_000_001, listOf(5_000_000_000, 4_000_000_001))
        assertEquals(9_000_000_001, big.sum())
    }
}
