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
}
