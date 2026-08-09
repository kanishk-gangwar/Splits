package com.kanishk.splits

import com.kanishk.splits.data.ExpenseNotice
import com.kanishk.splits.data.NoticeKind
import com.kanishk.splits.data.SUMMARY_NOTIFICATION_ID
import com.kanishk.splits.data.notificationFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun notice(
    id: String = "e1",
    group: String = "Goa trip",
    emoji: String = "🏝️",
    title: String = "Dinner",
    amount: Long = 1_200_00,
    currency: String = "INR",
    actor: String = "Aarav",
    kind: NoticeKind = NoticeKind.Added,
) = ExpenseNotice(
    expenseId = id,
    groupId = "g",
    groupName = group,
    groupEmoji = emoji,
    title = title,
    amountMinor = amount,
    currencyCode = currency,
    actorName = actor,
    kind = kind,
)

class NotificationTextTest {

    /**
     * The bug this suite exists for: a mis-escaped template shipped the literal text
     * "${notices.size} updates in $where" to real phones.
     */
    @Test
    fun `no notification ever contains an uninterpolated template`() {
        val cases = listOf(
            listOf(notice()),
            listOf(notice(kind = NoticeKind.Updated)),
            listOf(notice(kind = NoticeKind.Removed)),
            listOf(notice(id = "a"), notice(id = "b")),
            listOf(notice(id = "a", group = "Goa trip"), notice(id = "b", group = "Flat 4B")),
        )

        for (case in cases) {
            val content = notificationFor(case)!!
            for (text in listOf(content.title, content.body)) {
                assertFalse(text.contains("\${"), "uninterpolated template in: $text")
                assertFalse(text.contains("$"), "stray dollar sign in: $text")
            }
        }
    }

    @Test
    fun `a single change reads as a sentence`() {
        val content = notificationFor(listOf(notice()))!!
        assertEquals("🏝️  Goa trip", content.title)
        assertEquals("Aarav added \"Dinner\" · ₹1,200", content.body)
    }

    @Test
    fun `the verb follows what happened`() {
        assertTrue(notificationFor(listOf(notice(kind = NoticeKind.Updated)))!!.body.contains("updated"))
        assertTrue(notificationFor(listOf(notice(kind = NoticeKind.Removed)))!!.body.contains("removed"))
    }

    @Test
    fun `several changes in one group name that group`() {
        val content = notificationFor(listOf(notice(id = "a"), notice(id = "b")))!!
        assertEquals("Splits", content.title)
        assertEquals("2 updates in Goa trip", content.body)
        assertEquals(SUMMARY_NOTIFICATION_ID, content.id)
    }

    @Test
    fun `several changes across groups are counted`() {
        val content = notificationFor(
            listOf(
                notice(id = "a", group = "Goa trip"),
                notice(id = "b", group = "Flat 4B"),
                notice(id = "c", group = "Office lunch"),
            ),
        )!!
        assertEquals("3 updates in 3 groups", content.body)
    }

    @Test
    fun `a single change reuses the expense id so an edit replaces its own notification`() {
        val first = notificationFor(listOf(notice(id = "same", kind = NoticeKind.Added)))!!
        val second = notificationFor(listOf(notice(id = "same", kind = NoticeKind.Updated)))!!
        assertEquals(first.id, second.id)
    }

    @Test
    fun `a blank title still reads properly`() {
        val content = notificationFor(listOf(notice(title = "")))!!
        assertTrue(content.body.contains("an expense"), content.body)
    }

    @Test
    fun `nothing to report produces no notification`() {
        assertNull(notificationFor(emptyList()))
    }
}
