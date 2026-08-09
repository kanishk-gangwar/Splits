package com.kanishk.splits.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.data.normaliseInviteCode
import com.kanishk.splits.model.GroupCard
import com.kanishk.splits.ui.components.VSpace

/**
 * Long-pressing a group opens this. Delete is only offered to the admin — everyone else
 * gets Hide, which removes the group from their own list without touching anyone else's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionsSheet(
    card: GroupCard,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleArchive: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val repository = LocalRepository.current
    val isAdmin = card.myMemberId != null && card.group.adminMemberId == card.myMemberId
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = "${card.group.emoji}  ${card.group.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            VSpace(4.dp)

            SheetAction(Icons.AutoMirrored.Outlined.Login, "Open group", onOpen)

            if (card.group.archived) {
                SheetAction(Icons.Outlined.Unarchive, "Unarchive", onToggleArchive)
            } else {
                SheetAction(
                    Icons.Outlined.Archive,
                    "Archive",
                    onToggleArchive,
                    subtitle = "Keeps the history, moves it off your main list",
                )
            }

            SheetAction(
                Icons.Outlined.VisibilityOff,
                "Hide from my list",
                onHide,
                subtitle = "Only affects this device. Unhide from Settings.",
            )

            if (isAdmin) {
                SheetAction(
                    Icons.Outlined.DeleteOutline,
                    "Delete group",
                    onDelete,
                    subtitle = "Removes it for everyone. You're the admin.",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
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
fun DeleteGroupDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$groupName\"?") },
        text = {
            Text(
                "This removes the group and every expense in it, for everyone. " +
                    "Archive it instead if you just want it off your list."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun JoinWithCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val normalised = normaliseInviteCode(code)
    val valid = normalised.length == 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group") },
        text = {
            Column {
                Text(
                    "Enter the 8-character code from the invite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VSpace(12.dp)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(12) },
                    singleLine = true,
                    label = { Text("Invite code") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSubmit(normalised) }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
