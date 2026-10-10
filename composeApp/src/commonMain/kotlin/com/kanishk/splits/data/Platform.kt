package com.kanishk.splits.data

import app.cash.sqldelight.db.SqlDriver

/** Opens (and migrates) the on-device database. */
expect fun createSqlDriver(): SqlDriver

/** Hands [text] to the OS share sheet. */
expect fun shareText(text: String)

/** Puts [text] on the system clipboard. */
expect fun copyToClipboard(text: String)

/** Something recognisable in the settings screen, e.g. "Pixel 8" or "iPhone". */
expect fun deviceLabel(): String

/**
 * An identifier for this app on this device that outlives the app's own data — in particular
 * one that is the same again after an uninstall and reinstall. Null when the platform has
 * nothing of the kind, in which case identity falls back to a random id that lives and dies
 * with the install.
 *
 * It is a *seed*, never used as-is: [deriveDeviceId] hashes it into the 32-hex shape the
 * server expects, so the raw value is never sent anywhere.
 */
expect fun stableInstallId(): String?

/**
 * Hands [url] to the browser. The one caller that matters is the update button, which points
 * at the APK on GitHub Releases: the download, the "install unknown apps" prompt and the
 * installer are all the platform's job, and an app that tried to own that flow would need
 * permissions it has no other use for.
 */
expect fun openUrl(url: String)

/** The installed version, e.g. "1.5.0". Shown in settings and compared against the latest release. */
expect fun appVersionName(): String

/**
 * [count] bytes from the platform's cryptographic RNG.
 *
 * Not a convenience over `kotlin.random.Random`: group ids, device ids and invite codes are
 * the *only* thing standing between a stranger and a group's data (see the server security
 * model in TECHNICAL.md), so they have to be unguessable rather than merely well distributed.
 * A general-purpose PRNG carries far less entropy than the id it prints and its future output
 * can be derived from output already seen — neither is acceptable for a bearer secret.
 */
expect fun secureRandomBytes(count: Int): ByteArray
