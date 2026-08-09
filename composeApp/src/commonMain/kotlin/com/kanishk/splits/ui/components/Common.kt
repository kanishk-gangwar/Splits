package com.kanishk.splits.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.ui.theme.MoneyTextStyle
import com.kanishk.splits.ui.theme.SplitsTheme
import com.kanishk.splits.ui.theme.avatarColor

// ------------------------------------------------------------------ avatars --

fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '.', '-').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(
    name: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ring: Boolean = false,
) {
    val tint = avatarColor(colorIndex)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.22f))
            .then(
                if (ring) Modifier.border(2.dp, tint, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = tint,
            fontSize = (size.value * 0.36f).sp,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** Overlapping avatars used on group cards. */
@Composable
fun AvatarStack(
    members: List<Member>,
    modifier: Modifier = Modifier,
    max: Int = 4,
    size: Dp = 28.dp,
) {
    val shown = members.take(max)
    val overflow = members.size - shown.size
    val backdrop = MaterialTheme.colorScheme.surfaceContainer

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy((-size / 3))) {
        shown.forEach { member ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(backdrop)
                    .padding(1.5.dp),
            ) {
                Avatar(member.name, member.colorIndex, size = size - 3.dp)
            }
        }
        if (overflow > 0) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(backdrop)
                    .padding(1.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(size - 3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (size.value * 0.32f).sp,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- money --

/**
 * The one place that decides what a signed balance *means*. Everything else asks this
 * so "you are owed" is never accidentally rendered in the colour of "you owe".
 */
@Composable
fun balanceColor(netMinor: Long): Color = when {
    netMinor > 0 -> SplitsTheme.money.positive
    netMinor < 0 -> SplitsTheme.money.negative
    else -> SplitsTheme.money.neutral
}

@Composable
fun balanceContainerColor(netMinor: Long): Color = when {
    netMinor > 0 -> SplitsTheme.money.positiveContainer
    netMinor < 0 -> SplitsTheme.money.negativeContainer
    else -> SplitsTheme.money.neutralContainer
}

@Composable
fun MoneyText(
    amountMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    showSign: Boolean = false,
) {
    val prefix = if (showSign && amountMinor > 0) "+" else ""
    Text(
        text = prefix + formatMinor(amountMinor, currencyCode),
        modifier = modifier,
        color = color,
        style = MoneyTextStyle.copy(fontSize = fontSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Tinted "you are owed ₹420" chip. */
@Composable
fun BalancePill(
    netMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    settledLabel: String = "settled up",
    owedLabel: String = "you are owed",
    oweLabel: String = "you owe",
    compact: Boolean = false,
) {
    val tint by animateColorAsState(balanceColor(netMinor))
    val container by animateColorAsState(balanceContainerColor(netMinor))

    Surface(
        modifier = modifier,
        color = container,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = when {
                    netMinor > 0 -> owedLabel
                    netMinor < 0 -> oweLabel
                    else -> settledLabel
                },
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.85f),
                fontSize = 10.sp,
                maxLines = 1,
            )
            if (netMinor != 0L) {
                MoneyText(
                    amountMinor = if (netMinor < 0) -netMinor else netMinor,
                    currencyCode = currencyCode,
                    color = tint,
                    fontSize = if (compact) 13.sp else 15.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ surface --

/** The app's one card treatment: flat fill, hairline outline, generous radius. */
@Composable
fun SplitsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = color,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = { content() },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
}

@Composable
fun GlyphTile(
    glyph: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun EmptyState(
    glyph: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontSize = 34.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ------------------------------------------------------------- segmented UI --

/** Sliding pill selector used for Active/Archived and the split modes. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val weight by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = weight)
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** Nudges a badge to sit on the corner of whatever it decorates. */
@Composable
fun CornerBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.offset(x = 4.dp, y = (-4).dp),
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 10.sp,
        )
    }
}
