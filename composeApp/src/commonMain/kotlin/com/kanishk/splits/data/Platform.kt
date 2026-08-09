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
 * [count] bytes from the platform's cryptographic RNG.
 *
 * Not a convenience over `kotlin.random.Random`: group ids, device ids and invite codes are
 * the *only* thing standing between a stranger and a group's data (see the server security
 * model in TECHNICAL.md), so they have to be unguessable rather than merely well distributed.
 * A general-purpose PRNG carries far less entropy than the id it prints and its future output
 * can be derived from output already seen — neither is acceptable for a bearer secret.
 */
expect fun secureRandomBytes(count: Int): ByteArray
