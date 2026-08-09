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
