package com.kanishk.splits.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.ReimbursementCategory
import com.kanishk.splits.model.dayKey
import com.kanishk.splits.model.formatDayHeader
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.resolveCategory
import com.kanishk.splits.model.summarise
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.AvatarStack
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.GlyphTile
import com.kanishk.splits.ui.components.MoneyText
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SegmentedControl
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.components.balanceColor
import com.kanishk.splits.ui.theme.categoryTint
import kotlinx.coroutines.launch

@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onSettleUp: (String, String, Long) -> Unit,
    onOpenGroupSettings: () -> Unit,
    onGroupGone: () -> Unit,
) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val detail by repository.observeGroupDetail(groupId).collectAsStateWithLifecycle(null)

    var tab by remember { mutableStateOf(0) }
    var showInvite by remember { mutableStateOf(false) }
    var showIdentityPicker by remember { mutableStateOf(false) }

    val current = detail
    // The group can vanish underneath us when the admin deletes it.
    LaunchedEffect(current) {
        // `null` before the first emission is normal, so only bail once we've seen data.
    }

    if (current == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    val group = current.group
    val me = current.meIn(repository.deviceId)
    val summary = remember(current) { summarise(current.members, current.expenses) }
    val isAdmin = current.isAdmin(repository.deviceId)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SplitsTopBar(
                title = "${group.emoji}  ${group.name}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showInvite = true }) {
                        Icon(
                            Icons.Outlined.IosShare,
                            contentDescription = "Share invite",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenGroupSettings) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Group settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add expense", modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupHeaderCard(
                    detail = current,
                    myBalance = summary.balanceOf(me?.id),
                    totalSpentMinor = summary.totalSpentMinor,
                    isJoined = me != null,
                )
            }

            if (me == null) {
                item {
                    IdentityPrompt(onPick = { showIdentityPicker = true })
                }
            }

            item {
                SegmentedControl(
                    options = listOf("Expenses", "Balances"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (tab == 0) {
                expensesTab(
                    detail = current,
                    myMemberId = me?.id,
                    onEditExpense = onEditExpense,
                )
            } else {
                balancesTab(
                    detail = current,
                    summary = summary,
                    myMemberId = me?.id,
                    onSettleUp = onSettleUp,
                )
            }
        }
    }

    if (showInvite) {
        InviteSheet(
            groupName = group.name,
            inviteCode = group.inviteCode,
            onDismiss = { showInvite = false },
        )
    }

    if (showIdentityPicker) {
        IdentityPickerSheet(
            members = current.members,
            onDismiss = { showIdentityPicker = false },
            onPick = { memberId ->
                scope.launch { repository.claimMember(groupId, memberId) }
                showIdentityPicker = false
            },
        )
    }
}

// ------------------------------------------------------------------- header --

@Composable
private fun GroupHeaderCard(
    detail: GroupDetail,
    myBalance: Long,
    totalSpentMinor: Long,
    isJoined: Boolean,
) {
    val currency = detail.group.currencyCode
    SplitsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Total spent")
                    VSpace(4.dp)
                    MoneyText(
                        amountMinor = totalSpentMinor,
                        currencyCode = currency,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                AvatarStack(detail.members, max = 5, size = 30.dp)
            }

            if (isJoined) {
                VSpace(16.dp)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                myBalance > 0 -> "You are owed"
                                myBalance < 0 -> "You owe"
                                else -> "You're all settled up"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (myBalance != 0L) {
                            MoneyText(
                                amountMinor = if (myBalance < 0) -myBalance else myBalance,
                                currencyCode = currency,
                                color = balanceColor(myBalance),
                                fontSize = 18.sp,
                            )
                        }
                    }
                }
            }

            // Settlements are money moving, not money spent — say so once, here.
            val reimbursed = detail.expenses.count { it.isReimbursement }
            if (reimbursed > 0) {
                VSpace(10.dp)
                Text(
                    "$reimbursed settlement${if (reimbursed == 1) "" else "s"} recorded, not counted in the total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IdentityPrompt(onPick: () -> Unit) {
    SplitsCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Which one are you?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Pick your name so balances know who you are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Button(onClick = onPick, shape = RoundedCornerShape(12.dp)) { Text("Pick") }
        }
    }
}

// ----------------------------------------------------------------- expenses --

private fun androidx.compose.foundation.lazy.LazyListScope.expensesTab(
    detail: GroupDetail,
    myMemberId: String?,
    onEditExpense: (String) -> Unit,
) {
    if (detail.expenses.isEmpty()) {
        item {
            EmptyState(
                glyph = "💸",
                title = "No expenses yet",
                subtitle = "Add the first one and everyone's share is worked out for you.",
            )
        }
        return
    }

    val sections = detail.expenses.groupBy { dayKey(it.occurredAt) }
        .toList()
        .sortedByDescending { it.first }

    sections.forEach { (_, dayExpenses) ->
        item(key = "header-${dayExpenses.first().id}") {
            Text(
                formatDayHeader(dayExpenses.first().occurredAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
        items(dayExpenses, key = { it.id }) { expense ->
            ExpenseRow(
                expense = expense,
                detail = detail,
                myMemberId = myMemberId,
                onClick = { onEditExpense(expense.id) },
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    detail: GroupDetail,
    myMemberId: String?,
    onClick: () -> Unit,
) {
    val currency = detail.group.currencyCode
    val payer = detail.member(expense.paidByMemberId)
    val category = if (expense.isReimbursement) {
        ReimbursementCategory
    } else {
        resolveCategory(expense.categoryId)
    }

    // What this row did to *your* position, stated plainly.
    val myShare = expense.shareOf(myMemberId.orEmpty())
    val iPaid = myMemberId != null && expense.paidByMemberId == myMemberId
    val myDelta = (if (iPaid) expense.amountMinor else 0L) - myShare

    SplitsCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphTile(
                glyph = category?.glyph ?: "🧾",
                tint = categoryTint(category?.toneIndex ?: 20),
                size = 42.dp,
            )

            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = expense.title.ifBlank {
                        if (expense.isReimbursement) "Settlement" else "Expense"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VSpace(2.dp)
                Text(
                    text = if (expense.isReimbursement) {
                        val to = detail.member(expense.paidToMemberId)
                        "${payer?.name ?: "Someone"} paid ${to?.name ?: "someone"}"
                    } else {
                        "${payer?.name ?: "Someone"} paid ${formatMinor(expense.amountMinor, currency)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (expense.isReimbursement) {
                    Text(
                        "settlement",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                    MoneyText(
                        amountMinor = expense.amountMinor,
                        currencyCode = currency,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                } else {
                    Text(
                        text = when {
                            myMemberId == null -> "total"
                            myDelta > 0 -> "you lent"
                            myDelta < 0 -> "you borrowed"
                            else -> "not involved"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                    MoneyText(
                        amountMinor = if (myMemberId == null) {
                            expense.amountMinor
                        } else {
                            if (myDelta < 0) -myDelta else myDelta
                        },
                        currencyCode = currency,
                        color = if (myMemberId == null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            balanceColor(myDelta)
                        },
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- balances --

private fun androidx.compose.foundation.lazy.LazyListScope.balancesTab(
    detail: GroupDetail,
    summary: com.kanishk.splits.model.GroupSummary,
    myMemberId: String?,
    onSettleUp: (String, String, Long) -> Unit,
) {
    val currency = detail.group.currencyCode

    items(
        summary.balances.sortedByDescending { it.netMinor },
        key = { "bal-${it.memberId}" },
    ) { balance ->
        val member = detail.member(balance.memberId) ?: return@items
        SplitsCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    member.name,
                    member.colorIndex,
                    size = 40.dp,
                    ring = member.id == myMemberId,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (member.id == myMemberId) "${member.name} (you)" else member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            balance.netMinor > 0 -> "gets back"
                            balance.netMinor < 0 -> "owes"
                            else -> "settled up"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!balance.isSettled) {
                    MoneyText(
                        amountMinor = if (balance.netMinor < 0) -balance.netMinor else balance.netMinor,
                        currencyCode = currency,
                        color = balanceColor(balance.netMinor),
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }

    if (summary.settlements.isNotEmpty()) {
        item {
            Text(
                "Settle up in ${summary.settlements.size} payment${if (summary.settlements.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }

        items(summary.settlements, key = { "s-${it.fromMemberId}-${it.toMemberId}" }) { settlement ->
            val from = detail.member(settlement.fromMemberId)
            val to = detail.member(settlement.toMemberId)
            SplitsCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(from?.name ?: "?", from?.colorIndex ?: 0, size = 32.dp)
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "pays",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp).size(16.dp),
                    )
                    Avatar(to?.name ?: "?", to?.colorIndex ?: 0, size = 32.dp)

                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            "${from?.name ?: "?"} → ${to?.name ?: "?"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MoneyText(
                            amountMinor = settlement.amountMinor,
                            currencyCode = currency,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }

                    TextButton(
                        onClick = {
                            onSettleUp(
                                settlement.fromMemberId,
                                settlement.toMemberId,
                                settlement.amountMinor,
                            )
                        },
                    ) {
                        Text("Record")
                    }
                }
            }
        }
    } else {
        item {
            EmptyState(
                glyph = "🎉",
                title = "Everyone's square",
                subtitle = "No outstanding balances in this group.",
            )
        }
    }
}
