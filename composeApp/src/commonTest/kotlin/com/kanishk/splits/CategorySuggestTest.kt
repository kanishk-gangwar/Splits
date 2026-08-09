package com.kanishk.splits

import com.kanishk.splits.model.BuiltInCategories
import com.kanishk.splits.model.suggestCategoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorySuggestTest {

    @Test
    fun `the reported case is tagged`() {
        // "train" used to land on None.
        assertEquals("transport", suggestCategoryId("Train"))
        assertEquals("transport", suggestCategoryId("Train to Goa"))
        assertEquals("transport", suggestCategoryId("train tickets"))
    }

    @Test
    fun `everyday titles land somewhere sensible`() {
        assertEquals("dining", suggestCategoryId("Dinner at Toit"))
        assertEquals("dining", suggestCategoryId("Swiggy"))
        assertEquals("groceries", suggestCategoryId("Blinkit order"))
        assertEquals("stay", suggestCategoryId("Hotel in Anjuna"))
        assertEquals("flights", suggestCategoryId("Indigo flight"))
        assertEquals("transport", suggestCategoryId("Uber to airport"))
        assertEquals("health", suggestCategoryId("Medicines"))
        assertEquals("entertainment", suggestCategoryId("Movie night"))
    }

    @Test
    fun `more specific rules win over broader ones`() {
        // Petrol is a transport-shaped word but belongs to Fuel.
        assertEquals("fuel", suggestCategoryId("Petrol for the car"))
        assertEquals("fuel", suggestCategoryId("Diesel"))
    }

    @Test
    fun `matching is per word, not substring`() {
        // "bar" must not fire on "barber"; "cat" must not fire on "category".
        assertTrue(suggestCategoryId("Barber") != "dining")
        assertTrue(suggestCategoryId("Category review") != "pets")
        assertTrue(suggestCategoryId("Cardamom") != "transport")
    }

    @Test
    fun `simple plurals still match`() {
        assertEquals("transport", suggestCategoryId("Buses"))
        assertEquals("gifts", suggestCategoryId("Gifts for the team"))
    }

    @Test
    fun `case and punctuation do not matter`() {
        assertEquals("dining", suggestCategoryId("DINNER, drinks"))
        assertEquals("transport", suggestCategoryId("metro/bus"))
    }

    @Test
    fun `nothing recognisable suggests nothing`() {
        assertNull(suggestCategoryId(""))
        assertNull(suggestCategoryId("   "))
        assertNull(suggestCategoryId("Zzzz"))
        assertNull(suggestCategoryId("Misc thing"))
    }

    @Test
    fun `every suggestion maps to a real built-in category`() {
        // A rule pointing at an id that no longer exists would silently suggest nothing.
        val ids = BuiltInCategories.map { it.id }.toSet()
        val samples = listOf(
            "train", "petrol", "flight", "hotel", "grocery", "dinner", "rent",
            "electricity", "wifi", "movie", "shopping", "medicine", "gym",
            "tuition", "gift", "dog", "laundry", "insurance", "gst", "visa",
        )
        for (sample in samples) {
            val id = suggestCategoryId(sample)
            assertTrue(id != null, "\"$sample\" suggested nothing")
            assertTrue(id in ids, "\"$sample\" suggested unknown category \"$id\"")
        }
    }
}
