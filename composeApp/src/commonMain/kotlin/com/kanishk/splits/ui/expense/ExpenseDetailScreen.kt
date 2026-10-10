package com.kanishk.splits.ui.expense

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.ReimbursementCategory
import com.kanishk.splits.model.formatDate
import com.kanishk.splits.model.resolveCategory
import com.kanishk.splits.ui.components.Avatar
import com.kanishk.splits.ui.components.GlyphTile
import com.kanishk.splits.ui.components.MoneyText
import com.kanishk.splits.ui.components.SectionLabel
import com.kanishk.splits.ui.components.SplitsCard
import com.kanishk.splits.ui.components.SplitsTopBar
import com.kanishk.splits.ui.components.VSpace
import com.kanishk.splits.ui.theme.categoryTint

/**
 * An entry seen by somebody who may not change it — anyone other than whoever added it or the
 * admin, or anyone at all once the group is archived.
 *
 * It used to be one of two things: the full editor, Save and Delete included, or a row that did
 * nothing when tapped. The first let people change entries that were not theirs; the second hid
 * what an entry was even from the people it was split between. This is the third option: every
 * fact on the entry, and no control that would suggest it can be changed.
 */
@Composable
fun ExpenseDetailScreen(
    detail: GroupDetail,
    expense: Expense,
    myMemberId: String?,
    onBack: () -> Unit,
) {
    val currency = detail.group.currencyCode
    val category = if (expense.isReimbursement) ReimbursementCategory else resolveCategory(expense.categoryId)
    val title = expense.title.ifBlank { if (expense.isReimbursement) "Settlement" else "Expense" }
    val payer = detail.member(expense.paidByMemberId)
    val creator = detail.member(expense.createdByMemberId ?: expense.paidByMemberId)

    fun nameOf(member: Member?): String = when {
        member == null -> "Someone"
        member.id == myMemberId -> "${member.name} (you)"
        else -> member.name
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SplitsTopBar(
                title = if (expense.isReimbursement) "Settlement" else "Expense",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlyphTile(
                        glyph = category?.glyph ?: "🧾",
                        tint = categoryTint(category?.toneIndex ?: 20),
                        size = 56.dp,
                    )
                    VSpace(12.dp)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    VSpace(4.dp)
                    MoneyText(
                        amountMinor = expense.amountMinor,
                        currencyCode = currency,
                        fontSize = 34.sp,
                    )
                    VSpace(4.dp)
                    Text(
                        listOfNotNull(
                            formatDate(expense.occurredAt),
                            category?.label?.takeUnless { expense.isReimbursement },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expense.isReimbursement) {
                item {
                    val to = detail.member(expense.paidToMemberId)
                    SplitsCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonSlot("From", payer, nameOf(payer), Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "paid",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            PersonSlot("To", to, nameOf(to), Modifier.weight(1f))
                        }
                    }
                }
            } else {
                item {
                    SplitsCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Paid by",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                nameOf(payer),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                item { SectionLabel("Split between ${expense.splits.size}") }

                items(expense.splits, key = { it.memberId }) { split ->
                    val member = detail.member(split.memberId)
                    SplitsCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                member?.name ?: "?",
                                member?.colorIndex ?: 0,
                                size = 32.dp,
                                ring = split.memberId == myMemberId,
                            )
                            Text(
                                nameOf(member),
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MoneyText(
                                amountMinor = split.shareMinor,
                                currencyCode = currency,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            val note = expense.note?.trim().orEmpty()
            if (note.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("Note")
                        VSpace(8.dp)
                        SplitsCard(Modifier.fillMaxWidth()) {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(14.dp),
                    )
                    Text(
                        text = if (detail.group.archived) {
                            "This group is archived, so its entries can't be changed."
                        } else {
                            "Added by ${creator?.name ?: "someone"}. Only they or the group " +
                                "admin can change or delete it."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonSlot(caption: String, member: Member?, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(8.dp)
        Avatar(member?.name ?: "?", member?.colorIndex ?: 0, size = 44.dp)
        VSpace(6.dp)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
