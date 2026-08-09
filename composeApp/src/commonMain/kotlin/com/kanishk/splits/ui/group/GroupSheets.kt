package com.kanishk.splits.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanishk.splits.data.copyToClipboard
import com.kanishk.splits.data.shareText
import com.kanishk.splits.inviteLink
import com.kanishk.splits.inviteShareMessage
import com.kanishk.splits.model.Member
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.VSpace

/**
 * The share flow. The link carries the code in its fragment so the web page can resolve it
 * client-side, and the code is shown separately for anyone typing it in by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    groupName: String,
    inviteCode: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Invite to $groupName",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            VSpace(6.dp)
            Text(
                "Anyone who opens this picks their own name from the list. No phone numbers, no sign-up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            VSpace(20.dp)

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "INVITE CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                    VSpace(4.dp)
                    Text(
                        inviteCode.chunked(4).joinToString(" "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 4.sp,
                    )
                }
            }

            VSpace(10.dp)
            Text(
                inviteLink(inviteCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            VSpace(20.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard(inviteLink(inviteCode))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                    Text(if (copied) "Copied" else "Copy link", Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { shareText(inviteShareMessage(groupName, inviteCode)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.IosShare, contentDescription = null, Modifier.size(18.dp))
                    Text("Share", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * "Who are you?" — the whole identity model in one screen. Names already claimed by another
 * device are shown locked so two people can't both be Aarav.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityPickerSheet(
    members: List<Member>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    claimedByMe: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // Names held by somebody else are left out entirely rather than shown greyed. A list
        // full of unavailable names is an invitation to tap the wrong one, and the only thing
        // a locked row tells you is that it is not for you.
        val selectable = members.filter { !it.isClaimed || it.id == claimedByMe }
        val takenByOthers = members.count { it.isClaimed && it.id != claimedByMe }

        Column(Modifier.navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(
                "Which one are you?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = if (selectable.isEmpty()) {
                    "Every name here is already in use on another device."
                } else {
                    "Tap your name to claim it on this device."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            VSpace(10.dp)

            selectable.forEach { member ->
                MemberPickRow(
                    member = member,
                    locked = false,
                    isMe = member.id == claimedByMe,
                    onClick = { onPick(member.id) },
                )
            }

            if (takenByOthers > 0) {
                Text(
                    text = "$takenByOthers other name${if (takenByOthers == 1) " is" else "s are"} " +
                        "already claimed. They free up if that person leaves the group or " +
                        "switches to a different name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MemberPickRow(
    member: Member,
    locked: Boolean,
    isMe: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(member.name, member.colorIndex, size = 40.dp, ring = isMe)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                member.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (locked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (locked) {
                Text(
                    "already claimed on another device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (isMe) {
                Text(
                    "that's you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (locked) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
