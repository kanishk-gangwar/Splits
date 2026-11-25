package com.kanishk.splits.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Purple = Color(0xFF7C5CFF)
private val Teal = Color(0xFF31D0AA)

// The launch screen's icon box. Android's system splash draws its 108-unit icon (splash_ring.xml)
// into 288dp, centred in the window; drawing on the same grid at the same place means the first
// frame here is pixel-for-pixel the system's, so the hand-over has no seam.
private val IconBox = 288.dp
private const val GRID = 108f
private const val CENTER = 54f
private const val OUTER = 24f
private const val INNER = 12f

// Timeline, in ms. The same beats as the web splash (index.html).
private const val BAR_START = 150f
private const val BAR_END = 530f
private const val PART_START = 480f
private const val PART_END = 1000f
private const val NAME_START = 760f
private const val NAME_END = 1180f
private const val INTRO_MS = 1300
private const val FADE_MS = 300

private val Overshoot = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)

/**
 * The app icon acting out its name: the whole ring, the bar drops through it, the halves slide
 * apart and "Splits" fades in; then the splash fades away and calls [onFinished].
 */
@Composable
fun SplitsSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    // Same colours as the system launch screen (values/colors.xml, values-night/colors.xml).
    val background = if (dark) Color(0xFF121212) else Color.White
    val ink = if (dark) Color.White else Color(0xFF121212)

    val clock = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        clock.animateTo(INTRO_MS.toFloat(), tween(INTRO_MS, easing = LinearEasing))
        launch { fade.animateTo(0f, tween(FADE_MS)) }.join()
        onFinished()
    }

    val t = clock.value
    val bar = phase(t, BAR_START, BAR_END, FastOutSlowInEasing)
    val part = phase(t, PART_START, PART_END, Overshoot)
    val name = phase(t, NAME_START, NAME_END, FastOutSlowInEasing)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = fade.value
                val grow = 1f + 0.04f * (1f - fade.value)
                scaleX = grow
                scaleY = grow
            }
            .background(background)
            // Taps must not reach the screen underneath while it is covered.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(IconBox)) {
            val unit = size.width / GRID
            drawRing(unit, separation = 7f * part, barProgress = bar, ink = ink)
        }
        Text(
            "Splits",
            color = ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset(y = 104.dp)
                .graphicsLayer {
                    alpha = name
                    translationY = (1f - name) * 8.dp.toPx()
                },
        )
    }
}

/** Progress of one beat of the timeline, 0 before [start], 1 after [end], eased in between. */
private fun phase(t: Float, start: Float, end: Float, easing: Easing): Float =
    easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

private fun DrawScope.drawRing(unit: Float, separation: Float, barProgress: Float, ink: Color) {
    val c = Offset(CENTER * unit, CENTER * unit)
    val outer = Rect(c, OUTER * unit)
    val inner = Rect(c, INNER * unit)

    // Left half: top → round the left → bottom on the outside, then back up the inside.
    val left = Path().apply {
        moveTo(c.x, c.y - OUTER * unit)
        arcTo(outer, 270f, -180f, false)
        lineTo(c.x, c.y + INNER * unit)
        arcTo(inner, 90f, 180f, false)
        close()
    }
    val right = Path().apply {
        moveTo(c.x, c.y - OUTER * unit)
        arcTo(outer, 270f, 180f, false)
        lineTo(c.x, c.y + INNER * unit)
        arcTo(inner, 90f, -180f, false)
        close()
    }
    translate(left = -separation * unit) { drawPath(left, Purple) }
    translate(left = separation * unit) { drawPath(right, Teal) }

    if (barProgress > 0f) {
        // The launcher icon's divider (ic_launcher_foreground.xml) runs from 26 to 82; it drops
        // in from the top.
        val top = 26f * unit
        val height = (82f - 26f) * unit * barProgress
        drawRect(ink, topLeft = Offset(c.x - 1.25f * unit, top), size = Size(2.5f * unit, height))
    }
}
