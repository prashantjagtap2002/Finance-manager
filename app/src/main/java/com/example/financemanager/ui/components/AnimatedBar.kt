package com.example.financemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Bars that animate their length in the draw phase.
 *
 * Sizing a bar with `fillMaxHeight(fraction)` runs the layout pass again on every frame, and
 * reading the animated value in composition recomposes the bar — and everything around it — on
 * every frame too. The daily-spending chart did both for each of up to 31 bars at once, so a
 * single frame of its intro cost 31 recompositions and 31 layout passes. Drawing the bar instead
 * costs one repaint and nothing else.
 *
 * That is also why the fraction comes in as a lambda: taking a `Float` would read the animation in
 * composition at the call site and hand the cost straight back.
 */

/** The spring these bars settle with: lively enough to read as physical, no visible wobble. */
val BarSpring: AnimationSpec<Float> = spring(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * A fraction that eases from 0 to [target] when it first appears and re-springs whenever [target]
 * changes.
 *
 * `animateFloatAsState` starts *at* its target, so growing a chart in on arrival needed a separate
 * "intro progress" animation multiplied into every bar's target — which re-aimed every spring on
 * every frame of the intro, while the parent recomposed around them. Starting at zero gets the
 * same entrance from the one animation that was already there.
 */
@Composable
fun rememberBarFraction(target: Float, spec: AnimationSpec<Float> = BarSpring): State<Float> {
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(target) { fraction.animateTo(target.coerceIn(0f, 1f), spec) }
    return fraction.asState()
}

/**
 * A bar that grows upward from the bottom, with its top corners rounded.
 *
 * [fraction] is how much of the available height it fills, read on every frame inside the draw
 * lambda.
 */
@Composable
fun VerticalBar(
    fraction: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp
) {
    // Rewound rather than rebuilt: this runs once per bar per frame, and at 31 bars a fresh Path
    // each time is 31 allocations a frame for a shape that never changes topology.
    val path = remember { Path() }

    Spacer(
        modifier = modifier.drawBehind {
            val filled = size.height * fraction().coerceIn(0f, 1f)
            if (filled <= 0f || size.width <= 0f) return@drawBehind

            val radius = min(cornerRadius.toPx(), min(filled, size.width / 2f))
            val top = size.height - filled

            path.rewind()
            path.moveTo(0f, size.height)
            path.lineTo(0f, top + radius)
            path.quadraticTo(0f, top, radius, top)
            path.lineTo(size.width - radius, top)
            path.quadraticTo(size.width, top, size.width, top + radius)
            path.lineTo(size.width, size.height)
            path.close()

            drawPath(path, color)
        }
    )
}

/**
 * A pill-shaped bar that grows from the start edge, drawn over [trackColor].
 *
 * [fraction] is read on every frame inside the draw lambda, so the track and the fill are one
 * repaint rather than a relayout of whatever row they sit in.
 */
@Composable
fun HorizontalBar(
    fraction: () -> Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier.drawBehind {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = radius)

            val filled = size.width * fraction().coerceIn(0f, 1f)
            // Below one pill's width there is nothing a rounded rect can draw that reads as a bar.
            if (filled < size.height / 2f) return@drawBehind

            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(filled, size.height),
                cornerRadius = radius
            )
        }
    )
}
