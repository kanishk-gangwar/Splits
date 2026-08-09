package com.kanishk.splits.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Avatar hues. Members keep their colour for the life of the group, which makes the
 * balances list scannable without reading a single name.
 */
private val AvatarColors = listOf(
    Color(0xFF7C5CFF), // violet
    Color(0xFF31D0AA), // mint
    Color(0xFFFF8A5C), // coral
    Color(0xFF4FA9FF), // sky
    Color(0xFFFFC24B), // amber
    Color(0xFFF06BB0), // pink
    Color(0xFF66D46B), // green
    Color(0xFF9B7BFF), // lilac
    Color(0xFF2FC3D6), // teal
    Color(0xFFFF6B6B), // red
)

fun avatarColor(index: Int): Color = AvatarColors[abs(index) % AvatarColors.size]

/** Tints behind category glyphs — deliberately low-chroma so they never fight the amount. */
private val CategoryTints = listOf(
    Color(0xFF7C5CFF),
    Color(0xFFFF8A5C),
    Color(0xFF4FA9FF),
    Color(0xFFFFC24B),
    Color(0xFF31D0AA),
    Color(0xFFF06BB0),
    Color(0xFF66D46B),
    Color(0xFF2FC3D6),
)

fun categoryTint(index: Int): Color = CategoryTints[abs(index) % CategoryTints.size]

/** Emoji offered when creating a group. */
val GroupEmojis = listOf(
    "🏠", "✈️", "🍜", "🎉", "🏝️", "🚗", "🎓", "💼",
    "🏔️", "🎬", "⛺️", "🛍️", "🏋️", "🐾", "💍", "🎸",
)
