package com.kanishk.splits.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kanishk.splits.db.SplitsDatabase
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

actual fun createSqlDriver(): SqlDriver =
    NativeSqliteDriver(
        schema = SplitsDatabase.Schema,
        name = "splits.db",
    )

actual fun shareText(text: String) {
    val controller = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    val presenter = topViewController() ?: return

    // On iPad an activity sheet without an anchor crashes, so pin it to the presenter.
    controller.popoverPresentationController?.sourceView = presenter.view

    presenter.presentViewController(controller, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun deviceLabel(): String = UIDevice.currentDevice.name
