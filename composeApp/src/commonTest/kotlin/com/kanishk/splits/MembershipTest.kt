package com.kanishk.splits

import com.kanishk.splits.model.Group
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MY_DEVICE = "device-mine"
private const val OTHER_DEVICE = "device-theirs"

private fun member(id: String, claimedBy: String? = null) =
    Member(id = id, groupId = "g", name = id, colorIndex = 0, claimedByDeviceId = claimedBy)

private fun detail(vararg members: Member) = GroupDetail(
    group = Group(
        id = "g",
        name = "Goa trip",
        emoji = "🏝️",
        currencyCode = "INR",
        inviteCode = "ABCD2345",
        adminMemberId = "aarav",
        createdAt = 0,
        updatedAt = 0,
        archived = false,
        hidden = false,
    ),
    members = members.toList(),
    expenses = emptyList(),
)

class MembershipTest {

    @Test
    fun `a claimed name is not available to anyone else`() {
        val group = detail(
            member("aarav", claimedBy = OTHER_DEVICE),
            member("bhavna"),
            member("chetan"),
        )

        assertEquals(listOf("bhavna", "chetan"), group.availableMembers.map { it.id })
        assertEquals(listOf("aarav"), group.joinedMembers.map { it.id })
    }

    @Test
    fun `the joined count reflects devices, not names on the list`() {
        // Three participants exist, but only one has actually turned up.
        val group = detail(
            member("aarav", claimedBy = OTHER_DEVICE),
            member("bhavna"),
            member("chetan"),
        )
        assertEquals(1, group.joinedCount)
        assertEquals(3, group.members.size)
        assertFalse(group.everyoneJoined())
    }

    @Test
    fun `everyone joined only when every name is claimed`() {
        val all = detail(
            member("aarav", claimedBy = OTHER_DEVICE),
            member("bhavna", claimedBy = MY_DEVICE),
        )
        assertTrue(all.everyoneJoined())
    }

    @Test
    fun `an empty group has not had everyone join`() {
        // Guards against `0 == 0` reading as "everyone is here".
        assertFalse(detail().everyoneJoined())
    }

    @Test
    fun `giving up a name puts it back in the pool`() {
        val before = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertEquals(listOf("bhavna"), before.availableMembers.map { it.id })

        // What releaseMyIdentity does to the row.
        val after = detail(member("aarav", claimedBy = null), member("bhavna"))
        assertEquals(listOf("aarav", "bhavna"), after.availableMembers.map { it.id })
        assertEquals(0, after.joinedCount)
    }

    @Test
    fun `this device finds itself and loses itself correctly`() {
        val group = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertEquals("aarav", group.meIn(MY_DEVICE)?.id)
        assertNull(group.meIn(OTHER_DEVICE))
        assertNull(group.meIn(null))
    }

    @Test
    fun `admin rights follow the device holding the admin name`() {
        val mine = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertTrue(mine.isAdmin(MY_DEVICE))
        assertFalse(mine.isAdmin(OTHER_DEVICE))

        // Give the admin name up and the rights go with it.
        val released = detail(member("aarav"), member("bhavna", claimedBy = MY_DEVICE))
        assertFalse(released.isAdmin(MY_DEVICE))
    }

    @Test
    fun `only your own name is yours to rename`() {
        val group = detail(
            member("aarav", claimedBy = OTHER_DEVICE),
            member("bhavna", claimedBy = MY_DEVICE),
            member("chetan"),
        )

        assertTrue(group.canRename(MY_DEVICE, "bhavna"))
        assertFalse(group.canRename(MY_DEVICE, "aarav"), "cannot retype somebody else's name")
        assertFalse(group.canRename(MY_DEVICE, "chetan"), "an unclaimed name is nobody's to edit")
        assertFalse(group.canRename(null, "bhavna"))
    }

    @Test
    fun `being admin does not extend to renaming other people`() {
        val group = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertTrue(group.isAdmin(MY_DEVICE))
        assertFalse(group.canRename(MY_DEVICE, "bhavna"))
    }

    @Test
    fun `only the admin can remove a participant`() {
        val asAdmin = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertTrue(asAdmin.canRemove(MY_DEVICE, "bhavna"))

        // Same group, seen from a device that is not the admin.
        val asMember = detail(member("aarav"), member("bhavna", claimedBy = MY_DEVICE))
        assertFalse(asMember.canRemove(MY_DEVICE, "aarav"))
        assertFalse(asMember.canRemove(MY_DEVICE, "bhavna"), "not even yourself")
        assertFalse(asMember.canRemove(null, "aarav"))
    }

    @Test
    fun `the admin cannot remove themselves`() {
        // Otherwise the group is left with nobody who can delete it.
        val group = detail(member("aarav", claimedBy = MY_DEVICE), member("bhavna"))
        assertFalse(group.canRemove(MY_DEVICE, "aarav"))
    }

    @Test
    fun `every name taken leaves nothing to claim`() {
        val group = detail(
            member("aarav", claimedBy = OTHER_DEVICE),
            member("bhavna", claimedBy = OTHER_DEVICE),
        )
        assertTrue(group.availableMembers.isEmpty())
        assertTrue(group.everyoneJoined())
    }
}
