package com.kanishk.splits.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.data.deviceLabel
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SegmentedControl
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()

    val savedName by repository.observeDisplayName().collectAsStateWithLifecycle("")
    val themePref by repository.observeThemeMode().collectAsStateWithLifecycle("system")
    val hiddenGroups by repository.observeHiddenGroups().collectAsStateWithLifecycle(emptyList())

    var nameField by remember { mutableStateOf<String?>(null) }
    val displayedName = nameField ?: savedName

    // Autosave the display name shortly after typing stops.
    LaunchedEffect(nameField) {
        val value = nameField ?: return@LaunchedEffect
        delay(400)
        repository.setDisplayName(value)
    }

    val themeMode = ThemeMode.fromPref(themePref)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SplitsTopBar("Settings", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    SectionLabel("Your default name")
                    VSpace(10.dp)
                    OutlinedTextField(
                        value = displayedName,
                        onValueChange = { nameField = it },
                        label = { Text("Name") },
                        placeholder = { Text("Used when you create a group") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(6.dp)
                    Text(
                        "This only prefills forms. In each group you pick which participant you are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column {
                    SectionLabel("Appearance")
                    VSpace(10.dp)
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = themeMode.ordinal,
                        onSelect = { index ->
                            scope.launch {
                                repository.setThemeMode(ThemeMode.entries[index].pref)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Column {
                    SectionLabel("Hidden groups")
                    VSpace(6.dp)
                    Text(
                        if (hiddenGroups.isEmpty()) {
                            "Nothing hidden. Hiding a group removes it from this device only — everyone else keeps theirs."
                        } else {
                            "These are hidden on this device only."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(hiddenGroups, key = { it.id }) { group ->
                SplitsCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(group.emoji, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            group.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = {
                            scope.launch { repository.setHidden(group.id, false) }
                        }) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Unhide", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("This device")
                    VSpace(10.dp)
                    SplitsCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                deviceLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            VSpace(4.dp)
                            Text(
                                "ID ${repository.deviceId.take(12)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VSpace(10.dp)
                            Text(
                                "Your identity in each group is tied to this device. There is no account " +
                                    "and no phone number.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
