package com.kanishk.splits.ui.join

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.LocalSyncEngine
import com.kanishk.splits.model.Group
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import kotlinx.coroutines.launch

/**
 * The whole point of requirement 1: an invite link lands here, the group's participant names
 * are listed, and the visitor taps whichever one is them. No phone number is ever asked for.
 */
@Composable
fun JoinScreen(
    inviteCode: String,
    onJoined: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = LocalRepository.current
    val syncEngine = LocalSyncEngine.current
    val scope = rememberCoroutineScope()

    var group by remember { mutableStateOf<Group?>(null) }
    var resolved by remember { mutableStateOf(false) }

    LaunchedEffect(inviteCode) {
        // A code shared by someone else won't be on this device yet, so ask the server.
        group = repository.findGroupByInvite(inviteCode)
            ?: run {
                syncEngine.fetchInvite(inviteCode)
                repository.findGroupByInvite(inviteCode)
            }
        resolved = true
    }

    val found = group
    val detail by remember(found?.id) {
        if (found == null) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            repository.observeGroupDetail(found.id)
        }
    }.collectAsStateWithLifecycle(null)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SplitsTopBar("Join group", onBack = onBack) },
    ) { padding ->
        when {
            !resolved -> Box(Modifier.fillMaxSize().padding(padding))

            found == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    glyph = "🔍",
                    title = "That invite didn't match anything",
                    subtitle = "Code $inviteCode doesn't match any group we could reach. Check the " +
                        "code, or ask the admin to re-share the invite link.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                val members = detail?.members.orEmpty()
                val myId = detail?.meIn(repository.deviceId)?.id

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(found.emoji, fontSize = 32.sp)
                            }
                            VSpace(14.dp)
                            Text(
                                found.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            VSpace(6.dp)
                            Text(
                                "${members.size} people · pick your name to join",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    items(members, key = { it.id }) { member ->
                        val takenByOther = member.isClaimed && member.id != myId
                        SplitsCard(
                            Modifier.fillMaxWidth(),
                            onClick = if (takenByOther) {
                                null
                            } else {
                                {
                                    scope.launch {
                                        repository.claimMember(found.id, member.id)
                                        onJoined(found.id)
                                    }
                                }
                            },
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(
                                    member.name,
                                    member.colorIndex,
                                    size = 42.dp,
                                    ring = member.id == myId,
                                )
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(
                                        member.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (takenByOther) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = when {
                                            member.id == myId -> "that's you"
                                            takenByOther -> "already claimed on another device"
                                            else -> "tap to claim"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (member.id == myId) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                Icon(
                                    if (takenByOther) Icons.Outlined.Lock else Icons.Outlined.PersonAddAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            "Not on the list? Ask the admin to add your name, then reopen this link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
