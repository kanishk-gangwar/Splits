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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.kanishk.splits.model.MemberBalance
import com.kanishk.splits.model.ReimbursementCategory
import com.kanishk.splits.model.dayKey
import com.kanishk.splits.model.expensePageCutoffMillis
import com.kanishk.splits.model.formatDayHeader
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.hasOccurredBefore
import com.kanishk.splits.model.involving
import com.kanishk.splits.model.nowMillis
import com.kanishk.splits.model.occurredOnOrAfter
import com.kanishk.splits.model.resolveCategory
import com.kanishk.splits.model.summarise
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.AvatarStack
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.HSpace
import com.kanishk.splits.ui.components.GlyphTile
import com.kanishk.splits.ui.components.MoneyText
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SegmentedControl
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.SyncRefreshBox
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.components.balanceColor
import com.kanishk.splits.ui.components.balanceContainerColor
import com.kanishk.splits.ui.theme.categoryTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onSettleUp: (String, String, Long) -> Unit,
    onOpenGroupSettings: () -> Unit,
    onOpenInsights: () -> Unit,
    onGroupGone: () -> Unit,
) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val detail by repository.observeGroupDetail(groupId).collectAsStateWithLifecycle(null)

    var tab by remember { mutableStateOf(0) }
    var showInvite by remember { mutableStateOf(false) }
    var showIdentityPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var filterMemberId by remember(groupId) { mutableStateOf<String?>(null) }
    var pagesLoaded by remember(groupId) { mutableIntStateOf(1) }
    var loadingMore by remember(groupId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val now = remember(groupId) { nowMillis() }

    val current = detail

    // The group can vanish underneath us — the admin deletes it and a sync pull brings the
    // tombstone down while this screen is open. `null` before the first emission is just the
    // initial load, so only bail out once we have actually seen the group and then lost it.
    var hasLoaded by remember(groupId) { mutableStateOf(false) }
    LaunchedEffect(current) {
        if (current != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onGroupGone()
        }
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

    // An archived group is a closed book: it keeps its history but nothing in it can change
    // until it is unarchived. Enforced here rather than only hiding buttons, so every entry
    // point into editing is covered.
    val readOnly = group.archived

    // Holding the invite shows you the group; adding to it needs a name. Until this device has
    // picked one there is nobody to attribute an entry to, so the add and record controls stay
    // away and the prompt below the header says why.
    val canWrite = !readOnly && me != null

    val cutoff = remember(now, pagesLoaded) { expensePageCutoffMillis(now, pagesLoaded) }
    val hasOlder = remember(current.expenses, filterMemberId, cutoff) {
        current.expenses.involving(filterMemberId).hasOccurredBefore(cutoff)
    }

    // Reveal the next three months when the last painted row is on screen. A short delay
    // keeps the footer spinner visible — the rows are already on disk, so without it the
    // next window would appear as if nothing happened.
    LaunchedEffect(groupId, listState) {
        snapshotFlow {
            if (tab != 0 || loadingMore || !hasOlder) return@snapshotFlow false
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad) {
                    loadingMore = true
                    delay(400)
                    pagesLoaded += 1
                    loadingMore = false
                }
            }
    }

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
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Insights") },
                                onClick = {
                                    showMenu = false
                                    onOpenInsights()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Insights, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Group settings") },
                                onClick = {
                                    showMenu = false
                                    onOpenGroupSettings()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Settings, contentDescription = null)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add expense", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        SyncRefreshBox(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupHeaderCard(
                    detail = current,
                    mine = summary.of(me?.id),
                    totalSpentMinor = summary.totalSpentMinor,
                )
            }

            if (readOnly) {
                item { ArchivedBanner(onOpenSettings = onOpenGroupSettings) }
            }

            if (me == null && !readOnly) {
                item {
                    IdentityPrompt(onPick = { showIdentityPicker = true })
                }
            }

            item {
                val owing = summary.settlementsFor(group.simplifyDebts).size
                SegmentedControl(
                    options = listOf(
                        "Expenses",
                        "Balances",
                        if (owing > 0) "Settle up $owing" else "Settle up",
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (tab == 2) {
                settleUpTab(
                    detail = current,
                    summary = summary,
                    canRecord = canWrite,
                    onSettleUp = onSettleUp,
                )
            } else if (tab == 0) {
                if (current.members.size > 1 && current.expenses.isNotEmpty()) {
                    item {
                        ParticipantFilterRow(
                            detail = current,
                            myMemberId = me?.id,
                            selectedId = filterMemberId,
                            onSelect = { filterMemberId = it },
                        )
                    }
                }

                expensesTab(
                    detail = current,
                    deviceId = repository.deviceId,
                    myMemberId = me?.id,
                    filterMemberId = filterMemberId,
                    cutoffMillis = cutoff,
                    loadingMore = loadingMore,
                    hasOlder = hasOlder,
                    readOnly = readOnly,
                    onEditExpense = onEditExpense,
                )
            } else if (tab == 1) {
                balancesTab(
                    detail = current,
                    summary = summary,
                    myMemberId = me?.id,
                )
            }
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
            claimedByMe = me?.id,
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
    mine: MemberBalance?,
    totalSpentMinor: Long,
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
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                AvatarStack(detail.members, max = 5, size = 30.dp)
            }

            // The two numbers people actually come here for: what this trip cost *me*, and
            // how much of it I fronted. Net position alone hides both.
            if (mine != null) {
                VSpace(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "Your share",
                        amountMinor = mine.spentMinor,
                        currencyCode = currency,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "You paid",
                        amountMinor = mine.paidMinor,
                        currencyCode = currency,
                        modifier = Modifier.weight(1f),
                    )
                }

                VSpace(10.dp)
                val net = mine.netMinor
                Surface(
                    color = balanceContainerColor(net),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                net > 0 -> "You are owed"
                                net < 0 -> "You owe"
                                else -> "You're all settled up"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = balanceColor(net),
                            modifier = Modifier.weight(1f),
                        )
                        if (net != 0L) {
                            MoneyText(
                                amountMinor = if (net < 0) -net else net,
                                currencyCode = currency,
                                color = balanceColor(net),
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

/** One quiet label-over-amount cell. Two of these read faster than a sentence. */
@Composable
private fun StatTile(
    label: String,
    amountMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
            )
            VSpace(3.dp)
            MoneyText(
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
            )
        }
    }
}

/** Says plainly why nothing on this screen can be changed, and how to undo it. */
@Composable
private fun ArchivedBanner(onOpenSettings: () -> Unit) {
    SplitsCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onOpenSettings,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    "Archived — read only",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "The history is all here, but nothing can be added or changed. Unarchive from group settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Filter the list down to one person's involvement — what they paid for, or owe a share of. */
@Composable
private fun ParticipantFilterRow(
    detail: GroupDetail,
    myMemberId: String?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    // Put whoever is using the phone first: "what was I part of" is the common question.
    val ordered = detail.members.sortedWith(
        compareByDescending<Member> { it.id == myMemberId }.thenBy { it.name.lowercase() }
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChipPill(
                label = "Everyone",
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
        }
        items(ordered, key = { it.id }) { member ->
            FilterChipPill(
                label = if (member.id == myMemberId) "${member.name} (you)" else member.name,
                selected = member.id == selectedId,
                colorIndex = member.colorIndex,
                onClick = { onSelect(if (member.id == selectedId) null else member.id) },
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colorIndex: Int? = null,
) {
    val tint = if (colorIndex != null) {
        com.kanishk.splits.ui.theme.avatarColor(colorIndex)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (colorIndex != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            HSpace(7.dp)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
                    "Pick your name to add expenses and see what you owe.",
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
    deviceId: String?,
    myMemberId: String?,
    filterMemberId: String?,
    cutoffMillis: Long,
    loadingMore: Boolean,
    hasOlder: Boolean,
    readOnly: Boolean,
    onEditExpense: (String) -> Unit,
) {
    if (detail.expenses.isEmpty()) {
        item {
            EmptyState(
                glyph = "💸",
                title = "No expenses yet",
                subtitle = if (readOnly) {
                    "This group was archived before anything was added to it."
                } else {
                    "Add the first one and everyone's share is worked out for you."
                },
            )
        }
        return
    }

    // "Involved" means they either paid for it or owe a share of it — see Expense.involves.
    // The list itself only paints three calendar months at a time; older rows stay off the
    // composition until the user scrolls into the footer loader.
    val visible = detail.expenses.involving(filterMemberId).occurredOnOrAfter(cutoffMillis)

    if (visible.isEmpty()) {
        if (hasOlder || loadingMore) {
            item(key = "load-more") { ExpensePageLoader() }
        } else {
            item {
                EmptyState(
                    glyph = "🔍",
                    title = "Nothing here for ${detail.member(filterMemberId)?.name ?: "them"}",
                    subtitle = "They haven't paid for anything or owed a share in this group yet.",
                )
            }
        }
        return
    }

    val sections = visible.groupBy { dayKey(it.occurredAt) }
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
            // An entry opens for editing only for whoever added it (or the admin). Everyone
            // else gets a plain row — there is nothing on the form they would be allowed to
            // change, so a tap that leads to a disabled Save would only confuse.
            val editable = !readOnly && detail.canEditExpense(deviceId, expense)
            ExpenseRow(
                expense = expense,
                detail = detail,
                myMemberId = myMemberId,
                onClick = if (editable) ({ onEditExpense(expense.id) }) else null,
            )
        }
    }

    if (loadingMore || hasOlder) {
        item(key = "load-more") {
            if (loadingMore) {
                ExpensePageLoader()
            } else {
                Box(Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}

@Composable
private fun ExpensePageLoader() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    detail: GroupDetail,
    myMemberId: String?,
    onClick: (() -> Unit)?,
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

                // Who this entry involves. The faces carry the names, so the line beside them
                // only has to say how wide the split was.
                val involved = expense.splits.mapNotNull { detail.member(it.memberId) }
                if (involved.isNotEmpty() && !expense.isReimbursement) {
                    VSpace(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(involved, max = 5, size = 20.dp)
                        HSpace(8.dp)
                        Text(
                            text = if (involved.size == detail.members.size) {
                                "everyone"
                            } else {
                                "${involved.size} people"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
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
                    VSpace(2.dp)
                    // Net alone never answers "but how much did *they* actually spend?".
                    Text(
                        text = "spent ${formatMinor(balance.spentMinor, currency)}" +
                            " · paid ${formatMinor(balance.paidMinor, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            balance.netMinor > 0 -> "gets back"
                            balance.netMinor < 0 -> "owes"
                            else -> "settled up"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
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
    }
}

// ---------------------------------------------------------------- settle up --

/**
 * The payments that clear the group, on their own tab. It used to sit under every member's
 * balance, which meant scrolling past all of them to reach the thing you were going to act on
 * — worse the bigger the group, which is exactly when it matters most.
 *
 * Which payments depends on the group's `simplifyDebts` setting: the fewest that square
 * everyone up, or every pairwise debt exactly as the expenses created it. The admin chooses
 * in group settings; the line at the top says which is showing.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.settleUpTab(
    detail: GroupDetail,
    summary: com.kanishk.splits.model.GroupSummary,
    canRecord: Boolean,
    onSettleUp: (String, String, Long) -> Unit,
) {
    val currency = detail.group.currencyCode
    val simplify = detail.group.simplifyDebts
    val settlements = summary.settlementsFor(simplify)

    if (settlements.isEmpty()) {
        item {
            EmptyState(
                glyph = "🎉",
                title = "Everyone's square",
                subtitle = "No outstanding balances in this group.",
            )
        }
        return
    }

    item {
        val count = settlements.size
        Text(
            text = if (simplify) {
                "The fewest payments that clear the group — $count in all."
            } else {
                "Who owes whom, exactly as the expenses add up — " +
                    "$count payment${if (count == 1) "" else "s"}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }

    items(settlements, key = { "s-${it.fromMemberId}-${it.toMemberId}" }) { settlement ->
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
                        "${from?.name ?: "?"} pays ${to?.name ?: "?"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoneyText(
                        amountMinor = settlement.amountMinor,
                        currencyCode = currency,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )
                }

                if (canRecord) {
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
    }
}
