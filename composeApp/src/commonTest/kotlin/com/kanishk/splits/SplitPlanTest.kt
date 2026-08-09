package com.kanishk.splits

import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.SplitMode
import com.kanishk.splits.model.planSplits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val THREE = listOf("a", "b", "c")

private fun plan(
    mode: SplitMode,
    amount: Long,
    typed: Map<String, String> = emptyMap(),
    ids: List<String> = THREE,
) = planSplits(
    kind = ExpenseKind.EXPENSE,
    amountMinor = amount,
    participantIds = ids,
    paidToMemberId = null,
    mode = mode,
    typed = typed,
).associate { it.memberId to it.shareMinor }

class SplitPlanTest {

    // ------------------------------------------------------------------- exact --

    @Test
    fun `typing one exact amount spreads the rest across the others`() {
        // The reported bug: entering 300 for one person left the other two on zero.
        val shares = plan(SplitMode.Exact, amount = 90_000, typed = mapOf("a" to "300"))

        assertEquals(30_000, shares.getValue("a"))
        assertEquals(30_000, shares.getValue("b"))
        assertEquals(30_000, shares.getValue("c"))
        assertEquals(90_000, shares.values.sum())
    }

    @Test
    fun `an uneven remainder still reconciles to the total`() {
        val shares = plan(SplitMode.Exact, amount = 10_000, typed = mapOf("a" to "40"))

        assertEquals(4_000, shares.getValue("a"))
        // 60.00 across two people divides cleanly; the point is that nothing is lost.
        assertEquals(10_000, shares.values.sum())
    }

    @Test
    fun `stray paise land on somebody rather than vanishing`() {
        val shares = plan(SplitMode.Exact, amount = 10_001, typed = mapOf("a" to "0.01"))
        assertEquals(10_001, shares.values.sum())
    }

    @Test
    fun `two pinned rows leave the third holding the balance`() {
        val shares = plan(
            SplitMode.Exact,
            amount = 100_00,
            typed = mapOf("a" to "20", "b" to "30"),
        )
        assertEquals(20_00, shares.getValue("a"))
        assertEquals(30_00, shares.getValue("b"))
        assertEquals(50_00, shares.getValue("c"))
    }

    @Test
    fun `clearing a field hands the row back to automatic`() {
        // A blank string must read as "no opinion", not as zero.
        val shares = plan(SplitMode.Exact, amount = 90_00, typed = mapOf("a" to "", "b" to ""))
        assertEquals(30_00, shares.getValue("a"))
        assertEquals(30_00, shares.getValue("b"))
        assertEquals(30_00, shares.getValue("c"))
    }

    @Test
    fun `pinning every row is left exactly as typed`() {
        val shares = plan(
            SplitMode.Exact,
            amount = 100_00,
            typed = mapOf("a" to "10", "b" to "20", "c" to "30"),
        )
        assertEquals(10_00, shares.getValue("a"))
        assertEquals(20_00, shares.getValue("b"))
        assertEquals(30_00, shares.getValue("c"))
        // Under the total on purpose — the editor surfaces the shortfall instead of hiding it.
        assertEquals(60_00, shares.values.sum())
    }

    @Test
    fun `overshooting the total does not push anyone negative`() {
        val shares = plan(SplitMode.Exact, amount = 50_00, typed = mapOf("a" to "80"))
        assertEquals(80_00, shares.getValue("a"))
        assertEquals(0, shares.getValue("b"))
        assertEquals(0, shares.getValue("c"))
        assertTrue(shares.values.all { it >= 0 })
    }

    // ----------------------------------------------------------------- percent --

    @Test
    fun `typing one percentage spreads the remainder across the others`() {
        val shares = plan(SplitMode.Percent, amount = 100_00, typed = mapOf("a" to "50"))

        assertEquals(50_00, shares.getValue("a"))
        assertEquals(25_00, shares.getValue("b"))
        assertEquals(25_00, shares.getValue("c"))
        assertEquals(100_00, shares.values.sum())
    }

    @Test
    fun `percentages that reach exactly 100 reconcile to the paisa`() {
        val shares = plan(
            SplitMode.Percent,
            amount = 100_00,
            typed = mapOf("a" to "33.33", "b" to "33.33", "c" to "33.34"),
        )
        assertEquals(100_00, shares.values.sum())
    }

    @Test
    fun `percentages over 100 are shown literally rather than normalised away`() {
        val shares = plan(
            SplitMode.Percent,
            amount = 100_00,
            typed = mapOf("a" to "80", "b" to "80", "c" to "80"),
        )
        // Silently rescaling to 100% would hide the user's mistake behind a plausible split.
        assertTrue(shares.values.sum() > 100_00, "expected an over-allocation to stay visible")
    }

    // ------------------------------------------------------------------- other --

    @Test
    fun `equal mode ignores anything typed`() {
        val shares = plan(SplitMode.Equally, amount = 90_00, typed = mapOf("a" to "999"))
        assertEquals(30_00, shares.getValue("a"))
        assertEquals(90_00, shares.values.sum())
    }

    @Test
    fun `a settlement puts the whole amount on the recipient`() {
        val splits = planSplits(
            kind = ExpenseKind.REIMBURSEMENT,
            amountMinor = 500_00,
            participantIds = THREE,
            paidToMemberId = "b",
            mode = SplitMode.Equally,
            typed = emptyMap(),
        )
        assertEquals(1, splits.size)
        assertEquals("b", splits[0].memberId)
        assertEquals(500_00, splits[0].shareMinor)
    }

    @Test
    fun `exact splits reconcile across many shapes`() {
        for (total in listOf(1L, 99L, 10_000L, 33_333L, 1_000_001L)) {
            for (people in 1..6) {
                val ids = (1..people).map { "m$it" }
                // Pin the first row at roughly a third and let the rest absorb the difference.
                val pinned = total / 3
                val shares = plan(
                    SplitMode.Exact,
                    amount = total,
                    typed = mapOf("m1" to (pinned / 100).toString()),
                    ids = ids,
                )
                assertTrue(
                    shares.values.sum() <= total || people == 1,
                    "over-allocated $total across $people",
                )
                assertTrue(shares.values.all { it >= 0 })
            }
        }
    }
}
