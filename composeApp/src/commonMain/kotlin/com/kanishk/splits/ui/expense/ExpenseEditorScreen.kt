package com.kanishk.splits.ui.expense

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.model.BuiltInCategories
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.FULL_PERCENT
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.SplitMode
import com.kanishk.splits.model.planSplits
import com.kanishk.splits.model.customCategoryId
import com.kanishk.splits.model.formatDate
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.minorToEditText
import com.kanishk.splits.model.nowMillis
import com.kanishk.splits.model.parseAmountToMinor
import com.kanishk.splits.model.resolveCategory
import com.kanishk.splits.model.symbolOf
import com.kanishk.splits.ui.ExpenseEditorRoute
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SegmentedControl
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.theme.categoryTint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(
    route: ExpenseEditorRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val detail by repository.observeGroupDetail(route.groupId).collectAsStateWithLifecycle(null)

    val editing = route.expenseId != null
    var loaded by remember { mutableStateOf(!editing) }
    var existing by remember { mutableStateOf<Expense?>(null) }

    // Form state.
    var kind by remember {
        mutableStateOf(
            if (route.presetKind == "REIMBURSEMENT") ExpenseKind.REIMBURSEMENT else ExpenseKind.EXPENSE
        )
    }
    var amountText by remember { mutableStateOf(minorToEditText(route.presetAmountMinor)) }
    var title by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var paidBy by remember { mutableStateOf(route.presetFromMemberId) }
    var paidTo by remember { mutableStateOf(route.presetToMemberId) }
    var occurredAt by remember { mutableStateOf(nowMillis()) }
    var note by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(SplitMode.Equally) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val shareText = remember { mutableStateMapOf<String, String>() }

    var showPaidByPicker by remember { mutableStateOf(false) }
    var showPaidToPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = detail

    // Load an existing expense once, then let the user's edits own the state.
    LaunchedEffect(route.expenseId) {
        val id = route.expenseId ?: return@LaunchedEffect
        val loadedExpense = repository.loadExpense(id)
        if (loadedExpense != null) {
            existing = loadedExpense
            kind = loadedExpense.kind
            amountText = minorToEditText(loadedExpense.amountMinor)
            title = loadedExpense.title
            categoryId = loadedExpense.categoryId
            paidBy = loadedExpense.paidByMemberId
            paidTo = loadedExpense.paidToMemberId
            occurredAt = loadedExpense.occurredAt
            note = loadedExpense.note.orEmpty()
            loadedExpense.splits.forEach { split ->
                selected[split.memberId] = true
                shareText[split.memberId] = minorToEditText(split.shareMinor)
            }
            // Uneven shares mean this was not an equal split.
            val distinct = loadedExpense.splits.map { it.shareMinor }.distinct()
            if (distinct.size > 1) splitMode = SplitMode.Exact
        }
        loaded = true
    }

    // Default everyone in, and default the payer to whoever this device is.
    LaunchedEffect(current, loaded) {
        val group = current ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        if (selected.isEmpty() && route.expenseId == null) {
            group.members.forEach { selected[it.id] = true }
        }
        if (paidBy == null) {
            paidBy = group.meIn(repository.deviceId)?.id ?: group.members.firstOrNull()?.id
        }
    }

    if (current == null || !loaded) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    val currency = current.group.currencyCode
    val amountMinor = parseAmountToMinor(amountText) ?: 0L
    val isReimbursement = kind == ExpenseKind.REIMBURSEMENT

    val participants = current.members.filter { selected[it.id] == true }
    val computedSplits = planSplits(
        kind = kind,
        amountMinor = amountMinor,
        participantIds = participants.map { it.id },
        paidToMemberId = paidTo,
        mode = splitMode,
        typed = shareText,
    )
    val splitTotal = computedSplits.sumOf { it.shareMinor }
    val splitBalanced = splitTotal == amountMinor

    val canSave = amountMinor > 0 &&
        paidBy != null &&
        computedSplits.isNotEmpty() &&
        splitBalanced &&
        (!isReimbursement || (paidTo != null && paidTo != paidBy))

    fun save() {
        val payer = paidBy ?: return
        val resolvedTitle = when {
            title.isNotBlank() -> title
            isReimbursement -> {
                val from = current.member(payer)?.name ?: "Someone"
                val to = current.member(paidTo)?.name ?: "someone"
                "$from → $to"
            }
            else -> resolveCategory(categoryId)?.label ?: "Expense"
        }
        scope.launch {
            repository.saveExpense(
                groupId = route.groupId,
                expenseId = route.expenseId,
                title = resolvedTitle,
                amountMinor = amountMinor,
                paidByMemberId = payer,
                kind = kind,
                categoryId = if (isReimbursement) null else categoryId,
                note = note,
                occurredAt = occurredAt,
                splits = computedSplits,
            )
            onDone()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SplitsTopBar(
                title = if (editing) "Edit entry" else if (isReimbursement) "Record settlement" else "New expense",
                onBack = onBack,
                actions = {
                    if (editing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    // One combined inset, not two. The IME inset already spans the navigation
                    // bar, so padding for both lifted the button a nav-bar's height too high —
                    // which is what put it on top of the amount field.
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(16.dp),
            ) {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (editing) "Save changes" else if (isReimbursement) "Record settlement" else "Add expense",
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // The amount sits outside the scrolling area. It is the one field you always want
            // in view while the keyboard is up; inside the list it could scroll away or end up
            // behind the button.
            AmountField(
                value = amountText,
                onValueChange = { amountText = it },
                currencyCode = currency,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                SegmentedControl(
                    options = listOf("Expense", "Settlement"),
                    selectedIndex = if (isReimbursement) 1 else 0,
                    onSelect = { index ->
                        kind = if (index == 1) ExpenseKind.REIMBURSEMENT else ExpenseKind.EXPENSE
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    text = if (isReimbursement) {
                        "A settlement moves money between two people. It updates balances but is left out of the group total."
                    } else {
                        "A shared cost, split between whoever it was for."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (isReimbursement) {
                item {
                    TransferRow(
                        detail = current,
                        fromId = paidBy,
                        toId = paidTo,
                        onPickFrom = { showPaidByPicker = true },
                        onPickTo = { showPaidToPicker = true },
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What was it for?") },
                        placeholder = { Text("Dinner at Toit") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    CategoryPicker(
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                        onCustom = { showCustomCategory = true },
                    )
                }

                item {
                    PickerRow(
                        label = "Paid by",
                        value = current.member(paidBy)?.name ?: "Choose",
                        onClick = { showPaidByPicker = true },
                    )
                }

                item {
                    SplitSection(
                        detail = current,
                        selected = selected,
                        shareText = shareText,
                        splitMode = splitMode,
                        onModeChange = { splitMode = it },
                        amountMinor = amountMinor,
                        computedSplits = computedSplits,
                        balanced = splitBalanced,
                        splitTotal = splitTotal,
                    )
                }
            }

            item {
                PickerRow(
                    label = "Date",
                    value = formatDate(occurredAt),
                    onClick = { showDatePicker = true },
                    trailingIcon = Icons.Outlined.CalendarToday,
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }
        }
    }

    if (showPaidByPicker) {
        MemberPickerSheet(
            title = if (isReimbursement) "Who paid?" else "Paid by",
            members = current.members,
            selectedId = paidBy,
            onDismiss = { showPaidByPicker = false },
            onPick = {
                paidBy = it
                showPaidByPicker = false
            },
        )
    }

    if (showPaidToPicker) {
        MemberPickerSheet(
            title = "Who received it?",
            members = current.members.filter { it.id != paidBy },
            selectedId = paidTo,
            onDismiss = { showPaidToPicker = false },
            onPick = {
                paidTo = it
                showPaidToPicker = false
            },
        )
    }

    if (showCustomCategory) {
        CustomCategoryDialog(
            onDismiss = { showCustomCategory = false },
            onConfirm = { label ->
                categoryId = customCategoryId(label)
                showCustomCategory = false
            },
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { occurredAt = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("It will be removed from the group and balances will be recalculated.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = route.expenseId
                    if (id != null) scope.launch { repository.deleteExpense(id) }
                    confirmDelete = false
                    onDone()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

// ----------------------------------------------------------------- sections --

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currencyCode: String,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                symbolOf(currencyCode).trim(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    // Only ever accept something that parses, so the field can't hold nonsense.
                    val cleaned = input.filter { it.isDigit() || it == '.' }
                    if (cleaned.count { it == '.' } <= 1) onValueChange(cleaned)
                },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 44.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) {
                            Text(
                                "0",
                                fontSize = 44.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.width(IntrinsicWidthMin).padding(start = 6.dp),
            )
        }
    }
}

/** Keeps the amount field from collapsing to nothing when empty. */
private val IntrinsicWidthMin = 200.dp

@Composable
private fun CategoryPicker(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCustom: () -> Unit,
) {
    val custom = resolveCategory(selectedId)?.takeIf { it.isCustom }

    Column {
        SectionLabel("Category (optional)")
        VSpace(10.dp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CategoryChip(
                    glyph = "🚫",
                    label = "None",
                    selected = selectedId == null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSelect(null) },
                )
            }
            if (custom != null) {
                item {
                    CategoryChip(
                        glyph = custom.glyph,
                        label = custom.label,
                        selected = true,
                        tint = categoryTint(custom.toneIndex),
                        onClick = { },
                    )
                }
            }
            items(BuiltInCategories) { category ->
                CategoryChip(
                    glyph = category.glyph,
                    label = category.label,
                    selected = category.id == selectedId,
                    tint = categoryTint(category.toneIndex),
                    onClick = { onSelect(category.id) },
                )
            }
            item {
                CategoryChip(
                    glyph = "✏️",
                    label = "Custom",
                    selected = false,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onCustom,
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    glyph: String,
    label: String,
    selected: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer
            )
            .then(
                if (selected) Modifier.border(1.5.dp, tint, RoundedCornerShape(12.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 15.sp)
        Text(
            label,
            modifier = Modifier.padding(start = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    SplitsCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TransferRow(
    detail: GroupDetail,
    fromId: String?,
    toId: String?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
) {
    SplitsCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransferSlot(
                caption = "From",
                member = detail.member(fromId),
                onClick = onPickFrom,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TransferSlot(
                caption = "To",
                member = detail.member(toId),
                onClick = onPickTo,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TransferSlot(
    caption: String,
    member: Member?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(8.dp)
        Avatar(member?.name ?: "?", member?.colorIndex ?: 0, size = 44.dp)
        VSpace(6.dp)
        Text(
            member?.name ?: "Choose",
            style = MaterialTheme.typography.bodyMedium,
            color = if (member == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SplitSection(
    detail: GroupDetail,
    selected: MutableMap<String, Boolean>,
    shareText: MutableMap<String, String>,
    splitMode: SplitMode,
    onModeChange: (SplitMode) -> Unit,
    amountMinor: Long,
    computedSplits: List<Split>,
    balanced: Boolean,
    splitTotal: Long,
) {
    val currency = detail.group.currencyCode

    Column {
        SectionLabel("Split")
        VSpace(10.dp)
        SegmentedControl(
            options = listOf("Equally", "Exact", "Percent"),
            selectedIndex = splitMode.ordinal,
            onSelect = { index ->
                // Fields stay empty on purpose. An empty row is an *automatic* row that shows
                // its computed share as a placeholder; pre-filling every row would make them
                // all look hand-entered and defeat the redistribution.
                onModeChange(SplitMode.entries[index])
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (splitMode != SplitMode.Equally) {
            VSpace(8.dp)
            Text(
                text = if (splitMode == SplitMode.Exact) {
                    "Type an amount for anyone you want to fix. The rest is shared evenly across the others."
                } else {
                    "Type a percentage for anyone you want to fix. The remainder is split across the others."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        VSpace(12.dp)

        detail.members.forEach { member ->
            val isIn = selected[member.id] == true
            val share = computedSplits.firstOrNull { it.memberId == member.id }?.shareMinor ?: 0L

            SplitsCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isIn,
                        onCheckedChange = { checked ->
                            selected[member.id] = checked
                            // Release any pinned amount when someone leaves the split, or a
                            // number from a row that is no longer included keeps eating into
                            // the total.
                            if (!checked) shareText.remove(member.id)
                        },
                    )
                    Avatar(member.name, member.colorIndex, size = 32.dp)
                    Text(
                        member.name,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIn) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (isIn && splitMode != SplitMode.Equally) {
                        // An empty field means this row is worked out automatically, and its
                        // computed share shows as a greyed placeholder — so at a glance you can
                        // tell what you pinned from what the app filled in around you.
                        val autoHint = when {
                            splitMode == SplitMode.Exact -> minorToEditText(share)
                            amountMinor > 0 -> minorToEditText(share * FULL_PERCENT / amountMinor)
                            else -> "0"
                        }

                        OutlinedTextField(
                            value = shareText[member.id].orEmpty(),
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isDigit() || it == '.' }
                                if (cleaned.count { it == '.' } <= 1) shareText[member.id] = cleaned
                            },
                            placeholder = {
                                Text(
                                    autoHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.7f),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            prefix = if (splitMode == SplitMode.Exact) {
                                { Text(symbolOf(currency).trim()) }
                            } else {
                                null
                            },
                            suffix = if (splitMode == SplitMode.Percent) {
                                { Text("%") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            ),
                            modifier = Modifier.width(126.dp),
                        )
                    } else if (isIn) {
                        Text(
                            formatMinor(share, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!balanced && amountMinor > 0) {
            val difference = amountMinor - splitTotal
            Text(
                text = if (difference > 0) {
                    "${formatMinor(difference, currency)} still unassigned"
                } else {
                    "${formatMinor(-difference, currency)} over the total"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}
