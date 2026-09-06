package com.kanishk.splits.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalRepository
import com.kanishk.splits.model.MemberMonthInsight
import com.kanishk.splits.model.MonthYear
import com.kanishk.splits.model.abbreviatedMonth
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.formatMonthYear
import com.kanishk.splits.model.monthInsights
import com.kanishk.splits.model.monthYearOf
import com.kanishk.splits.model.todayMonthYear
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.EmptyState
import com.kanishk.splits.ui.components.MoneyText
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

    val insights = remember(current, selected) {
        monthInsights(current.members, current.expenses, selected)
    }
    val earliestMonth = remember(current.expenses) {
        current.expenses.minOfOrNull { monthYearOf(it.occurredAt) }
    }
    val canGoNext = selected < currentMonth
    val canGoPrev = earliestMonth == null || selected > earliestMonth
    val currency = current.group.currencyCode
    val me = current.meIn(repository.deviceId)

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
                items(insights.members, key = { it.member.id }) { row ->
                    MemberInsightCard(
                        insight = row,
                        currencyCode = currency,
                        isYou = row.member.id == me?.id,
                    )
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

@Composable
private fun MemberInsightCard(
    insight: MemberMonthInsight,
    currencyCode: String,
    isYou: Boolean,
) {
    SplitsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(insight.member.name, insight.member.colorIndex, size = 40.dp, ring = isYou)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (isYou) "${insight.member.name} (you)" else insight.member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    VSpace(2.dp)
                    Text(
                        "spent ${formatMinor(insight.spentMinor, currencyCode)}" +
                            " · paid ${formatMinor(insight.paidMinor, currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MoneyText(
                    amountMinor = insight.spentMinor,
                    currencyCode = currencyCode,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                )
            }

            if (insight.expenses.isNotEmpty()) {
                VSpace(12.dp)
                insight.expenses.forEach { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            expense.title.ifBlank { "Expense" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatMinor(expense.shareOf(insight.member.id), currencyCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
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
