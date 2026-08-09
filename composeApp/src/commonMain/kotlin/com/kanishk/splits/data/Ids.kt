package com.kanishk.splits.data

import kotlin.random.Random

private const val HEX = "0123456789abcdef"

/**
 * 128 bits of randomness rendered as hex. Ids are minted on-device and later pushed to the
 * server as-is, so they have to be collision-safe without ever asking a server for one.
 */
fun newId(): String = buildString(32) {
    repeat(32) { append(HEX[Random.nextInt(16)]) }
}

/** Ambiguous glyphs (0/O, 1/I/L) are left out so codes survive being read aloud. */
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

fun newInviteCode(): String = buildString(8) {
    repeat(8) { append(INVITE_ALPHABET[Random.nextInt(INVITE_ALPHABET.length)]) }
}

fun normaliseInviteCode(raw: String): String =
    raw.trim().uppercase().filter { it in INVITE_ALPHABET }
