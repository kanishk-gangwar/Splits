package com.kanishk.splits.data

import com.kanishk.splits.model.formatMinor

/** A notification ready to hand to the platform. */
data class NotificationContent(
    val id: Int,
    val title: String,
    val body: String,
)

/**
 * The summary notification always reuses one id, so repeated refreshes replace it rather than
 * stacking up.
 */
const val SUMMARY_NOTIFICATION_ID = 1

/**
 * Turns what changed into the text of a single notification.
 *
 * One change gets the detail. Several collapse into a summary, because a burst of individual
 * notifications for one refresh is unusable.
 *
 * Deliberately a pure function. A mis-escaped template here once shipped the raw placeholder
 * text to real phones instead of the numbers, and the only way to catch that is to assert on
 * the finished string — which a composable or a platform call cannot do.
 */
fun notificationFor(notices: List<ExpenseNotice>): NotificationContent? {
    if (notices.isEmpty()) return null

    if (notices.size == 1) {
        val notice = notices.first()
        val verb = when (notice.kind) {
            NoticeKind.Added -> "added"
            NoticeKind.Updated -> "updated"
            NoticeKind.Removed -> "removed"
        }
        val amount = formatMinor(notice.amountMinor, notice.currencyCode)
        val label = notice.title.ifBlank { "an expense" }

        return NotificationContent(
            id = notice.expenseId.hashCode(),
            title = notice.groupEmoji + "  " + notice.groupName,
            body = notice.actorName + " " + verb + " \"" + label + "\" · " + amount,
        )
    }

    val groups = notices.map { it.groupName }.distinct()
    val where = if (groups.size == 1) groups.first() else groups.size.toString() + " groups"

    return NotificationContent(
        id = SUMMARY_NOTIFICATION_ID,
        title = "Splits",
        body = notices.size.toString() + " updates in " + where,
    )
}
