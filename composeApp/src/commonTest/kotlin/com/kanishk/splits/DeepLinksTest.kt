package com.kanishk.splits

import com.kanishk.splits.data.newInviteCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A link that has been through a chat app, an email client and a copy-paste is not guaranteed
 * to arrive intact, so `parseInvite` accepts every shape — and every shape has to keep working
 * when the code itself changes.
 */
class DeepLinksTest {

    /**
     * The regression this file exists for. Invite codes were lengthened from 8 to 12 characters
     * without touching `parseInvite`, which tested `length == 8`. Every newly minted invite link
     * then parsed as "not an invite" and tapping one did nothing — silently, because a
     * malformed link and an unrecognised one are the same null.
     */
    @Test
    fun parsesLinksCarryingAFreshlyMintedCode() {
        repeat(50) {
            val code = newInviteCode()
            assertEquals(code, parseInvite(inviteLink(code)), "web link with $code")
            assertEquals(code, parseInvite(inviteDeepLink(code)), "deep link with $code")
            assertEquals(code, parseInvite("$INVITE_WEB_BASE?c=$code"), "query link with $code")
            assertEquals(code, parseInvite(code), "bare $code")
        }
    }

    /** Links minted by older builds are still sitting in people's chat histories. */
    @Test
    fun stillParsesLegacyEightCharacterCodes() {
        assertEquals("AB23CD45", parseInvite("splits://join/AB23CD45"))
        assertEquals("AB23CD45", parseInvite("$INVITE_WEB_BASE#AB23CD45"))
        assertEquals("AB23CD45", parseInvite("ab23cd45"))
    }

    @Test
    fun ignoresThingsThatAreNotInvites() {
        assertNull(parseInvite(null))
        assertNull(parseInvite(""))
        assertNull(parseInvite("   "))
        assertNull(parseInvite("https://example.com/"))
        // Too short to be either length, even after the alphabet filter.
        assertNull(parseInvite("AB23"))
    }

    /**
     * People forward the whole message, not the bare link. Only the token carrying the join
     * link is taken apart, so the prose around it cannot contribute — "expenses" filters down
     * to eight characters and would otherwise pass for a legacy code.
     */
    @Test
    fun findsTheCodeInsideAWholeForwardedMessage() {
        repeat(20) {
            val code = newInviteCode()
            assertEquals(code, parseInvite(inviteShareMessage("Goa trip", code)))
        }
    }

    /**
     * The join dialog was the second place to hard-code `length == 8` (see the class comment
     * for the first): after codes moved to 12 characters, nothing typed into it could enable
     * Continue. Its input goes through `parseInviteInput`, which must take a code however it
     * was copied — spaced in groups of four the way the share sheet and web page display it,
     * lowercased by a keyboard, or as the whole link when someone pastes that instead.
     */
    @Test
    fun dialogInputAcceptsACodeHoweverItWasCopied() {
        repeat(20) {
            val code = newInviteCode()
            assertEquals(code, parseInviteInput(code), "bare $code")
            assertEquals(code, parseInviteInput(code.lowercase()), "lowercase $code")
            assertEquals(
                code,
                parseInviteInput(code.chunked(4).joinToString(" ")),
                "grouped $code",
            )
            assertEquals(code, parseInviteInput(inviteLink(code)), "pasted link with $code")
            assertEquals(
                code,
                parseInviteInput(inviteShareMessage("Goa trip", code)),
                "pasted message with $code",
            )
        }
        assertEquals("AB23CD45", parseInviteInput("ab23 cd45"), "legacy 8-character code")
        assertNull(parseInviteInput(""))
        assertNull(parseInviteInput("ABCD"), "too short to be either length")
        assertNull(parseInviteInput("not an invite at all"))
    }
}
