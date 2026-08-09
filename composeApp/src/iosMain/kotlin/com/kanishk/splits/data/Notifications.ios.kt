package com.kanishk.splits.data

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual fun showNotification(id: Int, title: String, body: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
    }

    // A nil trigger delivers immediately. Reusing the id for the same expense means an edit
    // replaces its earlier notification instead of stacking a second one.
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = id.toString(),
        content = content,
        trigger = null,
    )

    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(request) { /* delivery errors are not actionable here */ }
}

actual fun requestNotificationPermission() {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { _, _ -> }
}
