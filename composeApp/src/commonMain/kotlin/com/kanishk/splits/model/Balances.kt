package com.kanishk.splits.model

import kotlin.math.min

/** Net position of one member. Positive means the group owes them. */
data class MemberBalance(
    val memberId: String,
    val netMinor: Long,
) {
    val isSettled: Boolean get() = netMinor == 0L
}

/** "From owes To this much" — one suggested transfer to square the group up. */
data class Settlement(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
)

/** Everything the group screen needs to render, computed in one pass. */
data class GroupSummary(
    val totalSpentMinor: Long,
    val balances: List<MemberBalance>,
    val settlements: List<Settlement>,
) {
    fun balanceOf(memberId: String?): Long =
        balances.firstOrNull { it.memberId == memberId }?.netMinor ?: 0L
}

/**
 * Reimbursements move money between members exactly like an expense does — the payer is
 * credited and the recipient is debited — so they *do* affect balances. What they must not
 * do is inflate the group's spend, so [GroupSummary.totalSpentMinor] skips them entirely.
 */
fun summarise(members: List<Member>, expenses: List<Expense>): GroupSummary {
    val net = LinkedHashMap<String, Long>()
    members.forEach { net[it.id] = 0L }

    var totalSpent = 0L

    for (expense in expenses) {
        if (expense.kind == ExpenseKind.EXPENSE) {
            totalSpent += expense.amountMinor
        }
        net[expense.paidByMemberId] = (net[expense.paidByMemberId] ?: 0L) + expense.amountMinor
        for (split in expense.splits) {
            net[split.memberId] = (net[split.memberId] ?: 0L) - split.shareMinor
        }
    }

    val balances = net.map { (memberId, amount) -> MemberBalance(memberId, amount) }
    return GroupSummary(
        totalSpentMinor = totalSpent,
        balances = balances,
        settlements = suggestSettlements(balances),
    )
}

/**
 * Greedy min-cash-flow: repeatedly match the biggest debtor against the biggest creditor.
 * Produces at most (n - 1) transfers, which is what people actually want to see — not a
 * literal edge for every pairwise debt.
 */
fun suggestSettlements(balances: List<MemberBalance>): List<Settlement> {
    val debtors = balances.filter { it.netMinor < 0 }
        .map { it.memberId to -it.netMinor }
        .sortedByDescending { it.second }
        .toMutableList()
    val creditors = balances.filter { it.netMinor > 0 }
        .map { it.memberId to it.netMinor }
        .sortedByDescending { it.second }
        .toMutableList()

    val result = mutableListOf<Settlement>()
    var debtorIndex = 0
    var creditorIndex = 0

    while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
        val (debtorId, owed) = debtors[debtorIndex]
        val (creditorId, due) = creditors[creditorIndex]
        val amount = min(owed, due)

        if (amount > 0) {
            result += Settlement(debtorId, creditorId, amount)
        }

        val debtorLeft = owed - amount
        val creditorLeft = due - amount
        debtors[debtorIndex] = debtorId to debtorLeft
        creditors[creditorIndex] = creditorId to creditorLeft

        if (debtorLeft == 0L) debtorIndex++
        if (creditorLeft == 0L) creditorIndex++
    }

    return result
}

/**
 * What the home screen shows at the top: the device owner's net position rolled up across
 * every group they belong to. Groups in different currencies are kept apart, because
 * summing rupees and dollars into one number would be a lie.
 */
data class OverallPosition(
    val byCurrency: Map<String, Long>,
) {
    val isEmpty: Boolean get() = byCurrency.values.all { it == 0L }
    val owedToYou: Map<String, Long> get() = byCurrency.filterValues { it > 0 }
    val youOwe: Map<String, Long> get() = byCurrency.filterValues { it < 0 }.mapValues { -it.value }
}
