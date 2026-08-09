package com.kanishk.splits.model

/** How an entry moves money. */
enum class ExpenseKind {
    /** A real cost the group incurred. Counts towards the group total. */
    EXPENSE,

    /**
     * One member handing money to another to square up. It changes who owes whom,
     * but it is *not* new spending, so it is excluded from the group total.
     */
    REIMBURSEMENT,
    ;

    companion object {
        fun fromDb(raw: String): ExpenseKind =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

data class Group(
    val id: String,
    val name: String,
    val emoji: String,
    val currencyCode: String,
    val inviteCode: String,
    val adminMemberId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val hidden: Boolean,
)

data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val colorIndex: Int,
    val claimedByDeviceId: String?,
) {
    val isClaimed: Boolean get() = claimedByDeviceId != null
}

data class Split(
    val memberId: String,
    val shareMinor: Long,
)

data class Expense(
    val id: String,
    val groupId: String,
    val title: String,
    val amountMinor: Long,
    val paidByMemberId: String,
    val kind: ExpenseKind,
    val categoryId: String?,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val splits: List<Split>,
) {
    val isReimbursement: Boolean get() = kind == ExpenseKind.REIMBURSEMENT

    /** For a reimbursement there is exactly one counterparty: the person being paid. */
    val paidToMemberId: String? get() = if (isReimbursement) splits.firstOrNull()?.memberId else null

    fun shareOf(memberId: String): Long = splits.firstOrNull { it.memberId == memberId }?.shareMinor ?: 0L
}

/** A whole group with everything needed to render it, loaded as one unit. */
data class GroupDetail(
    val group: Group,
    val members: List<Member>,
    val expenses: List<Expense>,
) {
    fun member(id: String?): Member? = members.firstOrNull { it.id == id }

    /** The member this device has claimed in this group, if any. */
    fun meIn(deviceId: String?): Member? =
        if (deviceId == null) null else members.firstOrNull { it.claimedByDeviceId == deviceId }

    fun isAdmin(deviceId: String?): Boolean {
        val me = meIn(deviceId) ?: return false
        return group.adminMemberId == me.id
    }

    /** Participants somebody has actually claimed on a device. */
    val joinedMembers: List<Member> get() = members.filter { it.isClaimed }

    /**
     * Names still up for grabs.
     *
     * A claimed name is not offered to anyone else — the point of the invite flow is that you
     * pick *your* name, and a list full of taken names invites exactly the mistake of tapping
     * someone else's. A name only returns here when its owner releases it or is removed.
     */
    val availableMembers: List<Member> get() = members.filter { !it.isClaimed }

    val joinedCount: Int get() = joinedMembers.size

    fun everyoneJoined(): Boolean = members.isNotEmpty() && joinedCount == members.size
}

/**
 * Whether [memberId] had anything to do with this entry.
 *
 * Both directions count: they either paid for it, or they owe a share of it. Filtering on only
 * one of those would quietly hide half of what someone was involved in.
 */
fun Expense.involves(memberId: String): Boolean =
    paidByMemberId == memberId || splits.any { it.memberId == memberId }

/** Filters to one member's involvement. A null id means "everyone", i.e. no filtering. */
fun List<Expense>.involving(memberId: String?): List<Expense> =
    if (memberId == null) this else filter { it.involves(memberId) }
