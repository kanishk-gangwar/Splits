package com.kanishk.splits.model

import kotlin.math.min

/**
 * Everything one member's money looks like in a group.
 *
 * [netMinor] is the position — positive means the group owes them — and includes settlements,
 * because a settlement genuinely moves money. [spentMinor] and [paidMinor] describe real
 * expenses only: what this person consumed, and what they fronted at the till. Keeping
 * settlements out of those two is what makes the members' spends add up to the group total.
 */
data class MemberBalance(
    val memberId: String,
    val netMinor: Long,
    val spentMinor: Long = 0L,
    val paidMinor: Long = 0L,
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
    /**
     * What each person owes each other person, straight from the expenses: the key is
     * (debtor, creditor) and the value is the gross amount, before the two directions of a
     * pair are netted against each other. [pairwiseSettlements] does the netting.
     */
    val pairDebts: Map<Pair<String, String>, Long> = emptyMap(),
) {
    /**
     * Deliberately lazy. The home screen keeps a summary per group but only ever reads
     * balances, and working out the fewest payments is the one expensive thing in here.
     */
    val settlements: List<Settlement> by lazy { suggestSettlements(balances) }

    /**
     * Every debt as the expenses created it, one line per pair of people who owe each other
     * anything, with the two directions netted. This is what "simplify debts" switched off
     * looks like in Splitwise: more payments than [settlements], but each one traceable to the
     * dinners it came from, which some groups prefer to a transfer to someone you never ate
     * with.
     */
    val pairwiseSettlements: List<Settlement> by lazy { netPairDebts(pairDebts) }

    /** The list the settle-up tab shows, by the group's [Group.simplifyDebts] setting. */
    fun settlementsFor(simplify: Boolean): List<Settlement> =
        if (simplify) settlements else pairwiseSettlements

    fun balanceOf(memberId: String?): Long =
        balances.firstOrNull { it.memberId == memberId }?.netMinor ?: 0L

    fun of(memberId: String?): MemberBalance? =
        balances.firstOrNull { it.memberId == memberId }

    /** This person's own share of the group's expenses — "what did I actually spend?". */
    fun spentBy(memberId: String?): Long = of(memberId)?.spentMinor ?: 0L

    /** What this person fronted at the till, which is usually not what they spent. */
    fun paidBy(memberId: String?): Long = of(memberId)?.paidMinor ?: 0L
}

/**
 * Reimbursements move money between members exactly like an expense does — the payer is
 * credited and the recipient is debited — so they *do* affect balances. What they must not
 * do is inflate the group's spend, so [GroupSummary.totalSpentMinor] skips them entirely.
 */
fun summarise(members: List<Member>, expenses: List<Expense>): GroupSummary {
    val net = LinkedHashMap<String, Long>()
    val spent = LinkedHashMap<String, Long>()
    val paid = LinkedHashMap<String, Long>()
    val pairDebts = LinkedHashMap<Pair<String, String>, Long>()
    members.forEach {
        net[it.id] = 0L
        spent[it.id] = 0L
        paid[it.id] = 0L
    }

    var totalSpent = 0L

    for (expense in expenses) {
        val contributions = expense.contributions
        if (expense.kind == ExpenseKind.EXPENSE) {
            totalSpent += expense.amountMinor
            for (payment in contributions) {
                paid[payment.memberId] = (paid[payment.memberId] ?: 0L) + payment.paidMinor
            }
            for (split in expense.splits) {
                spent[split.memberId] = (spent[split.memberId] ?: 0L) + split.shareMinor
            }
        }
        for (payment in contributions) {
            net[payment.memberId] = (net[payment.memberId] ?: 0L) + payment.paidMinor
        }
        for (split in expense.splits) {
            net[split.memberId] = (net[split.memberId] ?: 0L) - split.shareMinor
        }
        // With several payers, each share is owed to them in proportion to what they put in.
        // Your own share of something you paid for is not a debt to anyone. A settlement lands
        // here too: A paying B is a share of B's that A fronted, which is exactly a debt from B
        // to A and cancels what B owed.
        val owed = allocateShares(
            shares = expense.splits.map { it.shareMinor },
            paid = contributions.map { it.paidMinor },
        )
        expense.splits.forEachIndexed { row, split ->
            contributions.forEachIndexed { column, payment ->
                val amount = owed[row][column]
                if (split.memberId != payment.memberId && amount != 0L) {
                    val key = split.memberId to payment.memberId
                    pairDebts[key] = (pairDebts[key] ?: 0L) + amount
                }
            }
        }
    }

    val balances = net.map { (memberId, amount) ->
        MemberBalance(
            memberId = memberId,
            netMinor = amount,
            spentMinor = spent[memberId] ?: 0L,
            paidMinor = paid[memberId] ?: 0L,
        )
    }
    return GroupSummary(
        totalSpentMinor = totalSpent,
        balances = balances,
        pairDebts = pairDebts,
    )
}

/**
 * Collapses the two directions of every pair into one line. If A owes B 700 and B owes A
 * 300, that is one payment of 400 from A to B — nobody wants to hand money back and forth.
 * Biggest first, then by name, so the order is stable between renders.
 */
fun netPairDebts(pairDebts: Map<Pair<String, String>, Long>): List<Settlement> {
    val seen = HashSet<Pair<String, String>>()
    val result = mutableListOf<Settlement>()
    for ((debtor, creditor) in pairDebts.keys) {
        val forward = if (debtor < creditor) debtor to creditor else creditor to debtor
        if (!seen.add(forward)) continue
        val (x, y) = forward
        val net = (pairDebts[x to y] ?: 0L) - (pairDebts[y to x] ?: 0L)
        when {
            net > 0 -> result += Settlement(fromMemberId = x, toMemberId = y, amountMinor = net)
            net < 0 -> result += Settlement(fromMemberId = y, toMemberId = x, amountMinor = -net)
        }
    }
    return result.sortedWith(
        compareByDescending<Settlement> { it.amountMinor }
            .thenBy { it.fromMemberId }
            .thenBy { it.toMemberId }
    )
}

/**
 * The fewest payments that square the group up.
 *
 * Greedy matching (biggest debtor against biggest creditor) is the usual answer and it is
 * *not* minimal: on [4, 3, -1, -3, -3] it spends four transfers where three will do, because
 * it never notices that +3 and -3 cancel on their own. So instead, split the members into as
 * many independent zero-sum groups as possible — every such split saves exactly one payment —
 * and settle inside each group. A group of k people that cannot be split further always needs
 * k - 1 transfers, which greedy does produce, so greedy is safe once the split is right.
 *
 * Finding the best split is subset-sum, i.e. exponential; [MaxExactPartition] is where we stop
 * looking and fall back to plain greedy. Real groups are far smaller than that bound.
 */
fun suggestSettlements(balances: List<MemberBalance>): List<Settlement> {
    val active = balances.filter { it.netMinor != 0L }
    if (active.isEmpty()) return emptyList()
    return zeroSumGroups(active).flatMap { greedySettle(it) }
}

/**
 * Above this many people with a non-zero balance the exact search costs 3^n and stops being
 * worth it. Fifteen is already a very large group to be splitting a bill in.
 */
private const val MaxExactPartition = 15

/**
 * Splits members into the largest possible number of subsets that each sum to zero. One
 * subset is always possible (everyone together, since balances sum to zero by construction).
 */
private fun zeroSumGroups(active: List<MemberBalance>): List<List<MemberBalance>> {
    val n = active.size
    if (n > MaxExactPartition) return listOf(active)

    val full = (1 shl n) - 1
    val sum = LongArray(1 shl n)
    for (mask in 1..full) {
        val low = mask.countTrailingZeroBits()
        sum[mask] = sum[mask and (mask - 1)] + active[low].netMinor
    }

    // best[mask] = most zero-sum groups `mask` can be cut into, or -1 if it cannot be cut at
    // all; taken[mask] = the group to peel off to achieve that. Every candidate group is
    // anchored on the lowest member still in the mask, which is what keeps this at 3^n
    // instead of enumerating every subset of every subset twice over.
    val best = IntArray(1 shl n) { -1 }
    val taken = IntArray(1 shl n)
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
                if (remainder >= 0 && remainder + 1 > best[mask]) {
                    best[mask] = remainder + 1
                    taken[mask] = group
                }
            }
            if (sub == 0) break
            sub = (sub - 1) and rest
        }
    }

    if (best[full] <= 0) return listOf(active)

    val groups = mutableListOf<List<MemberBalance>>()
    var mask = full
    while (mask != 0) {
        val group = taken[mask]
        groups += active.filterIndexed { index, _ -> (group shr index) and 1 == 1 }
        mask = mask xor group
    }
    return groups
}

/**
 * Repeatedly match the biggest debtor against the biggest creditor. Optimal within a group
 * that has no zero-sum subset of its own, which is exactly what [zeroSumGroups] hands over.
 */
private fun greedySettle(balances: List<MemberBalance>): List<Settlement> {
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

/**
 * Splits [amount] across [weights] in proportion, in whole minor units, with the rounding
 * leftovers going to the largest remainders so the parts always add back up to [amount].
 */
/**
 * Who owes which payer how much: a table with a row per share and a column per payment, each
 * cell proportional to share × payment / total, in whole minor units.
 *
 * Rounding each row on its own keeps the rows right but can leave a payer's column a unit
 * off what they paid, and then the pairwise debts no longer add up to the balances. So the
 * cells are floored, and the leftover units handed out where both the row and the column are
 * still short — largest fractions first, then whatever pairing remains.
 *
 * When the two sides do not add up to the same total (a malformed row from sync), each share
 * is simply spread across the payers on its own.
 */
internal fun allocateShares(shares: List<Long>, paid: List<Long>): List<List<Long>> {
    if (paid.size <= 1 || shares.sum() != paid.sum() || paid.any { it < 0 } || shares.any { it < 0 }) {
        return shares.map { allocateProportionally(it, paid) }
    }
    val total = paid.sum()
    if (total == 0L) return shares.map { List(paid.size) { 0L } }

    val exact = shares.all { it <= EXACT_LIMIT } && paid.all { it <= EXACT_LIMIT }
    val cells = shares.map { share ->
        paid.map { p -> if (exact) share * p / total else (share.toDouble() * p / total).toLong() }
            .toMutableList()
    }
    val rowNeed = shares.indices.map { shares[it] - cells[it].sum() }.toMutableList()
    val columnNeed = paid.indices.map { col -> paid[col] - shares.indices.sumOf { cells[it][col] } }
        .toMutableList()

    val byFraction = shares.indices.flatMap { row -> paid.indices.map { col -> row to col } }
        .sortedByDescending { (row, col) -> if (exact) (shares[row] * paid[col]) % total else 0L }
    for ((row, col) in byFraction) {
        if (rowNeed[row] > 0 && columnNeed[col] > 0) {
            cells[row][col] += 1
            rowNeed[row] -= 1
            columnNeed[col] -= 1
        }
    }
    // Anything still short: pair the remaining row and column needs up in order. Both sides
    // need the same amount in total, so this always finishes.
    var col = 0
    for (row in shares.indices) {
        while (rowNeed[row] > 0) {
            while (columnNeed[col] == 0L) col++
            val step = minOf(rowNeed[row], columnNeed[col])
            cells[row][col] += step
            rowNeed[row] -= step
            columnNeed[col] -= step
        }
    }
    return cells
}

private const val EXACT_LIMIT = 3_000_000_000L

internal fun allocateProportionally(amount: Long, weights: List<Long>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    val total = weights.sum()
    if (total <= 0L) return weights.mapIndexed { i, _ -> if (i == 0) amount else 0L }
    if (weights.size == 1) return listOf(amount)
    // amount * weight stays exact in a Long up to ~3e9 each (₹3 crore in paise); beyond that
    // fall back to doubles, where being a paisa out is the lesser evil next to overflowing.
    val exact = amount <= EXACT_LIMIT && weights.all { it <= EXACT_LIMIT }
    val base = weights.map {
        if (exact) amount * it / total else (amount.toDouble() * it / total).toLong()
    }
    var leftover = amount - base.sum()
    val result = base.toMutableList()
    val byRemainder = weights.indices.sortedByDescending {
        if (exact) (amount * weights[it]) % total else 0L
    }
    for (index in byRemainder) {
        if (leftover == 0L) break
        result[index] += 1
        leftover -= 1
    }
    return result
}
