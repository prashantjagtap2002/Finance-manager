package com.example.financemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.Modifier.Node
import kotlinx.coroutines.launch

/**
 * Press feedback the way iOS does it: a highlight that appears the instant the finger lands and
 * fades once it lifts, rather than a ripple that blooms outward from the touch point after the
 * fact. Provided as `LocalIndication` by [com.example.financemanager.theme.FinanceManagerTheme],
 * so every `Modifier.clickable` row in the app picks it up without opting in.
 *
 * The highlight snaps on with no animation: any easing on the way *in* is latency between the
 * touch and the acknowledgement. Only the release is animated.
 */
class IosHighlightIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HighlightNode(interactionSource, color)

    override fun equals(other: Any?) = other is IosHighlightIndication && other.color == color
    override fun hashCode() = color.hashCode()
}

private class HighlightNode(
    private val interactionSource: InteractionSource,
    private val color: Color
) : Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { alpha.snapTo(1f); invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        alpha.animateTo(0f, tween(durationMillis = 220)) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a > 0f) drawRect(color.copy(alpha = color.alpha * a))
    }
}

/**
 * Shrinks the element slightly while it is held, as a physical object gives under a finger. The
 * press goes in on a stiff spring so it lands within a frame or two; the release uses a softer,
 * critically damped one so the element settles back without overshoot. Both are springs, so a
 * quick tap that releases mid-press reverses from wherever the scale currently is.
 *
 * The animated value is read in `graphicsLayer`, the draw phase, so holding a card never
 * recomposes it.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(pressed) {
        scale.animateTo(
            if (pressed) pressedScale else 1f,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (pressed) Spring.StiffnessHigh else Spring.StiffnessMediumLow
            )
        )
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
