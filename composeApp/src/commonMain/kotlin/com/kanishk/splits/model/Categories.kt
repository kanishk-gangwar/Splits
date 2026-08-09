package com.kanishk.splits.model

/**
 * Categories are optional. An expense can carry:
 *  - `null`                -> uncategorised
 *  - a built-in id         -> one of [BuiltInCategories]
 *  - `custom:<label>`      -> whatever the user typed
 */
data class ExpenseCategory(
    val id: String,
    val label: String,
    val glyph: String,
    val toneIndex: Int,
    val isCustom: Boolean = false,
)

private const val CUSTOM_PREFIX = "custom:"

val BuiltInCategories: List<ExpenseCategory> = listOf(
    ExpenseCategory("groceries", "Groceries", "🛒", 0),
    ExpenseCategory("dining", "Food & Drink", "🍜", 1),
    ExpenseCategory("transport", "Transport", "🚕", 2),
    ExpenseCategory("fuel", "Fuel", "⛽️", 3),
    ExpenseCategory("stay", "Stay", "🏨", 4),
    ExpenseCategory("flights", "Flights", "✈️", 5),
    ExpenseCategory("rent", "Rent", "🏠", 6),
    ExpenseCategory("utilities", "Utilities", "💡", 7),
    ExpenseCategory("internet", "Internet & Phone", "📶", 8),
    ExpenseCategory("entertainment", "Entertainment", "🎬", 9),
    ExpenseCategory("shopping", "Shopping", "🛍️", 10),
    ExpenseCategory("health", "Health", "💊", 11),
    ExpenseCategory("fitness", "Fitness", "🏋️", 12),
    ExpenseCategory("education", "Education", "📚", 13),
    ExpenseCategory("gifts", "Gifts", "🎁", 14),
    ExpenseCategory("pets", "Pets", "🐾", 15),
    ExpenseCategory("household", "Household", "🧺", 16),
    ExpenseCategory("insurance", "Insurance", "🛡️", 17),
    ExpenseCategory("taxes", "Taxes & Fees", "🧾", 18),
    ExpenseCategory("travel", "Travel", "🧳", 19),
    ExpenseCategory("other", "Other", "📌", 20),
)

/** The pill shown on a settlement row. Never selectable in the category picker. */
val ReimbursementCategory = ExpenseCategory("__reimbursement", "Settlement", "🤝", 21)

fun customCategoryId(label: String): String = CUSTOM_PREFIX + label.trim()

fun isCustomCategoryId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

fun customCategoryLabel(id: String): String = id.removePrefix(CUSTOM_PREFIX)

/** Resolves whatever is stored on the expense into something renderable. */
fun resolveCategory(id: String?): ExpenseCategory? {
    if (id == null) return null
    if (isCustomCategoryId(id)) {
        val label = customCategoryLabel(id)
        if (label.isEmpty()) return null
        return ExpenseCategory(
            id = id,
            label = label,
            glyph = "🏷️",
            // Stable tone so the same custom label keeps the same colour everywhere.
            toneIndex = label.fold(0) { acc, c -> acc + c.code },
            isCustom = true,
        )
    }
    return BuiltInCategories.firstOrNull { it.id == id }
}
