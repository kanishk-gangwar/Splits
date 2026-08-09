package com.kanishk.splits

import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.involves
import com.kanishk.splits.model.involving
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun entry(
    id: String,
    paidBy: String,
    sharedWith: List<String>,
    kind: ExpenseKind = ExpenseKind.EXPENSE,
) = Expense(
    id = id,
    groupId = "g",
    title = id,
    amountMinor = 1_000,
    paidByMemberId = paidBy,
    kind = kind,
    categoryId = null,
    note = null,
    occurredAt = 0,
    createdAt = 0,
    updatedAt = 0,
    splits = sharedWith.map { Split(it, 1_000L / sharedWith.size) },
)

class ExpenseFilterTest {

    @Test
    fun `paying for something counts as being involved`() {
        // Aarav paid but took no share — filtering him out would hide his own spending.
        val expense = entry("dinner", paidBy = "aarav", sharedWith = listOf("bhavna", "chetan"))
        assertTrue(expense.involves("aarav"))
    }

    @Test
    fun `owing a share counts as being involved`() {
        val expense = entry("dinner", paidBy = "aarav", sharedWith = listOf("bhavna", "chetan"))
        assertTrue(expense.involves("bhavna"))
        assertTrue(expense.involves("chetan"))
    }

    @Test
    fun `someone with no connection is not involved`() {
        val expense = entry("dinner", paidBy = "aarav", sharedWith = listOf("bhavna"))
        assertFalse(expense.involves("divya"))
    }

    @Test
    fun `a null filter returns everything untouched`() {
        val all = listOf(
            entry("a", "aarav", listOf("aarav", "bhavna")),
            entry("b", "bhavna", listOf("chetan")),
        )
        assertEquals(all, all.involving(null))
    }

    @Test
    fun `filtering keeps both what they paid for and what they owe`() {
        val all = listOf(
            entry("paid-by-aarav", "aarav", listOf("bhavna")),
            entry("owed-by-aarav", "bhavna", listOf("aarav")),
            entry("nothing-to-do-with-aarav", "bhavna", listOf("chetan")),
        )

        val mine = all.involving("aarav").map { it.id }
        assertEquals(listOf("paid-by-aarav", "owed-by-aarav"), mine)
    }

    @Test
    fun `settlements show up for both sides`() {
        // A settlement is one-sided in the split, so the payer is only reachable via paidBy.
        val settlement = entry(
            "settle",
            paidBy = "aarav",
            sharedWith = listOf("bhavna"),
            kind = ExpenseKind.REIMBURSEMENT,
        )
        assertTrue(settlement.involves("aarav"), "the person who paid up")
        assertTrue(settlement.involves("bhavna"), "the person who was paid")
        assertFalse(settlement.involves("chetan"))
    }

    @Test
    fun `filtering an empty list is empty`() {
        assertEquals(emptyList(), emptyList<Expense>().involving("aarav"))
    }
}
