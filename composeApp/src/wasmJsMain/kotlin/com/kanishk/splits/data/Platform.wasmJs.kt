package com.kanishk.splits.data

import app.cash.sqldelight.db.SqlDriver
import com.kanishk.splits.db.SplitsDatabase

/**
 * Opens the database that main() already loaded from IndexedDB, creating or migrating the
 * schema by hand — the browser has no driver-level helper that does it the way the Android and
 * native drivers do, so the version lives in SQLite's own `user_version` just like theirs.
 */
actual fun createSqlDriver(): SqlDriver {
    val driver = WebSqlDriver()
    val schema = SplitsDatabase.Schema
    val current = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value
    if (current == 0L) {
        schema.create(driver)
        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
    } else if (current < schema.version) {
        schema.migrate(driver, current, schema.version)
        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
    }
    return driver
}

actual fun shareText(text: String) = jsShare(text)

actual fun copyToClipboard(text: String) = jsCopy(text)

actual fun deviceLabel(): String = jsDeviceLabel()

/** A browser has nothing that survives clearing site data, so identity is a random id. */
actual fun stableInstallId(): String? = null

actual fun openUrl(url: String) = jsOpenUrl(url)

actual fun appVersionName(): String = GeneratedWebConfig.VERSION_NAME

/** `crypto.getRandomValues` is the browser's CSPRNG; see the expect declaration for why. */
actual fun secureRandomBytes(count: Int): ByteArray {
    require(count > 0) { "count must be positive" }
    val source = jsRandomBytes(count)
    return ByteArray(count) { jsByteAt(source, it).toByte() }
}

private fun jsShare(text: String): Unit = js(
    """{
    if (navigator.share) {
        navigator.share({ text: text }).catch(function () {});
    } else if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(function () { alert('Copied to clipboard'); });
    }
}"""
)

private fun jsCopy(text: String): Unit =
    js("{ if (navigator.clipboard) navigator.clipboard.writeText(text).catch(function () {}); }")

private fun jsDeviceLabel(): String = js(
    """(function () {
    var ua = navigator.userAgent;
    if (/iPhone/.test(ua)) return 'iPhone (web)';
    if (/iPad/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'iPad (web)';
    if (/Android/.test(ua)) return 'Android (web)';
    if (/Macintosh/.test(ua)) return 'Mac (web)';
    if (/Windows/.test(ua)) return 'Windows (web)';
    return 'Browser';
})()"""
)

private fun jsOpenUrl(url: String): Unit = js("{ window.open(url, '_blank'); }")

private fun jsRandomBytes(count: Int): JsAny = js("crypto.getRandomValues(new Uint8Array(count))")

private fun jsByteAt(array: JsAny, index: Int): Int = js("array[index]")
