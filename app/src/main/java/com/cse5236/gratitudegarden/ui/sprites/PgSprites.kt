package com.cse5236.gratitudegarden.ui.sprites

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

// ── Stroke / fill icons ──────────────────────────────────────────
// Recreated from the design's SVG path data (24×24 viewport).

enum class PgIconName { Mail, Lock, Eye, User, Mic, Flame, Home, Shop, Leaf, Cog, Check, Back }

private fun ImageVector.Builder.strokePath(d: String, color: Color, width: Float = 1.9f) {
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        stroke = SolidColor(color),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.fillPath(d: String, color: Color) {
    addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(color))
}

private fun buildIcon(name: PgIconName, color: Color): ImageVector {
    val b = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    )
    when (name) {
        PgIconName.Mail -> b.strokePath("M4 6h16v12H4z M4 7l8 6 8-6", color)
        PgIconName.Lock -> b.strokePath("M6 11h12v9H6z M9 11V8a3 3 0 0 1 6 0v3", color)
        PgIconName.Eye -> b.strokePath("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z", color)
        PgIconName.User -> b.strokePath("M4 20a8 8 0 0 1 16 0 M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8z", color)
        PgIconName.Home -> b.strokePath("M4 11l8-7 8 7v9H4z", color)
        PgIconName.Shop -> b.strokePath("M4 8h16l-1 12H5z M8 8V6a4 4 0 0 1 8 0v2", color)
        PgIconName.Cog -> b.strokePath(
            "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M12 3v2 M12 19v2 M5 12H3 M21 12h-2 " +
                "M6 6l1.5 1.5 M16.5 16.5L18 18 M6 18l1.5-1.5 M16.5 7.5L18 6",
            color,
        )
        PgIconName.Check -> b.strokePath("M5 12l4 4 10-10", color, width = 2.6f)
        PgIconName.Back -> b.strokePath("M15 6l-6 6 6 6", color)
        PgIconName.Leaf -> b.fillPath("M4 20 C4 10 12 4 20 4 C20 12 14 20 4 20 Z", color)
        PgIconName.Flame -> b.fillPath("M12 2 C12 2 7 7 7 12 a5 5 0 0 0 10 0 C17 9 14 7 12 2 Z", color)
        PgIconName.Mic -> {
            b.fillPath("M9 6 a3 3 0 0 1 6 0 v6 a3 3 0 0 1 -6 0 z", color)
            b.strokePath("M6 12a6 6 0 0 0 12 0 M12 18v3", color)
        }
    }
    return b.build()
}

@Composable
fun PgIcon(name: PgIconName, color: Color, size: Dp) {
    val vector = remember(name, color) { buildIcon(name, color) }
    Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(size))
}

// ── Currency coin ────────────────────────────────────────────────
@Composable
fun CoinIcon(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        drawCircle(Color(0xFFE8A92E), radius = s * 0.5f, center = c)
        drawCircle(Color(0xFFF4C542), radius = s * 0.33f, center = c)
        val w = s * 0.06f
        drawLine(Color(0xFFA66E18), Offset(c.x - s * 0.1f, c.y - s * 0.18f), Offset(c.x - s * 0.1f, c.y + s * 0.18f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(Color(0xFFA66E18), Offset(c.x + s * 0.1f, c.y - s * 0.18f), Offset(c.x + s * 0.1f, c.y + s * 0.18f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

// ── Plant sprites ────────────────────────────────────────────────
data class PlantColors(val bloom: Color?, val center: Color?, val leaf: Color)

object PlantPalette {
    val Tulip = PlantColors(Color(0xFFE85D75), Color(0xFFA8324A), Color(0xFF6B9B47))
    val Sunflower = PlantColors(Color(0xFFF4C542), Color(0xFF8B5A2B), Color(0xFF5C8A4D))
    val Lavender = PlantColors(Color(0xFF9B7BC9), Color(0xFF6B4D8C), Color(0xFF7BA85C))
    val Rose = PlantColors(Color(0xFFD85A7A), Color(0xFF8C3855), Color(0xFF5C8A4D))
    val Daisy = PlantColors(Color(0xFFFAF5E8), Color(0xFFF4C542), Color(0xFF6B9B47))
    val Poppy = PlantColors(Color(0xFFE8553D), Color(0xFF3A2D1F), Color(0xFF7BA85C))
    val Mint = PlantColors(null, null, Color(0xFF88B070))

    private val all = listOf(Tulip, Sunflower, Lavender, Rose, Daisy, Poppy, Mint)
    private val byName = mapOf(
        "tulip" to Tulip, "sunflower" to Sunflower, "lavender" to Lavender, "rose" to Rose,
        "daisy" to Daisy, "poppy" to Poppy, "mint" to Mint, "herb" to Mint,
    )

    fun forName(name: String?): PlantColors? = name?.let { byName[it.lowercase()] }

    /** Map an item slug (e.g. "seed.sunset_tulip") to its plant colours. */
    fun forSlug(slug: String?): PlantColors {
        val s = slug?.lowercase() ?: return Tulip
        return when {
            "tulip" in s -> Tulip
            "sunflower" in s -> Sunflower
            "lavender" in s -> Lavender
            "rose" in s -> Rose
            "daisy" in s -> Daisy
            "poppy" in s -> Poppy
            "mint" in s || "herb" in s -> Mint
            else -> Tulip
        }
    }

    /** Deterministic colour pick when only an opaque id (e.g. item_id) is known. */
    fun forSeed(seed: String): PlantColors = all[seed.hashCode().absoluteValue % all.size]
}

/** Map a DB `growth_stage` enum value to a sprite stage (1 sprout · 2 sapling · 3 mature). */
fun growthStageToSprite(stage: String): Int = when (stage.lowercase()) {
    "seedling" -> 1
    "sapling" -> 2
    else -> 3
}

@Composable
fun Plant(colors: PlantColors, stage: Int, size: Dp) {
    Plant(colors = colors, stage = stage, modifier = Modifier.size(size))
}

/**
 * @param idle when true the sprite gently sways/breathes forever (for the living
 *   garden grid). [phaseMillis] desynchronises neighbours so they don't move in
 *   lockstep — pass a per-cell offset.
 */
@Composable
fun Plant(
    colors: PlantColors,
    stage: Int,
    modifier: Modifier,
    idle: Boolean = false,
    phaseMillis: Int = 0,
) {
    val swaying = if (idle) modifier.plantSway(phaseMillis) else modifier
    Canvas(modifier = swaying) {
        when (stage) {
            1 -> drawSprout(colors)
            2 -> drawSapling(colors)
            else -> drawMaturePlant(colors)
        }
    }
}

/** Rooted-at-the-base sway + subtle breathing; cheap (transforms a static layer). */
private fun Modifier.plantSway(phaseMillis: Int): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "plantSway")
    val wave by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseMillis % 2600),
        ),
        label = "plantWave",
    )
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 0.92f)
        rotationZ = wave * 3.5f
        val s = 1f + 0.03f * ((wave + 1f) / 2f)
        scaleX = s; scaleY = s
    }
}

/** Mature (stage 3) plant, drawn to fill a [size] × [size] box. */
@Composable
fun MaturePlant(colors: PlantColors, size: Dp) {
    Canvas(modifier = Modifier.size(size)) { drawMaturePlant(colors) }
}

private fun DrawScope.drawSprout(colors: PlantColors) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val lf = s * 0.22f
    drawLine(
        color = colors.leaf,
        start = Offset(c.x, c.y + lf * 0.5f),
        end = Offset(c.x, c.y - lf * 0.2f),
        strokeWidth = s * 0.045f,
        cap = StrokeCap.Round,
    )
    rotate(-22f, pivot = c) {
        drawOval(colors.leaf, topLeft = Offset(c.x - lf, c.y - lf * 0.35f), size = Size(lf, lf * 0.7f))
    }
    rotate(22f, pivot = c) {
        drawOval(colors.leaf, topLeft = Offset(c.x, c.y - lf * 0.35f), size = Size(lf, lf * 0.7f))
    }
}

private fun DrawScope.drawSapling(colors: PlantColors) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val lf = s * 0.34f
    for (deg in listOf(0f, 72f, 144f, 216f, 288f)) {
        rotate(deg, pivot = c) {
            val w = lf * 0.7f
            val h = lf
            drawOval(colors.leaf, topLeft = Offset(c.x - w / 2f, c.y - h * 0.9f), size = Size(w, h))
        }
    }
    drawCircle(colors.leaf, radius = lf * 0.22f, center = c)
}

private fun DrawScope.drawMaturePlant(colors: PlantColors) {
    val s = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    val lf = s * 0.38f

    // Leaves fanning out from the base.
    for (deg in listOf(0f, 60f, 120f, 180f, 240f, 300f)) {
        rotate(deg, pivot = center) {
            val w = lf * 0.75f
            val h = lf * 1.1f
            drawOval(
                color = colors.leaf,
                topLeft = Offset(center.x - w / 2f, center.y - h * 0.85f),
                size = Size(w, h),
            )
        }
    }

    val bloom = colors.bloom
    if (bloom != null) {
        val bloomSize = s * 0.6f
        val petal = bloomSize * 0.52f
        for (deg in listOf(0f, 72f, 144f, 216f, 288f)) {
            rotate(deg, pivot = center) {
                drawCircle(
                    color = bloom,
                    radius = petal / 2f,
                    center = Offset(center.x, center.y - bloomSize * 0.22f),
                )
            }
        }
        colors.center?.let {
            drawCircle(color = it, radius = bloomSize * 0.42f / 2f, center = center)
        }
    } else {
        drawCircle(color = colors.leaf, radius = s * 0.31f, center = center)
    }
}

// ── Potted plant (onboarding hero) ───────────────────────────────
@Composable
fun PottedPlant(size: Dp, colors: PlantColors = PlantPalette.Tulip) {
    Box(modifier = Modifier.size(width = size, height = size * 1.2f)) {
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            MaturePlant(colors = colors, size = size * 0.95f)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(size * 0.85f)
                .height(size * 0.42f)
                .clip(
                    RoundedCornerShape(
                        topStart = 6.dp, topEnd = 6.dp,
                        bottomStart = 14.dp, bottomEnd = 14.dp,
                    )
                )
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFC97B5C), Color(0xFFA85F44)))
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(size * 0.38f))
                .width(size * 0.92f)
                .height(size * 0.1f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFD88A6D), Color(0xFFB36B4E)))
                ),
        )
    }
}
