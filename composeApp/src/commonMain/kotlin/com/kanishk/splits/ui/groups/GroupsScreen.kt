package com.kanishk.splits.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.LocalSyncEngine
import com.kanishk.splits.data.overallPosition
import com.kanishk.splits.model.GroupCard
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.formatRelative
import com.kanishk.splits.ui.components.AvatarStack
import com.kanishk.splits.ui.components.BalancePill
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.GlyphTile
import com.kanishk.splits.ui.components.SegmentedControl
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SyncFailureStrip
import com.kanishk.splits.ui.components.SyncRefreshBox
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.theme.SplitsTheme
import com.kanishk.splits.ui.theme.avatarColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onJoinWithCode: (String) -> Unit,
) {
    val repository = LocalRepository.current
    val syncEngine = LocalSyncEngine.current
    val scope = rememberCoroutineScope()
    val cards by repository.observeGroupCards().collectAsStateWithLifecycle(emptyList())

    var showArchived by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<GroupCard?>(null) }
    var confirmDelete by remember { mutableStateOf<GroupCard?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val active = cards.filterNot { it.group.archived }
    val archived = cards.filter { it.group.archived }
    val visible = if (showArchived) archived else active

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("New group", modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        SyncRefreshBox(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Splits",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(
                            Icons.Outlined.AddLink,
                            contentDescription = "Join with a code",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SyncFailureStrip() }

            if (cards.isNotEmpty()) {
                item { OverallCard(cards) }
            }

            if (archived.isNotEmpty()) {
                item {
                    SegmentedControl(
                        options = listOf("Active (${active.size})", "Archived (${archived.size})"),
                        selectedIndex = if (showArchived) 1 else 0,
                        onSelect = { showArchived = it == 1 },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (visible.isEmpty()) {
                item {
                    if (showArchived) {
                        EmptyState(
                            glyph = "🗄️",
                            title = "Nothing archived",
                            subtitle = "Groups you archive are parked here, with their history intact.",
                        )
                    } else {
                        EmptyState(
                            glyph = "🧾",
                            title = "No groups yet",
                            subtitle = "Start one for a trip, a flat, or a dinner — then share the link so everyone can pick their name.",
                        )
                    }
                }
            }

            items(visible, key = { it.group.id }) { card ->
                GroupRow(
                    card = card,
                    onClick = { onOpenGroup(card.group.id) },
                    onLongClick = { sheetTarget = card },
                )
            }
        }
        }
    }

    sheetTarget?.let { card ->
        GroupActionsSheet(
            card = card,
            onDismiss = { sheetTarget = null },
            onOpen = {
                sheetTarget = null
                onOpenGroup(card.group.id)
            },
            onToggleArchive = {
                scope.launch { repository.setArchived(card.group.id, !card.group.archived) }
                sheetTarget = null
            },
            onHide = {
                scope.launch { repository.setHidden(card.group.id, true) }
                sheetTarget = null
            },
            onDelete = {
                sheetTarget = null
                confirmDelete = card
            },
        )
    }

    confirmDelete?.let { card ->
        DeleteGroupDialog(
            groupName = card.group.name,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                scope.launch {
                    if (syncEngine.deleteGroupEverywhere(card.group.id)) {
                        repository.deleteGroup(card.group.id)
                    }
                }
                confirmDelete = null
            },
        )
    }

    if (showJoinDialog) {
        JoinWithCodeDialog(
            onDismiss = { showJoinDialog = false },
            onSubmit = { code ->
                showJoinDialog = false
                onJoinWithCode(code)
            },
        )
    }
}

/**
 * The rollup at the top. Currencies are listed separately rather than summed — adding
 * rupees to dollars would produce a confident, wrong number.
 */
@Composable
private fun OverallCard(cards: List<GroupCard>) {
    val position = cards.overallPosition()
    val money = SplitsTheme.money

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(money.heroStart, money.heroEnd)))
            .padding(20.dp),
    ) {
        Column {
            Text(
                "Your position",
                style = MaterialTheme.typography.labelMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
            )
            VSpace(10.dp)

            if (position.isEmpty) {
                Text(
                    "All settled up",
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Text(
                    "Nothing owed in either direction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    PositionColumn("you are owed", position.owedToYou)
                    PositionColumn("you owe", position.youOwe)
                }
            }
        }
    }
}

@Composable
private fun PositionColumn(label: String, amounts: Map<String, Long>) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
        if (amounts.isEmpty()) {
            Text(
                "—",
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
            )
        } else {
            amounts.forEach { (code, amount) ->
                Text(
                    formatMinor(amount, code),
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    card: GroupCard,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val group = card.group
    SplitsCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (group.archived) 0.62f else 1f)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphTile(
                glyph = group.emoji,
                tint = avatarColor(group.name.length),
                size = 50.dp,
            )

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VSpace(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(card.members, max = 4, size = 22.dp)
                    Text(
                        text = buildString {
                            append("  ")
                            if (!card.isJoined) {
                                append("tap to pick your name")
                            } else {
                                append(formatMinor(card.summary.totalSpentMinor, group.currencyCode))
                                append(" spent · ")
                                append(formatRelative(card.lastActivityAt))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (card.isJoined) {
                BalancePill(
                    netMinor = card.myBalanceMinor,
                    currencyCode = group.currencyCode,
                    compact = true,
                )
            }
        }
    }
}
