package com.kanishk.splits.model

/**
 * One member's picture of a single calendar month: what they consumed, what they fronted,
 * and the real expenses they were part of. Settlements are left out of all three — they
 * move money around, they are not new spending.
 */
data class MemberMonthInsight(
    val member: Member,
    val spentMinor: Long,
    val paidMinor: Long,
    val expenses: List<Expense>,
)

data class MonthInsights(
    val monthYear: MonthYear,
    val totalSpentMinor: Long,
    val members: List<MemberMonthInsight>,
)

fun monthInsights(
    members: List<Member>,
    expenses: List<Expense>,
    monthYear: MonthYear,
): MonthInsights {
    val inMonth = expenses.inMonth(monthYear)
    val summary = summarise(members, inMonth)
    return MonthInsights(
        monthYear = monthYear,
        totalSpentMinor = summary.totalSpentMinor,
        members = members.map { member ->
            MemberMonthInsight(
                member = member,
                spentMinor = summary.spentBy(member.id),
                paidMinor = summary.paidBy(member.id),
                expenses = inMonth.filter { !it.isReimbursement && it.involves(member.id) },
            )
        }.sortedWith(
            compareByDescending<MemberMonthInsight> { it.spentMinor }
                .thenBy { it.member.name.lowercase() }
        ),
    )
}
