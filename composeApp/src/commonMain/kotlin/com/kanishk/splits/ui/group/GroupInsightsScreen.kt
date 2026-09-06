package com.kanishk.splits.ui.group

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.MemberMonthInsight
import com.kanishk.splits.model.MonthYear
import com.kanishk.splits.model.abbreviatedMonth
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.formatMonthYear
import com.kanishk.splits.model.monthInsights
import com.kanishk.splits.model.monthYearOf
import com.kanishk.splits.model.resolveCategory
import com.kanishk.splits.model.todayMonthYear
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.GlyphTile
import com.kanishk.splits.ui.components.HSpace
import com.kanishk.splits.ui.components.MoneyText
import com.kanishk.splits.ui.theme.avatarColor
import com.kanishk.splits.ui.theme.categoryTint
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import kotlinx.datetime.Month

@Composable
fun GroupInsightsScreen(
    groupId: String,
    onBack: () -> Unit,
) {
    val repository = LocalRepository.current
    val detail by repository.observeGroupDetail(groupId).collectAsStateWithLifecycle(null)
    val currentMonth = remember { todayMonthYear() }
    var selected by remember(groupId) { mutableStateOf(currentMonth) }
    var showPicker by remember { mutableStateOf(false) }

    val current = detail
    if (current == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    val me = current.meIn(repository.deviceId)
    val insights = remember(current, selected, me?.id) {
        monthInsights(current.members, current.expenses, selected, myMemberId = me?.id)
    }
    val mine = remember(insights, me?.id) {
        insights.members.firstOrNull { it.member.id == me?.id }
    }
    val others = remember(insights, me?.id) {
        insights.members.filter { it.member.id != me?.id }
    }
    val earliestMonth = remember(current.expenses) {
        current.expenses.minOfOrNull { monthYearOf(it.occurredAt) }
    }
    val canGoNext = selected < currentMonth
    val canGoPrev = earliestMonth == null || selected > earliestMonth
    val currency = current.group.currencyCode

    // One member's list is open at a time so a busy month does not become a single
    // endless page. You start open — that is the row people came here for.
    var expandedId by remember(groupId, selected) { mutableStateOf(me?.id) }
    var revealAll by remember(groupId, selected) { mutableStateOf(false) }

    fun toggleExpanded(memberId: String) {
        if (expandedId == memberId) {
            expandedId = null
            revealAll = false
        } else {
            expandedId = memberId
            revealAll = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SplitsTopBar("Insights", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthNavigator(
                    selected = selected,
                    canGoPrev = canGoPrev,
                    canGoNext = canGoNext,
                    onPrev = { selected = selected.minusMonths(1) },
                    onNext = { selected = selected.plusMonths(1) },
                    onPick = { showPicker = true },
                )
            }

            item {
                SplitsCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        SectionLabel("Total spent")
                        VSpace(4.dp)
                        MoneyText(
                            amountMinor = insights.totalSpentMinor,
                            currencyCode = currency,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (mine != null) {
                            VSpace(16.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                InsightStatTile(
                                    label = "Your share",
                                    amountMinor = mine.spentMinor,
                                    currencyCode = currency,
                                    modifier = Modifier.weight(1f),
                                )
                                InsightStatTile(
                                    label = "You paid",
                                    amountMinor = mine.paidMinor,
                                    currencyCode = currency,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            VSpace(4.dp)
                            Text(
                                buildString {
                                    append("Across ${current.members.size} ")
                                    append(if (current.members.size == 1) "member" else "members")
                                    append(" in ${formatMonthYear(selected)}.")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (insights.members.isEmpty()) {
                item {
                    EmptyState(
                        glyph = "👥",
                        title = "No members yet",
                        subtitle = "Add people to this group and their monthly spend will show up here.",
                    )
                }
            } else if (insights.totalSpentMinor == 0L && insights.members.all { it.expenses.isEmpty() }) {
                item {
                    EmptyState(
                        glyph = "📊",
                        title = "Nothing in ${formatMonthYear(selected)}",
                        subtitle = "Pick another month to see what everyone spent.",
                    )
                }
            } else {
                if (mine != null) {
                    item {
                        SectionLabel(
                            "You",
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    item(key = mine.member.id) {
                        MemberInsightCard(
                            insight = mine,
                            currencyCode = currency,
                            totalSpentMinor = insights.totalSpentMinor,
                            isYou = true,
                            expanded = expandedId == mine.member.id,
                            revealAll = revealAll && expandedId == mine.member.id,
                            onToggle = { toggleExpanded(mine.member.id) },
                            onRevealAll = { revealAll = true },
                        )
                    }
                }
                if (others.isNotEmpty()) {
                    item {
                        SectionLabel(
                            if (mine != null) "Everyone else" else "Members",
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(others, key = { it.member.id }) { row ->
                        MemberInsightCard(
                            insight = row,
                            currencyCode = currency,
                            totalSpentMinor = insights.totalSpentMinor,
                            isYou = false,
                            expanded = expandedId == row.member.id,
                            revealAll = revealAll && expandedId == row.member.id,
                            onToggle = { toggleExpanded(row.member.id) },
                            onRevealAll = { revealAll = true },
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        MonthYearPickerSheet(
            selected = selected,
            currentMonth = currentMonth,
            earliestMonth = earliestMonth,
            onDismiss = { showPicker = false },
            onPick = {
                selected = it
                showPicker = false
            },
        )
    }
}

@Composable
private fun MonthNavigator(
    selected: MonthYear,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = canGoPrev) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = if (canGoPrev) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onPick),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                formatMonthYear(selected),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = if (canGoNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

private const val PreviewExpenseCount = 4

@Composable
private fun InsightStatTile(
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

private fun expenseSummaryLine(insight: MemberMonthInsight, currencyCode: String): String {
    val count = insight.expenses.size
    val countLabel = when (count) {
        0 -> "nothing this month"
        1 -> "1 expense"
        else -> "$count expenses"
    }
    return "spent ${formatMinor(insight.spentMinor, currencyCode)} · $countLabel"
}

@Composable
private fun MemberInsightCard(
    insight: MemberMonthInsight,
    currencyCode: String,
    totalSpentMinor: Long,
    isYou: Boolean,
    expanded: Boolean,
    revealAll: Boolean,
    onToggle: () -> Unit,
    onRevealAll: () -> Unit,
) {
    val expandable = insight.expenses.isNotEmpty()
    val shown = when {
        !expanded -> emptyList()
        revealAll || insight.expenses.size <= PreviewExpenseCount -> insight.expenses
        else -> insight.expenses.take(PreviewExpenseCount)
    }
    val hiddenCount = insight.expenses.size - shown.size
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "insight-chevron")
    val share = if (totalSpentMinor > 0) {
        (insight.spentMinor.toFloat() / totalSpentMinor.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val nameColor = if (isYou) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val mutedColor = if (isYou) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    SplitsCard(
        Modifier.fillMaxWidth().animateContentSize(),
        color = if (isYou) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    insight.member.name,
                    insight.member.colorIndex,
                    size = 40.dp,
                    ring = isYou,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (isYou) "${insight.member.name} (you)" else insight.member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    VSpace(2.dp)
                    Text(
                        expenseSummaryLine(insight, currencyCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MoneyText(
                    amountMinor = insight.spentMinor,
                    currencyCode = currencyCode,
                    color = nameColor,
                    fontSize = 17.sp,
                )
                if (expandable) {
                    HSpace(2.dp)
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Hide expenses" else "Show expenses",
                        tint = mutedColor,
                        modifier = Modifier.size(22.dp).rotate(chevron),
                    )
                }
            }

            if (totalSpentMinor > 0) {
                VSpace(10.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(mutedColor.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(share)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(avatarColor(insight.member.colorIndex)),
                    )
                }
            }

            if (expanded && shown.isNotEmpty()) {
                VSpace(10.dp)
                shown.forEach { expense ->
                    InsightExpenseRow(
                        expense = expense,
                        memberId = insight.member.id,
                        currencyCode = currencyCode,
                        mutedColor = mutedColor,
                        nameColor = nameColor,
                    )
                }
                if (hiddenCount > 0) {
                    TextButton(onClick = onRevealAll) {
                        Text("Show all ${insight.expenses.size} expenses")
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightExpenseRow(
    expense: Expense,
    memberId: String,
    currencyCode: String,
    mutedColor: androidx.compose.ui.graphics.Color,
    nameColor: androidx.compose.ui.graphics.Color,
) {
    val category = resolveCategory(expense.categoryId)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphTile(
            glyph = category?.glyph ?: "🧾",
            tint = categoryTint(category?.toneIndex ?: 20),
            size = 28.dp,
        )
        Text(
            expense.title.ifBlank { "Expense" },
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatMinor(expense.shareOf(memberId), currencyCode),
            style = MaterialTheme.typography.bodySmall,
            color = nameColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthYearPickerSheet(
    selected: MonthYear,
    currentMonth: MonthYear,
    earliestMonth: MonthYear?,
    onDismiss: () -> Unit,
    onPick: (MonthYear) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val minYear = minOf(earliestMonth?.year ?: currentMonth.year, selected.year, currentMonth.year)
    val years = (minYear..currentMonth.year).toList()
    var year by remember(selected) { mutableStateOf(selected.year) }

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
        ) {
            Text(
                "Choose a month",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VSpace(16.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(years, key = { it }) { option ->
                    val active = option == year
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { year = option },
                        color = if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            option.toString(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            VSpace(16.dp)
            Month.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { month ->
                        val candidate = MonthYear(year, month)
                        val enabled = candidate <= currentMonth &&
                            (earliestMonth == null || candidate >= earliestMonth)
                        val active = candidate == selected
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (enabled) Modifier.clickable { onPick(candidate) } else Modifier
                                ),
                            color = when {
                                active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                abbreviatedMonth(month),
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    active -> MaterialTheme.colorScheme.primary
                                    enabled -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
