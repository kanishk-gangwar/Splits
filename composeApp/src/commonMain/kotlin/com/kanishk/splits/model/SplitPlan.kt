package com.kanishk.splits.model

enum class SplitMode { Equally, Exact, Percent }

/** Percentages are held to two decimals, so 100% is 10_000 hundredths. */
const val FULL_PERCENT = 10_000L

/**
 * Turns what the user has typed into concrete per-member shares.
 *
 * The rule that matters: a row the user has *not* typed into is an automatic row. Pinning an
 * exact amount for one person must not strand everyone else on zero — whatever is left over is
 * shared evenly across the rows still floating. Clearing a field hands that row back to
 * automatic, which is why a blank string is treated as "no opinion" rather than as zero.
 *
 * Kept out of the Compose layer so it can be tested directly; the editor is a thin shell
 * around this.
 */
fun planSplits(
    kind: ExpenseKind,
    amountMinor: Long,
    participantIds: List<String>,
    paidToMemberId: String?,
    mode: SplitMode,
    typed: Map<String, String>,
): List<Split> {
    if (kind == ExpenseKind.REIMBURSEMENT) {
        val recipient = paidToMemberId ?: return emptyList()
        if (amountMinor <= 0) return emptyList()
        // A settlement is a one-sided split: the whole amount lands on the person being paid,
        // which is what cancels the debt without counting as group spending.
        return listOf(Split(recipient, amountMinor))
    }

    if (participantIds.isEmpty() || amountMinor <= 0) return emptyList()

    return when (mode) {
        SplitMode.Equally ->
            splitEvenly(amountMinor, participantIds.size)
                .mapIndexed { index, share -> Split(participantIds[index], share) }

        SplitMode.Exact -> {
            val pinned = pinnedValues(participantIds, typed)
            val floating = participantIds.filterNot { it in pinned }
            val leftOver = (amountMinor - pinned.values.sum()).coerceAtLeast(0L)
            val auto = splitEvenly(leftOver, floating.size)

            participantIds.map { id ->
                val index = floating.indexOf(id)
                Split(id, if (index >= 0) auto[index] else pinned.getValue(id))
            }
        }

        SplitMode.Percent -> {
            val pinned = pinnedValues(participantIds, typed)
            val floating = participantIds.filterNot { it in pinned }
            val leftOver = (FULL_PERCENT - pinned.values.sum()).coerceAtLeast(0L)
            val auto = splitEvenly(leftOver, floating.size)

            val weights = participantIds.map { id ->
                val index = floating.indexOf(id)
                if (index >= 0) auto[index] else pinned.getValue(id)
            }

            if (weights.sum() == FULL_PERCENT) {
                // Exactly 100%: reconcile so the shares add back up to the total to the paisa.
                splitByWeights(amountMinor, weights)
                    .mapIndexed { index, share -> Split(participantIds[index], share) }
            } else {
                // Over or under 100%. Render it literally so the mismatch stays visible to the
                // user instead of being silently normalised away into a plausible-looking split.
                weights.mapIndexed { index, weight ->
                    Split(participantIds[index], amountMinor * weight / FULL_PERCENT)
                }
            }
        }
    }
}

/**
 * The rows the user actually typed a number into. A blank field counts as untouched, so
 * clearing a value returns that row to automatic distribution.
 */
private fun pinnedValues(
    participantIds: List<String>,
    typed: Map<String, String>,
): Map<String, Long> = participantIds.mapNotNull { id ->
    parseAmountToMinor(typed[id].orEmpty())?.let { id to it }
}.toMap()
