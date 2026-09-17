package com.gratitudegarden.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Central motion tokens so the whole app animates with one voice. Mirrors the
 * [PgInk]/[PgPrimary] colour-token convention in Color.kt — reach for these
 * instead of hand-rolling durations/springs at each call site.
 */
object PgMotion {
    const val FastMillis = 140
    const val MediumMillis = 260
    const val SlowMillis = 460

    fun <T> fast(): AnimationSpec<T> = tween(FastMillis)
    fun <T> medium(): AnimationSpec<T> = tween(MediumMillis)

    /** Springy, slightly bouncy — for presses and selection "pops". */
    fun <T> pop(): AnimationSpec<T> =
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)

    /** Gentle settle — for content easing into place. */
    fun <T> settle(): AnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Scale the element down slightly while it's pressed, springing back on release.
 * Wire the *same* [interactionSource] into the element's `clickable(...)` so the
 * press state and the scale stay in sync.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PgMotion.pop(),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
