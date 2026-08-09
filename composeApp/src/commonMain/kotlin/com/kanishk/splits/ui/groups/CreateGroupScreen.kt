package com.kanishk.splits.ui.groups

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.model.SupportedCurrencies
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.theme.GroupEmojis
import com.kanishk.splits.ui.theme.avatarColor
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    onDone: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val savedName by repository.observeDisplayName().collectAsStateWithLifecycle("")

    var groupName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(GroupEmojis.first()) }
    var currency by remember { mutableStateOf("INR") }
    var myName by remember { mutableStateOf("") }
    var participant by remember { mutableStateOf("") }
    val participants = remember { mutableStateListOf<String>() }
    var saving by remember { mutableStateOf(false) }

    // Prefill the name they used last time, but never stomp on live typing.
    val effectiveMyName = myName.ifEmpty { savedName }
    val canCreate = groupName.isNotBlank() && effectiveMyName.isNotBlank() && !saving

    val participantFocus = remember { FocusRequester() }

    fun addParticipant() {
        val trimmed = participant.trim()
        if (trimmed.isNotEmpty() && participants.none { it.equals(trimmed, ignoreCase = true) }) {
            participants += trimmed
        }
        participant = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SplitsTopBar("New group", onBack = onBack) },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
            ) {
                Button(
                    onClick = {
                        if (!canCreate) return@Button
                        saving = true
                        scope.launch {
                            val id = repository.createGroup(
                                name = groupName,
                                emoji = emoji,
                                currencyCode = currency,
                                myName = effectiveMyName,
                                otherNames = participants.toList(),
                            )
                            onDone(id)
                        }
                    },
                    enabled = canCreate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Create group",
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
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
                                                RoundedCornerShape(16.dp),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { emoji = candidate },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(candidate, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Group")
                    VSpace(10.dp)
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        placeholder = { Text("Goa trip") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                                    .clickable { currency = option.code }
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
                Column {
                    SectionLabel("You")
                    VSpace(10.dp)
                    OutlinedTextField(
                        value = effectiveMyName,
                        onValueChange = { myName = it },
                        label = { Text("Your name") },
                        placeholder = { Text("How the group knows you") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(6.dp)
                    Text(
                        "You'll be the admin of this group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column {
                    SectionLabel("Participants")
                    VSpace(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = participant,
                            onValueChange = { participant = it },
                            label = { Text("Add a name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { addParticipant() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(participantFocus),
                        )
                        IconButton(
                            onClick = { addParticipant() },
                            enabled = participant.isNotBlank(),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PersonAddAlt,
                                contentDescription = "Add participant",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    VSpace(6.dp)
                    Text(
                        "No phone numbers needed — share the invite link and each person picks their own name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(participants, key = { _, name -> name }) { index, name ->
                SplitsCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name, index + 1, size = 34.dp)
                        Text(
                            name,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { participants.remove(name) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove $name",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(avatarColor(0).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("👤", fontSize = 13.sp)
                    }
                    Text(
                        "${participants.size + 1} people in this group",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
