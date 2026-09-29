package com.gratitudegarden.app.ui.sprites

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

// ── Backdrop scenes ──────────────────────────────────────────────
// Painted like the plant sprites: no drawables, just shapes scaled to the canvas, so one
// scene fills the Garden's band and the Shop's preview alike. Wide and short is the shape
// they're drawn for; the bottom edge fades to the soil, so the plot below continues it.

enum class Backdrop { CottageMeadow, MistyForest, CherryGrove, QuietShore, DesertSunset;

    companion object {
        /** Map a backdrop slug (e.g. "backdrop.misty_forest") to its scene. The starter is the fallback. */
        fun forSlug(slug: String?): Backdrop {
            val s = slug?.lowercase() ?: return CottageMeadow
            return when {
                "misty" in s || "forest" in s -> MistyForest
                "cherry" in s -> CherryGrove
                "shore" in s -> QuietShore
                "desert" in s || "sunset" in s -> DesertSunset
                else -> CottageMeadow
            }
        }
    }
}

/** Matches the top of the Garden's soil (SoilTop in GardenScreen). */
private val Soil = Color(0xFFA48560)

/** Decorative: no semantics, since the scene is the same idea as a wallpaper. */
@Composable
fun BackdropScene(slug: String?, modifier: Modifier) {
    Canvas(modifier = modifier) { drawBackdrop(slug) }
}

/** The scene for [slug], filling the draw area. Also painted off screen for the home-screen widget. */
internal fun DrawScope.drawBackdrop(slug: String?) {
    when (Backdrop.forSlug(slug)) {
        Backdrop.CottageMeadow -> drawCottageMeadow()
        Backdrop.MistyForest -> drawMistyForest()
        Backdrop.CherryGrove -> drawCherryGrove()
        Backdrop.QuietShore -> drawQuietShore()
        Backdrop.DesertSunset -> drawDesertSunset()
    }
    // Fade the last strip into the soil so the band and the plot read as one card.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Soil.copy(alpha = 0f), Soil),
            startY = size.height * 0.84f,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height * 0.84f),
        size = Size(size.width, size.height * 0.16f),
    )
}

private fun DrawScope.sky(top: Color, bottom: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
}

/**
 * A rolling ridge through [heights] (fractions of the canvas height, evenly spaced across
 * the width), filled down to the bottom edge. Smooth S-curves between the points.
 */
private fun DrawScope.ridge(color: Color, heights: List<Float>) {
    val w = size.width
    val h = size.height
    val step = w / (heights.size - 1)
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, heights[0] * h)
        for (i in 1 until heights.size) {
            val x0 = (i - 1) * step
            val x1 = i * step
            val xm = (x0 + x1) / 2f
            cubicTo(xm, heights[i - 1] * h, xm, heights[i] * h, x1, heights[i] * h)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.cloud(cx: Float, cy: Float, r: Float, color: Color = Color.White.copy(alpha = 0.9f)) {
    drawCircle(color, r, Offset(cx, cy))
    drawCircle(color, r * 0.75f, Offset(cx - r * 1.1f, cy + r * 0.25f))
    drawCircle(color, r * 0.8f, Offset(cx + r * 1.1f, cy + r * 0.2f))
}

/** A pine: two stacked triangles on a short trunk, standing on [baseY]. */
private fun DrawScope.pine(x: Float, baseY: Float, height: Float, color: Color) {
    val half = height * 0.28f
    drawRect(color, Offset(x - height * 0.03f, baseY - height * 0.12f), Size(height * 0.06f, height * 0.12f))
    for ((top, bottom, width) in listOf(Triple(0.0f, 0.62f, 0.75f), Triple(0.3f, 0.9f, 1f))) {
        val path = Path().apply {
            moveTo(x, baseY - height * (1f - top))
            lineTo(x - half * width, baseY - height * (1f - bottom))
            lineTo(x + half * width, baseY - height * (1f - bottom))
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawCottageMeadow() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFBFE3F0), Color(0xFFEAF6E4))
    drawCircle(Color(0xFFFBE08A), h * 0.14f, Offset(w * 0.84f, h * 0.26f))
    cloud(w * 0.2f, h * 0.24f, h * 0.08f)
    cloud(w * 0.55f, h * 0.16f, h * 0.06f)
    ridge(Color(0xFF9CC58A), listOf(0.55f, 0.42f, 0.5f, 0.38f, 0.48f, 0.44f))

    // The cottage, sitting into the near hill.
    val cx = w * 0.66f
    val base = h * 0.7f
    val bw = h * 0.26f
    val bh = h * 0.2f
    drawRect(Color(0xFFF3E6CC), Offset(cx - bw / 2f, base - bh), Size(bw, bh))
    drawPath(
        Path().apply {
            moveTo(cx - bw * 0.62f, base - bh)
            lineTo(cx, base - bh - h * 0.15f)
            lineTo(cx + bw * 0.62f, base - bh)
            close()
        },
        Color(0xFFB5563E),
    )
    drawRect(Color(0xFF7A4E2D), Offset(cx - bw * 0.1f, base - bh * 0.55f), Size(bw * 0.2f, bh * 0.55f))
    drawRect(Color(0xFF9CC4D6), Offset(cx + bw * 0.2f, base - bh * 0.75f), Size(bw * 0.16f, bh * 0.28f))

    ridge(Color(0xFF7BAE62), listOf(0.74f, 0.66f, 0.72f, 0.7f, 0.64f, 0.72f))
}

private fun DrawScope.drawMistyForest() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFC9D6D3), Color(0xFFE8EEE8))
    // Far pines, faded by distance.
    for (i in 0..13) {
        val x = w * (i + 0.3f * (i % 3)) / 13f
        pine(x, h * 0.72f, h * (0.46f + 0.08f * ((i * 7) % 3)), Color(0xFF93AB9F))
    }
    // The mist sits between the two rows.
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f))),
        topLeft = Offset(0f, h * 0.4f),
        size = Size(w, h * 0.34f),
    )
    ridge(Color(0xFF5E7F4F), listOf(0.8f, 0.76f, 0.8f, 0.78f, 0.8f))
    for (i in 0..7) {
        val x = w * (i + 0.5f) / 8f + (if (i % 2 == 0) -w * 0.02f else w * 0.02f)
        pine(x, h * 0.82f, h * (0.5f + 0.1f * ((i * 5) % 3)), Color(0xFF4A6E51))
    }
}

private fun DrawScope.drawCherryGrove() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFFBE0E8), Color(0xFFFFF4E6))
    cloud(w * 0.78f, h * 0.2f, h * 0.07f)
    ridge(Color(0xFFBBD49B), listOf(0.6f, 0.5f, 0.56f, 0.48f, 0.58f))
    ridge(Color(0xFFA2C487), listOf(0.8f, 0.74f, 0.78f, 0.76f, 0.74f, 0.8f))

    val trunk = Color(0xFF7A5238)
    val pinks = listOf(Color(0xFFF2A7BE), Color(0xFFE889A6), Color(0xFFF7C4D3))
    for ((fx, scale) in listOf(0.14f to 1f, 0.38f to 0.8f, 0.63f to 1.1f, 0.88f to 0.85f)) {
        val x = w * fx
        val base = h * 0.8f
        val r = h * 0.13f * scale
        drawRect(trunk, Offset(x - r * 0.12f, base - r * 1.9f), Size(r * 0.24f, r * 1.9f))
        drawCircle(pinks[1], r, Offset(x, base - r * 2.2f))
        drawCircle(pinks[0], r * 0.85f, Offset(x - r * 0.75f, base - r * 1.85f))
        drawCircle(pinks[0], r * 0.85f, Offset(x + r * 0.75f, base - r * 1.9f))
        drawCircle(pinks[2], r * 0.55f, Offset(x + r * 0.2f, base - r * 2.6f))
    }
    // A few falling petals.
    for ((fx, fy) in listOf(0.25f to 0.3f, 0.5f to 0.55f, 0.74f to 0.35f, 0.94f to 0.6f, 0.06f to 0.62f)) {
        drawCircle(pinks[2], h * 0.018f, Offset(w * fx, h * fy))
    }
}

private fun DrawScope.drawQuietShore() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFF5CF9C), Color(0xFFFBEBD0))
    // A low sun, half set behind the sea.
    drawCircle(Color(0xFFF6B04E), h * 0.2f, Offset(w * 0.5f, h * 0.56f))
    cloud(w * 0.18f, h * 0.22f, h * 0.06f, Color.White.copy(alpha = 0.7f))
    drawRect(Color(0xFF6FA5C0), Offset(0f, h * 0.55f), Size(w, h * 0.25f))
    // The sun's reflection and a few wave lines.
    val shine = Color(0xFFF9D08A)
    for ((y, half) in listOf(0.6f to 0.14f, 0.65f to 0.1f, 0.7f to 0.06f)) {
        drawRect(shine, Offset(w * (0.5f - half * 0.6f), h * y), Size(w * half * 1.2f, h * 0.018f))
    }
    val foam = Color(0xFF9CC4D6)
    for ((fx, fy) in listOf(0.12f to 0.62f, 0.28f to 0.7f, 0.74f to 0.64f, 0.88f to 0.72f)) {
        drawRect(foam, Offset(w * fx, h * fy), Size(w * 0.08f, h * 0.016f))
    }
    ridge(Color(0xFFE9D3A3), listOf(0.8f, 0.77f, 0.79f, 0.76f, 0.78f))
}

private fun DrawScope.drawDesertSunset() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFE9795A), Color(0xFFF9CB7A))
    drawCircle(Color(0xFFFFE08A), h * 0.2f, Offset(w * 0.3f, h * 0.5f))
    ridge(Color(0xFFD98C5C), listOf(0.62f, 0.52f, 0.6f, 0.5f, 0.58f))
    ridge(Color(0xFFC4703F), listOf(0.82f, 0.72f, 0.78f, 0.7f, 0.8f, 0.74f))

    // One saguaro on the near dune.
    val cactus = Color(0xFF5E7A45)
    val x = w * 0.78f
    val base = h * 0.78f
    val t = h * 0.05f
    drawRect(cactus, Offset(x - t / 2f, base - h * 0.4f), Size(t, h * 0.4f))
    drawRect(cactus, Offset(x - t * 2f, base - h * 0.26f), Size(t * 1.5f, t * 0.8f))
    drawRect(cactus, Offset(x - t * 2f, base - h * 0.36f), Size(t * 0.8f, h * 0.11f))
    drawRect(cactus, Offset(x + t / 2f, base - h * 0.2f), Size(t * 1.3f, t * 0.8f))
    drawRect(cactus, Offset(x + t * 1.1f, base - h * 0.3f), Size(t * 0.8f, h * 0.11f))
}
