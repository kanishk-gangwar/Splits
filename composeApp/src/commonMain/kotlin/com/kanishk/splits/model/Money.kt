package com.kanishk.splits.model

import kotlin.math.abs

/**
 * Every amount in the app is stored as an integer number of minor units (paise, cents…).
 * Nothing is ever a Double — rounding drift on split arithmetic is the one bug users notice.
 */
data class Currency(val code: String, val symbol: String, val label: String)

val SupportedCurrencies: List<Currency> = listOf(
    Currency("INR", "₹", "Indian Rupee"),
    Currency("USD", "$", "US Dollar"),
    Currency("EUR", "€", "Euro"),
    Currency("GBP", "£", "British Pound"),
    Currency("AED", "AED ", "UAE Dirham"),
    Currency("SGD", "S$", "Singapore Dollar"),
    Currency("AUD", "A$", "Australian Dollar"),
    Currency("CAD", "C$", "Canadian Dollar"),
    Currency("JPY", "¥", "Japanese Yen"),
    Currency("CHF", "CHF ", "Swiss Franc"),
    Currency("THB", "฿", "Thai Baht"),
    Currency("MYR", "RM", "Malaysian Ringgit"),
    Currency("LKR", "Rs ", "Sri Lankan Rupee"),
    Currency("NPR", "Rs ", "Nepalese Rupee"),
)

fun currencyOf(code: String): Currency =
    SupportedCurrencies.firstOrNull { it.code == code } ?: Currency(code, "$code ", code)

fun symbolOf(code: String): String = currencyOf(code).symbol

/** Renders `123456` as `1,234.56`, grouping with the Indian system for INR. */
fun formatMinor(
    amountMinor: Long,
    currencyCode: String,
    withSymbol: Boolean = true,
    alwaysShowDecimals: Boolean = false,
): String {
    val negative = amountMinor < 0
    val abs = abs(amountMinor)
    val major = abs / 100
    val minor = (abs % 100).toInt()

    val digits = major.toString()
    val grouped = if (currencyCode == "INR" || currencyCode == "NPR" || currencyCode == "LKR") {
        groupIndian(digits)
    } else {
        groupWestern(digits)
    }

    val decimals = if (minor != 0 || alwaysShowDecimals) {
        "." + minor.toString().padStart(2, '0')
    } else {
        ""
    }

    return buildString {
        if (negative) append('-')
        if (withSymbol) append(symbolOf(currencyCode))
        append(grouped)
        append(decimals)
    }
}

/** 1234567 -> "12,34,567" */
private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    val chunks = mutableListOf<String>()
    var remaining = head
    while (remaining.length > 2) {
        chunks += remaining.takeLast(2)
        remaining = remaining.dropLast(2)
    }
    if (remaining.isNotEmpty()) chunks += remaining
    return chunks.reversed().joinToString(",") + "," + tail
}

/** 1234567 -> "1,234,567" */
private fun groupWestern(digits: String): String {
    if (digits.length <= 3) return digits
    val chunks = mutableListOf<String>()
    var remaining = digits
    while (remaining.length > 3) {
        chunks += remaining.takeLast(3)
        remaining = remaining.dropLast(3)
    }
    if (remaining.isNotEmpty()) chunks += remaining
    return chunks.reversed().joinToString(",")
}

/**
 * Parses free typing in the amount field ("1,200.5", "1200.", "") into minor units.
 * Returns null while the text is not yet a usable number.
 */
fun parseAmountToMinor(text: String): Long? {
    val cleaned = text.filter { it.isDigit() || it == '.' }
    if (cleaned.isEmpty()) return null
    val parts = cleaned.split('.')
    if (parts.size > 2) return null
    val major = parts[0].ifEmpty { "0" }
    val minor = parts.getOrNull(1).orEmpty().take(2).padEnd(2, '0')
    val majorLong = major.toLongOrNull() ?: return null
    val minorLong = minor.toLongOrNull() ?: return null
    return majorLong * 100 + minorLong
}

/** Renders minor units back into the editable text form (no symbol, no grouping). */
fun minorToEditText(amountMinor: Long): String {
    if (amountMinor == 0L) return ""
    val major = amountMinor / 100
    val minor = (amountMinor % 100).toInt()
    return if (minor == 0) major.toString() else "$major.${minor.toString().padStart(2, '0')}"
}

/**
 * Splits [totalMinor] across [count] people so the parts always add back up to the total.
 * The leftover minor units land on the first few people rather than vanishing.
 */
fun splitEvenly(totalMinor: Long, count: Int): List<Long> {
    if (count <= 0) return emptyList()
    val base = totalMinor / count
    val remainder = (totalMinor - base * count).toInt()
    return List(count) { index -> base + if (index < abs(remainder)) (if (remainder < 0) -1L else 1L) else 0L }
}

/** Same idea, but weighted — used for percentage and share splits. */
fun splitByWeights(totalMinor: Long, weights: List<Long>): List<Long> {
    val weightTotal = weights.sum()
    if (weightTotal == 0L) return splitEvenly(totalMinor, weights.size)

    val raw = weights.map { weight -> totalMinor * weight / weightTotal }
    var remainder = totalMinor - raw.sum()

    // Hand the leftover units to whoever was rounded down hardest.
    val order = weights.indices.sortedByDescending { index ->
        totalMinor * weights[index] % weightTotal
    }
    val result = raw.toMutableList()
    var cursor = 0
    while (remainder != 0L && order.isNotEmpty()) {
        val index = order[cursor % order.size]
        if (remainder > 0) {
            result[index] = result[index] + 1
            remainder--
        } else {
            result[index] = result[index] - 1
            remainder++
        }
        cursor++
    }
    return result
}
