package com.kanishk.splits.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes. Names are snake_case to match the Postgres columns exactly, so the RPC
 * functions can `to_jsonb(row)` and `->>'field'` without any translation layer.
 */

@Serializable
data class RemoteGroup(
    val id: String,
    val name: String,
    val emoji: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("admin_member_id") val adminMemberId: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class RemoteMember(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val name: String,
    @SerialName("color_index") val colorIndex: Int = 0,
    @SerialName("claimed_by_device_id") val claimedByDeviceId: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class RemoteExpense(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val title: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("paid_by_member_id") val paidByMemberId: String,
    val kind: String = "EXPENSE",
    @SerialName("category_id") val categoryId: String? = null,
    val note: String? = null,
    @SerialName("occurred_at") val occurredAt: Long,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class RemoteShare(
    @SerialName("expense_id") val expenseId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("share_minor") val shareMinor: Long,
)

/** What both `splits_pull` and `splits_resolve_invite` hand back. */
@Serializable
data class RemoteSnapshot(
    val found: Boolean = true,
    val groups: List<RemoteGroup> = emptyList(),
    val members: List<RemoteMember> = emptyList(),
    val expenses: List<RemoteExpense> = emptyList(),
    val shares: List<RemoteShare> = emptyList(),
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && members.isEmpty() && expenses.isEmpty() && shares.isEmpty()
}

@Serializable
data class PushPayload(
    val groups: List<RemoteGroup> = emptyList(),
    val members: List<RemoteMember> = emptyList(),
    val expenses: List<RemoteExpense> = emptyList(),
    val shares: List<RemoteShare> = emptyList(),
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && members.isEmpty() && expenses.isEmpty() && shares.isEmpty()
}

// The RPC argument envelopes — PostgREST takes named parameters as a JSON object body.

@Serializable
internal data class PullArgs(
    @SerialName("p_group_ids") val groupIds: List<String>,
    @SerialName("p_since") val since: Long,
)

@Serializable
internal data class InviteArgs(
    @SerialName("p_invite_code") val inviteCode: String,
)

@Serializable
internal data class PushArgs(
    @SerialName("p_payload") val payload: PushPayload,
)

@Serializable
internal data class DeleteGroupArgs(
    @SerialName("p_group_id") val groupId: String,
    @SerialName("p_device_id") val deviceId: String,
)

@Serializable
data class RpcResult(
    val ok: Boolean = false,
    val reason: String? = null,
)

@Serializable
internal data class PurgeArgs(
    @SerialName("p_retention_days") val retentionDays: Int,
)
