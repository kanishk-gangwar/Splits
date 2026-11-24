package com.kanishk.splits.data

/**
 * Shown through the service worker, because that is the only way iOS shows web notifications
 * at all — and only for a site added to the Home Screen (iOS 16.4+), never in a Safari tab.
 */
actual fun showNotification(id: Int, title: String, body: String) = jsNotify(id.toString(), title, body)

/**
 * Safari only grants this from a user tap, so the launch-time call made by App() is usually
 * ignored; the settings toggle is what actually gets permission on an iPhone.
 */
actual fun requestNotificationPermission() = jsRequestPermission()

private fun jsNotify(tag: String, title: String, body: String): Unit = js(
    """{
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    if (!navigator.serviceWorker) return;
    navigator.serviceWorker.ready
        .then(function (r) { return r.showNotification(title, { body: body, tag: tag }); })
        .catch(function () {});
}"""
)

private fun jsRequestPermission(): Unit = js(
    """{
    try {
        if ('Notification' in window && Notification.permission === 'default') {
            var p = Notification.requestPermission();
            if (p && p.catch) p.catch(function () {});
        }
    } catch (e) {}
}"""
)
