package com.cse5236.gratitudegarden.ui.sprites

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Stroke icons ─────────────────────────────────────────────────
// Recreated from the design's SVG path data (24×24 viewport, stroked).

private object IconPaths {
    const val MAIL = "M4 6h16v12H4z M4 7l8 6 8-6"
    const val LOCK = "M6 11h12v9H6z M9 11V8a3 3 0 0 1 6 0v3"
    const val EYE = "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
}

private fun strokeIcon(pathData: String, color: Color, strokeWidth: Float = 1.8f): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        stroke = SolidColor(color),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

enum class PgIconName(internal val path: String) {
    Mail(IconPaths.MAIL),
    Lock(IconPaths.LOCK),
    Eye(IconPaths.EYE),
}

@Composable
fun PgIcon(name: PgIconName, color: Color, size: Dp) {
    val vector = remember(name, color) { strokeIcon(name.path, color) }
    Image(imageVector = vector, contentDescription = null, modifier = Modifier.size(size))
}

// ── Plant sprite ─────────────────────────────────────────────────
// Mature bloom: a ring of leaves with a layered flower on top, built from
// circles/ovals to match the design's CSS-primitive plants.

data class PlantColors(val bloom: Color?, val center: Color?, val leaf: Color)

object PlantPalette {
    val Tulip = PlantColors(bloom = Color(0xFFE85D75), center = Color(0xFFA8324A), leaf = Color(0xFF6B9B47))
    val Sunflower = PlantColors(bloom = Color(0xFFF4C542), center = Color(0xFF8B5A2B), leaf = Color(0xFF5C8A4D))
    val Rose = PlantColors(bloom = Color(0xFFD85A7A), center = Color(0xFF8C3855), leaf = Color(0xFF5C8A4D))
}

/** Mature (stage 3) plant, drawn to fill a [size] × [size] box. */
@Composable
fun MaturePlant(colors: PlantColors, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        drawMaturePlant(colors)
    }
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
        // Leafy herb ball.
        drawCircle(color = colors.leaf, radius = s * 0.31f, center = center)
    }
}

// ── Potted plant (onboarding hero) ───────────────────────────────
@Composable
fun PottedPlant(size: Dp, colors: PlantColors = PlantPalette.Tulip) {
    Box(modifier = Modifier.size(width = size, height = size * 1.2f)) {
        // Plant sits on top, slightly overlapping the pot.
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            MaturePlant(colors = colors, size = size * 0.95f)
        }
        // Pot body.
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
        // Pot lip.
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
