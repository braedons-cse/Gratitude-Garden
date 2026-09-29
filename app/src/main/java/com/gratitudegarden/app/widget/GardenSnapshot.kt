package com.gratitudegarden.app.widget

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.sprites.drawBackdrop
import com.gratitudegarden.app.ui.sprites.drawPlant
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The garden card, painted off screen for the widget: the equipped backdrop as a band over
 * the plot, and every plant in its cell, drawn by the same functions as the Garden screen.
 *
 * Laid out in "card dp", the Garden card's own measurements on a typical phone, scaled to
 * the bitmap. The bitmap is capped at [MAX_PIXELS]: it travels to the launcher in a Binder
 * transaction, and the widget only ever shows it a few centimetres wide.
 */
object GardenSnapshot {
    const val MAX_PIXELS = 150_000

    // The Garden card on a 411dp phone, in dp. Mirrors GardenScreen's GardenGrid.
    private const val CARD_WIDTH = 379f
    private const val BAND = 64f
    private const val PADDING = 10f
    private const val GAP = 4f
    private const val CELL_CORNER = 6f
    private const val PLANT_PADDING = 4f
    private const val CARD_CORNER = 16f

    // Mirror SoilTop / SoilBottom in GardenScreen.
    private val SoilTop = Color(0xFFA48560)
    private val SoilBottom = Color(0xFF8B6F47)

    /** Height over width of the card for a [rows] × [cols] plot. */
    fun aspect(rows: Int, cols: Int): Float {
        val inner = CARD_WIDTH - 2 * PADDING
        return (BAND + 2 * PADDING + inner * rows / cols) / CARD_WIDTH
    }

    /** Paint [state] at most [maxWidthPx] wide, and within [MAX_PIXELS] regardless. */
    fun render(state: WidgetState.Ready, maxWidthPx: Int = Int.MAX_VALUE): Bitmap {
        val rows = state.gridRows.coerceAtLeast(1)
        val cols = state.gridCols.coerceAtLeast(1)
        val aspect = aspect(rows, cols)
        val budgetWidth = sqrt(MAX_PIXELS / aspect).toInt()
        val w = maxWidthPx.coerceIn(1, budgetWidth)
        val h = (w * aspect).roundToInt().coerceAtLeast(1)

        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            val s = w / CARD_WIDTH
            val band = BAND * s
            val card = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(CARD_CORNER * s)))
            }
            clipPath(card) {
                clipRect(bottom = band) {
                    inset(0f, 0f, 0f, size.height - band) { drawBackdrop(state.backdropSlug) }
                }
                drawRect(
                    Brush.verticalGradient(listOf(SoilTop, SoilBottom), startY = band, endY = size.height),
                    topLeft = Offset(0f, band),
                    size = Size(size.width, size.height - band),
                )

                val gap = GAP * s
                val left0 = PADDING * s
                val top0 = band + PADDING * s
                val cellW = (size.width - 2 * PADDING * s - (cols - 1) * gap) / cols
                val cellH = (size.height - top0 - PADDING * s - (rows - 1) * gap) / rows
                val plantAt = state.plants.associateBy { it.y to it.x }
                for (r in 0 until rows) for (c in 0 until cols) {
                    val left = left0 + c * (cellW + gap)
                    val top = top0 + r * (cellH + gap)
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(SoilTop, SoilBottom),
                            start = Offset(left, top),
                            end = Offset(left + cellW, top + cellH),
                        ),
                        topLeft = Offset(left, top),
                        size = Size(cellW, cellH),
                        cornerRadius = CornerRadius(CELL_CORNER * s),
                    )
                    val plant = plantAt[r to c] ?: continue
                    val pad = PLANT_PADDING * s
                    inset(left + pad, top + pad, size.width - (left + cellW - pad), size.height - (top + cellH - pad)) {
                        drawPlant(PlantPalette.forSlug(plant.slug), plant.stage)
                    }
                }
            }
        }
        return image.asAndroidBitmap()
    }
}
