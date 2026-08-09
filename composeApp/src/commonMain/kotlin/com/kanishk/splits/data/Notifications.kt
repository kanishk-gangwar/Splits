package com.kanishk.splits.data

/**
 * Local notifications, raised when a sync pull discovers that somebody else added or changed
 * an expense.
 *
 * These are *local* notifications, not push. They fire while the app is running or when it
 * syncs on launch or on pull-to-refresh. Delivering one to a phone with the app closed would
 * need FCM and APNs plus a server holding device tokens, which is well outside a free
 * backend — see the note in SETUP.md.
 */
expect fun showNotification(id: Int, title: String, body: String)

/**
 * Asks the OS for permission if it has not been granted.
 *
 * On Android the runtime prompt needs an Activity, so it is triggered from `MainActivity` and
 * this is a no-op. On iOS there is no such constraint and this does the asking.
 */
expect fun requestNotificationPermission()

/** What happened to an expense, as far as this device is concerned. */
enum class NoticeKind { Added, Updated, Removed }

/** Everything needed to write one notification line, gathered while the change is applied. */
data class ExpenseNotice(
    val expenseId: String,
    val groupId: String,
    val groupName: String,
    val groupEmoji: String,
    val title: String,
    val amountMinor: Long,
    val currencyCode: String,
    val actorName: String,
    val kind: NoticeKind,
)
