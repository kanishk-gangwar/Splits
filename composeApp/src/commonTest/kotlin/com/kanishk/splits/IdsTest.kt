package com.kanishk.splits

import com.kanishk.splits.data.newId
import com.kanishk.splits.data.newInviteCode
import com.kanishk.splits.data.normaliseInviteCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ids are the app's only credentials — knowing a group id authorises reading and writing that
 * group, and a device id is what proves admin rights. These are cheap assertions about shape
 * and spread; they cannot prove the source is cryptographic, but they do catch the format
 * drifting away from what the server enforces and the rejection sampling going wrong.
 */
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

class IdsTest {

    /**
     * `splits_device` in schema.sql accepts only 32 lower-case hex characters and grants no
     * rights to anything else, so this format is load-bearing on both sides.
     */
    @Test
    fun idIsThirtyTwoLowercaseHexCharacters() {
        val pattern = Regex("^[0-9a-f]{32}$")
        repeat(200) {
            val id = newId()
            assertTrue(pattern.matches(id), "id '$id' is not 32 lower-case hex characters")
        }
    }

    @Test
    fun idsDoNotRepeat() {
        val ids = List(2000) { newId() }
        assertEquals(ids.size, ids.toSet().size, "newId() produced a duplicate")
    }

    @Test
    fun inviteCodeIsTwelveCharactersFromTheUnambiguousAlphabet() {
        repeat(200) {
            val code = newInviteCode()
            assertEquals(12, code.length, "invite code '$code' is the wrong length")
            code.forEach { ch ->
                assertTrue(ch in INVITE_ALPHABET, "invite code '$code' contains '$ch'")
            }
        }
    }

    @Test
    fun inviteCodesDoNotRepeat() {
        val codes = List(2000) { newInviteCode() }
        assertEquals(codes.size, codes.toSet().size, "newInviteCode() produced a duplicate")
    }

    /**
     * The generator rejects bytes at or above 248 rather than taking `byte % 31`, because the
     * alphabet does not divide 256 evenly and a plain modulo would make the first eight letters
     * likelier than the rest. If rejection sampling were broken by an off-by-one — dropping the
     * tail of the alphabet, say — this is what would notice.
     */
    @Test
    fun everySymbolIsReachable() {
        val seen = List(500) { newInviteCode() }.flatMap { it.toList() }.toSet()
        val missing = INVITE_ALPHABET.filterNot { it in seen }
        assertTrue(missing.isEmpty(), "these symbols never appeared: $missing")
    }

    /** A code that has been through a chat app and a copy-paste still has to survive. */
    @Test
    fun normalisingRoundTripsAGeneratedCode() {
        val code = newInviteCode()
        assertEquals(code, normaliseInviteCode("  ${code.lowercase()}  "))
        assertEquals(code, normaliseInviteCode(code))
    }
}
