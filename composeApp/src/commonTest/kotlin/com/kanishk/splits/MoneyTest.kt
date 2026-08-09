package com.kanishk.splits

import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.minorToEditText
import com.kanishk.splits.model.parseAmountToMinor
import com.kanishk.splits.model.splitByWeights
import com.kanishk.splits.model.splitEvenly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `even split never loses or invents money`() {
        // 100.00 across 3 people cannot divide cleanly; the stray paise must still land somewhere.
        val parts = splitEvenly(10_000, 3)
        assertEquals(3, parts.size)
        assertEquals(10_000, parts.sum())
        assertEquals(listOf(3334L, 3333L, 3333L), parts)
    }

    @Test
    fun `even split handles exact division`() {
        assertEquals(listOf(2500L, 2500L, 2500L, 2500L), splitEvenly(10_000, 4))
    }

    @Test
    fun `even split of zero people is empty`() {
        assertEquals(emptyList(), splitEvenly(10_000, 0))
    }

    @Test
    fun `weighted split sums back to the total`() {
        // 33.33% / 33.33% / 33.34% of 100.00
        val parts = splitByWeights(10_000, listOf(3333, 3333, 3334))
        assertEquals(10_000, parts.sum())
        assertEquals(3, parts.size)
    }

    @Test
    fun `weighted split with lopsided weights still balances`() {
        val parts = splitByWeights(999_99, listOf(1, 1, 1, 1, 1, 1, 7))
        assertEquals(999_99, parts.sum())
    }

    @Test
    fun `zero weights fall back to an even split`() {
        val parts = splitByWeights(9_000, listOf(0, 0, 0))
        assertEquals(9_000, parts.sum())
        assertEquals(listOf(3000L, 3000L, 3000L), parts)
    }

    @Test
    fun `rupees group the Indian way`() {
        assertEquals("₹12,34,567", formatMinor(1_23_45_67_00, "INR"))
        assertEquals("₹1,234.50", formatMinor(1_234_50, "INR"))
        assertEquals("₹999", formatMinor(999_00, "INR"))
    }

    @Test
    fun `dollars group the western way`() {
        assertEquals("$1,234,567", formatMinor(1_234_567_00, "USD"))
        assertEquals("$12.05", formatMinor(1205, "USD"))
    }

    @Test
    fun `negative amounts keep the sign outside the symbol`() {
        assertEquals("-₹500", formatMinor(-500_00, "INR"))
    }

    @Test
    fun `amount parsing tolerates partial typing`() {
        assertEquals(1_200_50, parseAmountToMinor("1200.5"))
        assertEquals(1_200_00, parseAmountToMinor("1200"))
        assertEquals(1_200_00, parseAmountToMinor("1200."))
        assertEquals(1_200_56, parseAmountToMinor("1,200.567"))
        assertEquals(0L, parseAmountToMinor("."))
        assertNull(parseAmountToMinor(""))
        assertNull(parseAmountToMinor("1.2.3"))
    }

    @Test
    fun `edit text round trips through minor units`() {
        assertEquals("1200.50", minorToEditText(1_200_50))
        assertEquals("1200", minorToEditText(1_200_00))
        assertEquals("", minorToEditText(0))
        assertEquals(1_200_50, parseAmountToMinor(minorToEditText(1_200_50)))
    }

    @Test
    fun `splitting any amount across any group always reconciles`() {
        // Brute force the combinations a real group would hit.
        for (total in listOf(1L, 7L, 99L, 100L, 10_000L, 33_333L, 1_000_001L)) {
            for (people in 1..9) {
                assertEquals(
                    total,
                    splitEvenly(total, people).sum(),
                    "even split of $total across $people",
                )
            }
        }
    }

    @Test
    fun `weighted splits reconcile across many shapes`() {
        val weightSets = listOf(
            listOf(1L, 2L, 3L),
            listOf(5L, 5L),
            listOf(1L, 1L, 1L, 1L, 1L, 1L, 1L),
            listOf(10L, 1L),
        )
        for (total in listOf(1L, 101L, 9_999L, 123_456L)) {
            for (weights in weightSets) {
                val parts = splitByWeights(total, weights)
                assertEquals(total, parts.sum(), "weights $weights of $total")
                assertTrue(parts.size == weights.size)
            }
        }
    }
}
