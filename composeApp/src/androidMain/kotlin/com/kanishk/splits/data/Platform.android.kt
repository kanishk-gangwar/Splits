package com.kanishk.splits.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * One instance, reused. `SecureRandom()` seeds itself from the OS on construction, so minting
 * a fresh one per id is pure cost — and on Android the no-arg constructor is already the
 * correctly seeded platform CSPRNG.
 */
private val secureRandom = SecureRandom()

actual fun secureRandomBytes(count: Int): ByteArray =
    ByteArray(count).also { secureRandom.nextBytes(it) }
