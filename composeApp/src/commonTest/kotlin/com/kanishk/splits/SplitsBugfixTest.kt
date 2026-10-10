package com.kanishk.splits

import com.kanishk.splits.data.deriveDeviceId
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Group
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.Settlement
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.summarise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ADMIN_DEVICE = "device-admin"
private const val MY_DEVICE = "device-mine"
private const val STRANGER = "device-stranger"

private fun m(id: String, claimedBy: String? = null) =
    Member(id = id, groupId = "g", name = id, colorIndex = 0, claimedByDeviceId = claimedBy)

private fun e(
    id: String,
    amount: Long,
    paidBy: String,
    splits: List<Split>,
    createdBy: String? = null,
    kind: ExpenseKind = ExpenseKind.EXPENSE,
) = Expense(
    id = id, groupId = "g", title = id, amountMinor = amount, paidByMemberId = paidBy,
    kind = kind, categoryId = null, note = null, occurredAt = 0, createdAt = 0, updatedAt = 0,
    splits = splits, createdByMemberId = createdBy,
)

private fun group(members: List<Member>, expenses: List<Expense> = emptyList()) = GroupDetail(
    group = Group(
        id = "g", name = "Trip", emoji = "🏝️", currencyCode = "INR", inviteCode = "ABCD2345",
        adminMemberId = "admin", createdAt = 0, updatedAt = 0, archived = false, hidden = false,
    ),
    members = members,
    expenses = expenses,
)

class SplitsBugfixTest {

    private val members = listOf(m("admin", ADMIN_DEVICE), m("me", MY_DEVICE), m("other", "someone-else"), m("free"))

    // ---- 1. no name, no writes

    @Test
    fun `a device without a name has not joined and can edit nothing`() {
        val detail = group(members)
        val mine = e("x", 100, "me", listOf(Split("me", 100)), createdBy = "me")
        assertFalse(detail.hasJoined(STRANGER))
        assertFalse(detail.canEditExpense(STRANGER, mine))
        assertFalse(detail.canEditExpense(null, mine))
        assertTrue(detail.hasJoined(MY_DEVICE))
    }

    // ---- 2. creator or admin only

    @Test
    fun `only the creator or the admin can edit an entry`() {
        val detail = group(members)
        val byOther = e("x", 100, "me", listOf(Split("me", 100)), createdBy = "other")
        val byMe = e("y", 100, "other", listOf(Split("other", 100)), createdBy = "me")

        assertFalse(detail.canEditExpense(MY_DEVICE, byOther), "paying for it does not make it yours")
        assertTrue(detail.canEditExpense(MY_DEVICE, byMe), "adding it does, whoever paid")
        assertTrue(detail.canEditExpense(ADMIN_DEVICE, byOther), "the admin can fix anything")
    }

    @Test
    fun `an entry from before owners existed belongs to its payer`() {
        val detail = group(members)
        val legacy = e("x", 100, "me", listOf(Split("me", 100)), createdBy = null)
        assertTrue(detail.canEditExpense(MY_DEVICE, legacy))

        val paidByOther = e("y", 100, "other", listOf(Split("other", 100)), createdBy = null)
        assertFalse(detail.canEditExpense(MY_DEVICE, paidByOther))
    }

    @Test
    fun `only the admin may change how the group settles up`() {
        val detail = group(members)
        assertTrue(detail.canChangeSettleMode(ADMIN_DEVICE))
        assertFalse(detail.canChangeSettleMode(MY_DEVICE))
        assertFalse(detail.canChangeSettleMode(STRANGER))
    }

    // ---- 3. device id survives a reinstall

    @Test
    fun `the device id is derived deterministically and in the server's shape`() {
        val first = deriveDeviceId("abc123")
        val again = deriveDeviceId("abc123")
        assertEquals(first, again, "same seed after a reinstall, same id")
        assertTrue(first!!.matches(Regex("^[0-9a-f]{32}$")), "splits_device accepts only this")
        assertNotEquals(first, deriveDeviceId("abc124"))
        assertNotEquals("abc123", first, "the raw platform id never leaves the device")
    }

    @Test
    fun `no seed means no derived id`() {
        assertNull(deriveDeviceId(null))
        assertNull(deriveDeviceId("   "))
    }

    // ---- 4. all-debts settle-up

    @Test
    fun `pairwise settle-up keeps each debt with the person it is owed to`() {
        // a pays 300 for a,b,c; b pays 300 for b,c,... the classic case where simplifying
        // routes c's money to someone c never shared with.
        val a = m("a"); val b = m("b"); val c = m("c")
        val summary = summarise(
            listOf(a, b, c),
            listOf(
                e("1", 200, "a", listOf(Split("b", 200))), // b owes a 200
                e("2", 200, "b", listOf(Split("c", 200))), // c owes b 200
            ),
        )
        // Simplified: c pays a directly, one payment.
        assertEquals(listOf(Settlement("c", "a", 200)), summary.settlementsFor(simplify = true))
        // All debts: both payments, each to whom it is really owed.
        assertEquals(
            setOf(Settlement("b", "a", 200), Settlement("c", "b", 200)),
            summary.settlementsFor(simplify = false).toSet(),
        )
    }

    @Test
    fun `pairwise settle-up nets the two directions of a pair`() {
        val a = m("a"); val b = m("b")
        val summary = summarise(
            listOf(a, b),
            listOf(
                e("1", 700, "a", listOf(Split("b", 700))),
                e("2", 300, "b", listOf(Split("a", 300))),
            ),
        )
        assertEquals(listOf(Settlement("b", "a", 400)), summary.pairwiseSettlements)
    }

    @Test
    fun `a recorded settlement clears its pairwise debt`() {
        val a = m("a"); val b = m("b")
        val summary = summarise(
            listOf(a, b),
            listOf(
                e("1", 500, "a", listOf(Split("a", 250), Split("b", 250))),
                e("2", 250, "b", listOf(Split("a", 250)), kind = ExpenseKind.REIMBURSEMENT),
            ),
        )
        assertTrue(summary.pairwiseSettlements.isEmpty())
        assertTrue(summary.settlements.isEmpty())
    }

    @Test
    fun `pairwise payments always sum to the same net balances`() {
        val ids = listOf("a", "b", "c", "d")
        val summary = summarise(
            ids.map { m(it) },
            listOf(
                e("1", 1200, "a", ids.map { Split(it, 300) }),
                e("2", 900, "c", listOf(Split("b", 450), Split("d", 450))),
                e("3", 400, "d", listOf(Split("a", 200), Split("c", 200))),
            ),
        )
        val net = HashMap<String, Long>()
        summary.pairwiseSettlements.forEach {
            net[it.fromMemberId] = (net[it.fromMemberId] ?: 0) - it.amountMinor
            net[it.toMemberId] = (net[it.toMemberId] ?: 0) + it.amountMinor
        }
        ids.forEach { assertEquals(summary.balanceOf(it), net[it] ?: 0L, "member $it") }
    }
}
