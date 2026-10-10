package com.kanishk.splits.data

import com.kanishk.splits.model.sha256Hex

/**
 * Turns the platform's stable install id (see [stableInstallId]) into a device id.
 *
 * The result has the same 32-lower-case-hex shape as [newId], because the server's
 * `splits_device` accepts nothing else and because the two kinds of id must be
 * indistinguishable on the wire — a device id is an authorisation secret, and nothing should
 * be able to tell "derived" from "random" by looking at it.
 *
 * Hashing rather than using the platform value directly keeps that raw value off the wire
 * entirely. The domain prefix means the same seed would hash to something unrelated if it were
 * ever used for a second purpose.
 */
fun deriveDeviceId(platformSeed: String?): String? {
    val seed = platformSeed?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return sha256Hex("splits-device-v1:$seed").take(32)
}
