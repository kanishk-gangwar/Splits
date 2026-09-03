package com.kanishk.splits.ui

import kotlinx.serialization.Serializable

@Serializable
data object GroupsRoute

@Serializable
data object CreateGroupRoute

@Serializable
data class GroupRoute(val groupId: String)

@Serializable
data class GroupSettingsRoute(val groupId: String)

/**
 * One editor serves new expenses, edits, and settle-up. The settle-up entry points prefill
 * [presetKind], the two members, and the amount, so the user only has to confirm.
 *
 * [presetSettlementId] is the id a suggested settlement should be saved under. It is worked
 * out from the suggestion itself (see `settlementId`), so two people recording the same
 * payment write the same row instead of two — the editor only honours it while the prefilled
 * values are untouched.
 */
@Serializable
data class ExpenseEditorRoute(
    val groupId: String,
    val expenseId: String? = null,
    val presetKind: String = "EXPENSE",
    val presetFromMemberId: String? = null,
    val presetToMemberId: String? = null,
    val presetAmountMinor: Long = 0L,
    val presetSettlementId: String? = null,
)

@Serializable
data class JoinRoute(val inviteCode: String)

@Serializable
data object SettingsRoute
