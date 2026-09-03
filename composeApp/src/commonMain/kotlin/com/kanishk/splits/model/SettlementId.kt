package com.kanishk.splits.model

/**
 * A settlement suggested by [suggestSettlements] gets an id both devices can work out for
 * themselves, instead of a fresh random one each.
 *
 * The problem it solves is the obvious one, and it happens in real groups: two people look at
 * the same "A pays B 500" line and both tap Record. Two ids, two rows, and the group is now
 * 500 out — the payment has been counted twice and B looks overpaid. Nothing downstream can
 * tell the two rows apart, because on the wire they are simply two different expenses.
 *
 * So the id is derived from what the two devices already agree on: the group, who pays whom,
 * how much, and which day it is. Both pushes then land on the *same* primary key, the server
 * upserts, and one row survives — no dedupe pass, no new column, no server change. Whoever
 * saved last wins the title and the note, which is last-write-wins exactly as everywhere else.
 *
 * Two boundaries are worth stating plainly:
 *
 * - **The day is UTC**, so two devices recording the same suggestion either side of midnight
 *   UTC mint different ids and the duplicate comes back. The day has to be in the key at all
 *   because the same debt can recur: A owing B 500 again next week is a *new* payment, and
 *   without a day component it would overwrite the old one and quietly erase it.
 * - **A genuine second identical payment on the same day** would collide with the first. The
 *   repository handles that where it can actually be checked: if a row with this id is already
 *   in the database, the save falls back to a random id, because the user is looking at the
 *   first payment and recording another one on purpose. Only the case where the first row is
 *   *not* there — the concurrent one — merges, which is precisely the case we want merged.
 *
 * The hash covers the group id as well, so these ids stay as unguessable as the group id
 * itself. An expense id is not a credential, but it travels in the same payloads and there is
 * no reason to hand a predictable one to somebody who does not already hold the group.
 */
fun settlementId(
    groupId: String,
    fromMemberId: String,
    toMemberId: String,
    amountMinor: Long,
    dayIndex: Long,
): String {
    val material = "settle $groupId $fromMemberId $toMemberId $amountMinor $dayIndex"
    // 32 hex characters: the shape newId() produces and the one the server's id columns and
    // the app's own checks expect. Truncating SHA-256 to 128 bits keeps collisions theoretical.
    return sha256Hex(material).take(32)
}

/** Whole days since the epoch, UTC. `floorDiv` so a pre-1970 clock does not wrap the wrong way. */
fun dayIndexOf(millis: Long): Long = millis.floorDiv(86_400_000L)

// ------------------------------------------------------------------- sha-256 --

// Implemented here because the model package deliberately carries no dependencies and the
// Kotlin standard library has no hash. It is the standard algorithm, pinned to the published
// test vectors in SettlementIdTest — do not "simplify" it without running those.

private val ROUND_CONSTANTS = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
    0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
    0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
    0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
    0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
    0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
    0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
)

private const val HEX_DIGITS = "0123456789abcdef"

internal fun sha256Hex(input: String): String {
    val digest = sha256(input.encodeToByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val v = byte.toInt() and 0xFF
            append(HEX_DIGITS[v ushr 4])
            append(HEX_DIGITS[v and 0x0F])
        }
    }
}

private fun rotateRight(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

private fun sha256(message: ByteArray): ByteArray {
    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    // Pad to a multiple of 64 bytes: one 0x80 byte, zeroes, then the length in bits big-endian.
    val bitLength = message.size.toLong() * 8
    val paddedSize = ((message.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedSize - 1 - i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
    }

    val w = IntArray(64)
    var offset = 0
    while (offset < paddedSize) {
        for (i in 0 until 16) {
            val p = offset + i * 4
            w[i] = ((padded[p].toInt() and 0xFF) shl 24) or
                ((padded[p + 1].toInt() and 0xFF) shl 16) or
                ((padded[p + 2].toInt() and 0xFF) shl 8) or
                (padded[p + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = rotateRight(w[i - 15], 7) xor rotateRight(w[i - 15], 18) xor (w[i - 15] ushr 3)
            val s1 = rotateRight(w[i - 2], 17) xor rotateRight(w[i - 2], 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + ROUND_CONSTANTS[i] + w[i]
            val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
        offset += 64
    }

    val out = ByteArray(32)
    intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { index, value ->
        out[index * 4] = (value ushr 24).toByte()
        out[index * 4 + 1] = (value ushr 16).toByte()
        out[index * 4 + 2] = (value ushr 8).toByte()
        out[index * 4 + 3] = value.toByte()
    }
    return out
}
