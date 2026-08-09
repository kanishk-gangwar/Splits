package com.kanishk.splits.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.LocalSyncEngine
import com.kanishk.splits.model.SupportedCurrencies
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.groups.DeleteGroupDialog
import com.kanishk.splits.ui.theme.GroupEmojis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    onLeftGroupList: () -> Unit,
) {
    val repository = LocalRepository.current
    val syncEngine = LocalSyncEngine.current
    val scope = rememberCoroutineScope()
    val detail by repository.observeGroupDetail(groupId).collectAsStateWithLifecycle(null)

    var showInvite by remember { mutableStateOf(false) }
    var showIdentityPicker by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeBlocked by remember { mutableStateOf<String?>(null) }

    val current = detail
    if (current == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    val group = current.group
    val me = current.meIn(repository.deviceId)
    val isAdmin = current.isAdmin(repository.deviceId)

    var name by remember(group.id) { mutableStateOf(group.name) }
    var emoji by remember(group.id) { mutableStateOf(group.emoji) }
    var currency by remember(group.id) { mutableStateOf(group.currencyCode) }

    fun persistGroup() {
        scope.launch { repository.updateGroup(groupId, name, emoji, currency) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SplitsTopBar("Group settings", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    SectionLabel("Icon")
                    VSpace(10.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(GroupEmojis) { candidate ->
                            val selected = candidate == emoji
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        }
                                    )
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(15.dp),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        emoji = candidate
                                        persistGroup()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(candidate, fontSize = 21.sp)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    SectionLabel("Currency")
                    VSpace(10.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SupportedCurrencies) { option ->
                            val selected = option.code == currency
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        }
                                    )
                                    .clickable {
                                        currency = option.code
                                        persistGroup()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    "${option.symbol.trim()} ${option.code}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Participants", Modifier.weight(1f))
                    TextButton(onClick = { showAddMember = true }) {
                        Icon(
                            Icons.Outlined.PersonAddAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Add", Modifier.padding(start = 6.dp))
                    }
                }
            }

            items(current.members, key = { it.id }) { member ->
                SplitsCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            member.name,
                            member.colorIndex,
                            size = 38.dp,
                            ring = member.id == me?.id,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                if (member.id == me?.id) "${member.name} (you)" else member.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    if (member.id == group.adminMemberId) append("admin · ")
                                    append(if (member.isClaimed) "joined" else "not joined yet")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { renaming = member.id to member.name }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Rename ${member.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val removed = repository.removeMember(groupId, member.id)
                                    if (!removed) removeBlocked = member.name
                                }
                            },
                            enabled = member.id != group.adminMemberId,
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove ${member.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            item {
                VSpace(4.dp)
                SectionLabel("This group")
            }

            item {
                ActionCard(
                    icon = Icons.Outlined.IosShare,
                    title = "Share invite",
                    subtitle = "Code ${group.inviteCode}",
                    onClick = { showInvite = true },
                )
            }

            item {
                ActionCard(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Change who you are",
                    subtitle = me?.let { "Currently ${it.name}" } ?: "Not picked yet",
                    onClick = { showIdentityPicker = true },
                )
            }

            item {
                ActionCard(
                    icon = if (group.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    title = if (group.archived) "Unarchive group" else "Archive group",
                    subtitle = "Keeps everything, moves it off your main list",
                    onClick = {
                        scope.launch { repository.setArchived(groupId, !group.archived) }
                    },
                )
            }

            item {
                ActionCard(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Hide from my list",
                    subtitle = "Only on this device. Unhide from Settings.",
                    onClick = {
                        scope.launch {
                            repository.setHidden(groupId, true)
                            onLeftGroupList()
                        }
                    },
                )
            }

            if (isAdmin) {
                item {
                    ActionCard(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Delete group",
                        subtitle = "Removes it for everyone. Only you can do this.",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { confirmDelete = true },
                    )
                }
            } else {
                item {
                    Text(
                        "Only the group admin can delete this group. You can hide or archive it instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    // The name field has no Save button, so it autosaves once typing pauses.
    LaunchedEffect(name) {
        if (name.isNotBlank() && name != group.name) {
            delay(400)
            repository.updateGroup(groupId, name, emoji, currency)
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

    if (showAddMember) {
        NameDialog(
            title = "Add participant",
            initial = "",
            confirmLabel = "Add",
            onDismiss = { showAddMember = false },
            onConfirm = { value ->
                scope.launch { repository.addMember(groupId, value) }
                showAddMember = false
            },
        )
    }

    renaming?.let { (memberId, currentName) ->
        NameDialog(
            title = "Rename participant",
            initial = currentName,
            confirmLabel = "Save",
            onDismiss = { renaming = null },
            onConfirm = { value ->
                scope.launch { repository.renameMember(memberId, value) }
                renaming = null
            },
        )
    }

    if (confirmDelete) {
        DeleteGroupDialog(
            groupName = group.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                scope.launch {
                    if (syncEngine.deleteGroupEverywhere(groupId)) {
                        repository.deleteGroup(groupId)
                        onLeftGroupList()
                    }
                }
                confirmDelete = false
            },
        )
    }

    removeBlocked?.let { blockedName ->
        AlertDialog(
            onDismissRequest = { removeBlocked = null },
            title = { Text("Can't remove $blockedName") },
            text = {
                Text(
                    "$blockedName appears in at least one expense. Delete or reassign those " +
                        "entries first, then remove them."
                )
            },
            confirmButton = { TextButton(onClick = { removeBlocked = null }) { Text("Got it") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    SplitsCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(32) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
