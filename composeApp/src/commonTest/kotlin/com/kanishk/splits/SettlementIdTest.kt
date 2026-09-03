package com.kanishk.splits

import com.kanishk.splits.model.dayIndexOf
import com.kanishk.splits.model.settlementId
import com.kanishk.splits.model.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The id is the whole mechanism behind "two people record the same settlement and it counts
 * once", so it is worth pinning down properly. If the derivation drifts, two devices stop
 * agreeing and the duplicate quietly comes back — a bug nobody notices until the balances are
 * wrong.
 */
class SettlementIdTest {

    @Test
    fun sameSuggestionOnTwoDevicesYieldsTheSameId() {
        val a = settlementId("group-1", "member-a", "member-b", 50_000, 20_000)
        val b = settlementId("group-1", "member-a", "member-b", 50_000, 20_000)
        assertEquals(a, b)
    }

    /** The server's id columns and the rest of the app expect newId()'s shape. */
    @Test
    fun idHasTheSameShapeAsARandomOne() {
        val pattern = Regex("^[0-9a-f]{32}$")
        val id = settlementId("group-1", "member-a", "member-b", 50_000, 20_000)
        assertTrue(pattern.matches(id), "settlement id '$id' is not 32 lower-case hex characters")
    }

    @Test
    fun everyPartOfTheSuggestionChangesTheId() {
        val base = settlementId("group-1", "member-a", "member-b", 50_000, 20_000)
        assertNotEquals(base, settlementId("group-2", "member-a", "member-b", 50_000, 20_000))
        assertNotEquals(base, settlementId("group-1", "member-c", "member-b", 50_000, 20_000))
        assertNotEquals(base, settlementId("group-1", "member-a", "member-c", 50_000, 20_000))
        assertNotEquals(base, settlementId("group-1", "member-a", "member-b", 50_001, 20_000))
        assertNotEquals(base, settlementId("group-1", "member-a", "member-b", 50_000, 20_001))
    }

    /** Direction matters: A paying B is not B paying A. */
    @Test
    fun directionIsPartOfTheId() {
        assertNotEquals(
            settlementId("group-1", "member-a", "member-b", 50_000, 20_000),
            settlementId("group-1", "member-b", "member-a", 50_000, 20_000),
        )
    }

    /**
     * The same debt recurring next week is a new payment, not a re-run of the old one. Without
     * the day in the key it would overwrite the earlier settlement and erase it.
     */
    @Test
    fun theSameDebtOnADifferentDayIsADifferentPayment() {
        val monday = dayIndexOf(1_700_000_000_000)
        val tuesday = dayIndexOf(1_700_000_000_000 + 86_400_000)
        assertNotEquals(
            settlementId("group-1", "member-a", "member-b", 50_000, monday),
            settlementId("group-1", "member-a", "member-b", 50_000, tuesday),
        )
    }

    @Test
    fun dayIndexIsStableWithinAUtcDayAndTicksOverAtMidnight() {
        val midnight = 1_700_006_400_000L // 2023-11-15T00:00:00Z
        assertEquals(dayIndexOf(midnight), dayIndexOf(midnight + 86_399_999))
        assertEquals(dayIndexOf(midnight) + 1, dayIndexOf(midnight + 86_400_000))
        // Before the epoch the division has to floor, not truncate towards zero.
        assertEquals(-1L, dayIndexOf(-1))
    }

    /**
     * The published SHA-256 vectors. The hash lives in this repo rather than in a library, so
     * these are the only thing standing between a typo in the round constants and two devices
     * silently disagreeing.
     */
    @Test
    fun hashMatchesThePublishedVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
        // 56 bytes: the length that forces a second padding block.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }
}