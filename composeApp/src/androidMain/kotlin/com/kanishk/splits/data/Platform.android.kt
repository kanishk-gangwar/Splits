package com.kanishk.splits.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.kanishk.splits.db.SplitsDatabase
import java.security.SecureRandom

/**
 * Set once from [com.kanishk.splits.SplitsApplication] before any Compose code runs, so the
 * shared layer can reach a Context without every call site having to thread one through.
 */
@SuppressLint("StaticFieldLeak")
internal lateinit var appContext: Context

actual fun createSqlDriver(): SqlDriver =
    AndroidSqliteDriver(
        schema = SplitsDatabase.Schema,
        context = appContext,
        name = "splits.db",
    )

actual fun shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(chooser)
}

actual fun copyToClipboard(text: String) {
    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Splits invite", text))
}

actual fun deviceLabel(): String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

/**
 * `ANDROID_ID` is, from Android 8 (our minSdk), a 64-bit value scoped to the app's signing
 * key, the user and the device: no other app can read it, and it survives uninstalling and
 * reinstalling this one. It only changes on a factory reset or a different signing key — a
 * debug build and a release build are two different installs, which is what you would want.
 *
 * That makes it exactly the thing a "device id" was always meant to be. The old approach —
 * minting a random id and keeping it in our own database — lost the id, and with it every
 * claimed name, the moment the app was uninstalled.
 */
@SuppressLint("HardwareIds")
actual fun stableInstallId(): String? =
    runCatching { Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != BROKEN_EMULATOR_ANDROID_ID }

/** The one well-known duplicate value, from an old emulator bug. Treated as "no id". */
private const val BROKEN_EMULATOR_ANDROID_ID = "9774d56d682e549c"

actual fun openUrl(url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // A device with no browser at all is a stranger thing than a crash on the settings screen,
    // but it is the caller's screen that would die, so the miss is swallowed.
    runCatching { appContext.startActivity(view) }
}

@Suppress("DEPRECATION")
actual fun appVersionName(): String =
    runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: ""

/**
 * One instance, reused. `SecureRandom()` seeds itself from the OS on construction, so minting
 * a fresh one per id is pure cost — and on Android the no-arg constructor is already the
 * correctly seeded platform CSPRNG.
 */
private val secureRandom = SecureRandom()

actual fun secureRandomBytes(count: Int): ByteArray =
    ByteArray(count).also { secureRandom.nextBytes(it) }
