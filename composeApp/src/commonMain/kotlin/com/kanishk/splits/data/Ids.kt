package com.kanishk.splits.data

private const val HEX = "0123456789abcdef"

/**
 * 128 bits of randomness rendered as hex. Ids are minted on-device and later pushed to the
 * server as-is, so they have to be collision-safe without ever asking a server for one.
 *
 * They also have to be *unguessable*, which is a stronger requirement and the reason this uses
 * [secureRandomBytes] rather than `kotlin.random.Random`: with no accounts, knowing a group id
 * is what authorises reading and writing that group, and a device id is what proves admin
 * rights to `splits_delete_group`. A general-purpose PRNG holds less internal state than the
 * 128 bits it prints and its future output follows from output already observed — fine for
 * shuffling, not for a bearer secret.
 *
 * The server enforces the shape as well (`splits_device` in schema.sql accepts only 32 lower
 * case hex characters), so this format is load-bearing — changing it means changing that too.
 */
fun newId(): String {
    val bytes = secureRandomBytes(16)
    return buildString(32) {
        bytes.forEach { byte ->
            val v = byte.toInt() and 0xFF
            append(HEX[v ushr 4])
            append(HEX[v and 0x0F])
        }
    }
}

/** Ambiguous glyphs (0/O, 1/I/L) are left out so codes survive being read aloud. */
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/**
 * Length is a security parameter, not a cosmetic one. Resolving an invite is unauthenticated
 * by design — the code *is* the credential — so the only real defence against someone scanning
 * for any valid group is that there are far too many codes to scan. Eight characters was about
 * 2^40, which is uncomfortably reachable when an attacker does not care *which* group they
 * find; twelve is about 2^59, which is not.
 *
 * Shorter codes minted by older versions keep working: lookup is by exact match and
 * [normaliseInviteCode] is length-agnostic. The throttle in `splits_resolve_invite` exists to
 * cover those.
 */
private const val INVITE_CODE_LENGTH = 12

/**
 * Rejection sampling rather than `byte % 31`. The alphabet does not divide 256 evenly, so a
 * plain modulo would make the first eight letters measurably more likely than the rest and
 * quietly shave entropy off every code.
 */
fun newInviteCode(): String {
    val n = INVITE_ALPHABET.length
    val limit = 256 - (256 % n) // 248: the largest multiple of 31 a byte can hold
    return buildString(INVITE_CODE_LENGTH) {
        while (length < INVITE_CODE_LENGTH) {
            secureRandomBytes(INVITE_CODE_LENGTH).forEach { byte ->
                if (length == INVITE_CODE_LENGTH) return@forEach
                val v = byte.toInt() and 0xFF
                if (v < limit) append(INVITE_ALPHABET[v % n])
            }
        }
    }
}

fun normaliseInviteCode(raw: String): String =
    raw.trim().uppercase().filter { it in INVITE_ALPHABET }
