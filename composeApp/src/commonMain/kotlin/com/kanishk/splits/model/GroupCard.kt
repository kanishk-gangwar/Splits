package com.kanishk.splits.model

/** One row on the home screen: a group plus everything needed to render it without a second query. */
data class GroupCard(
    val group: Group,
    val members: List<Member>,
    val summary: GroupSummary,
    val myMemberId: String?,
    val expenseCount: Int,
    val lastActivityAt: Long,
) {
    val myBalanceMinor: Long get() = summary.balanceOf(myMemberId)
    val isJoined: Boolean get() = myMemberId != null
}
