package com.kanishk.splits.model

/**
 * Guesses a category from what the user typed as the title, so "Train to Goa" lands on
 * Transport instead of sitting on None.
 *
 * Deliberately conservative: it is a suggestion the user can override with one tap, and a
 * wrong guess is more annoying than no guess. Ambiguous words ("ticket", "bill", "fees") are
 * left out rather than assigned to whichever category seemed most likely.
 *
 * Rules are ordered, and the first hit wins. More specific categories come before broader ones
 * — "petrol" must reach Fuel before Transport gets a chance at it.
 */
private data class Rule(val categoryId: String, val keywords: List<String>)

private val Rules: List<Rule> = listOf(
    Rule("fuel", listOf("petrol", "diesel", "fuel", "cng", "refuel", "hpcl", "iocl")),
    Rule(
        "transport",
        listOf(
            "train", "metro", "bus", "uber", "ola", "rapido", "taxi", "cab", "auto",
            "rickshaw", "irctc", "toll", "parking", "commute", "transport", "ride",
        ),
    ),
    Rule(
        "flights",
        listOf("flight", "airfare", "indigo", "airline", "airlines", "airport", "baggage"),
    ),
    Rule(
        "stay",
        listOf("hotel", "airbnb", "hostel", "resort", "lodge", "homestay", "stay", "checkin"),
    ),
    Rule(
        "groceries",
        listOf(
            "grocery", "groceries", "supermarket", "bigbasket", "blinkit", "zepto",
            "dmart", "instamart", "vegetable", "sabzi", "milk", "kirana",
        ),
    ),
    Rule(
        "dining",
        listOf(
            "dinner", "lunch", "breakfast", "brunch", "food", "restaurant", "cafe",
            "coffee", "chai", "tea", "pizza", "burger", "biryani", "swiggy", "zomato",
            "snack", "snacks", "drink", "drinks", "beer", "bar", "pub", "dessert",
            "icecream", "dhaba", "takeaway", "starbucks",
        ),
    ),
    Rule("rent", listOf("rent", "lease", "landlord", "deposit", "maintenance")),
    Rule(
        "utilities",
        listOf("electricity", "power bill", "water bill", "gas bill", "utility", "utilities"),
    ),
    Rule(
        "internet",
        listOf(
            "internet", "wifi", "broadband", "recharge", "jio", "airtel", "vodafone",
            "postpaid", "prepaid", "phone bill",
        ),
    ),
    Rule(
        "entertainment",
        listOf(
            "movie", "movies", "cinema", "netflix", "spotify", "prime", "hotstar",
            "concert", "bookmyshow", "pvr", "inox", "gaming", "arcade", "bowling",
        ),
    ),
    Rule(
        "shopping",
        listOf("shopping", "amazon", "flipkart", "myntra", "clothes", "shoes", "mall", "ajio"),
    ),
    Rule(
        "health",
        listOf(
            "medicine", "medicines", "pharmacy", "chemist", "doctor", "hospital",
            "clinic", "medical", "apollo", "dentist", "lab test",
        ),
    ),
    Rule("fitness", listOf("gym", "yoga", "fitness", "workout", "trainer", "zumba")),
    Rule(
        "education",
        listOf("tuition", "course", "class", "classes", "textbook", "stationery", "exam"),
    ),
    Rule("gifts", listOf("gift", "gifts", "present", "birthday", "anniversary", "wedding")),
    Rule("pets", listOf("pet", "dog", "cat", "vet", "kennel", "petfood")),
    Rule(
        "household",
        listOf(
            "laundry", "cleaning", "maid", "repair", "plumber", "electrician",
            "furniture", "detergent", "househelp",
        ),
    ),
    Rule("insurance", listOf("insurance", "premium", "policy")),
    Rule("taxes", listOf("tax", "taxes", "gst", "fine", "penalty", "challan")),
    Rule("travel", listOf("visa", "passport", "tour", "trek", "sightseeing", "luggage")),
)

/**
 * Returns a built-in category id for [title], or null when nothing matches confidently.
 *
 * Matching is per word rather than substring, so "car" does not fire on "card" and "bar" does
 * not fire on "barber". Simple plurals are folded in.
 */
fun suggestCategoryId(title: String): String? {
    val text = title.lowercase().trim()
    if (text.isEmpty()) return null

    val words = text.split(' ', ',', '.', '-', '/', '(', ')', '&', '+', '\n', '\t')
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return null

    for (rule in Rules) {
        for (keyword in rule.keywords) {
            val matched = if (keyword.contains(' ')) {
                // Multi-word keywords only make sense against the whole string.
                text.contains(keyword)
            } else {
                words.any { word ->
                    word == keyword ||
                        word.removeSuffix("s") == keyword ||
                        // "buses" -> "bus", "classes" -> "class"
                        word.removeSuffix("es") == keyword
                }
            }
            if (matched) return rule.categoryId
        }
    }
    return null
}

/** The resolved suggestion, ready to render. */
fun suggestCategory(title: String): ExpenseCategory? = resolveCategory(suggestCategoryId(title))
