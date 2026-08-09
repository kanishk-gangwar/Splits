package com.kanishk.splits.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kanishk.splits.R

private const val CHANNEL_ID = "splits_activity"

private fun ensureChannel(manager: NotificationManager) {
    // minSdk is 26, so channels always exist. Creating an existing channel is a no-op.
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Group activity",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "New and updated expenses in your groups"
    }
    manager.createNotificationChannel(channel)
}

private fun canPost(): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

actual fun showNotification(id: Int, title: String, body: String) {
    // Posting without permission throws on some OEM builds, so check rather than catch.
    if (!canPost()) return

    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureChannel(manager)

    val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
    val contentIntent = launch?.let {
        PendingIntent.getActivity(
            appContext,
            0,
            it,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    val notification = Notification.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .apply { if (contentIntent != null) setContentIntent(contentIntent) }
        .build()

    manager.notify(id, notification)
}

/** Android's prompt needs an Activity; MainActivity asks on launch instead. */
actual fun requestNotificationPermission() = Unit
