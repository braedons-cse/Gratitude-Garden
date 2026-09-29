package com.gratitudegarden.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gratitudegarden.app.widget.GardenSnapshot
import com.gratitudegarden.app.widget.WidgetPlant
import com.gratitudegarden.app.widget.WidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [GardenSnapshot] — the widget's garden travels to the launcher in a Binder transaction,
 * so however big the widget or the garden, the bitmap stays within its pixel budget.
 */
@RunWith(AndroidJUnit4::class)
class GardenSnapshotTest {

    private fun garden(rows: Int, cols: Int) = WidgetState.Ready(
        streakDays = 3,
        streakHeld = false,
        thoughtsLeft = 7,
        backdropSlug = "backdrop.misty_forest",
        gridRows = rows,
        gridCols = cols,
        plants = listOf(WidgetPlant(0, 0, "seed.sunset_tulip", 3), WidgetPlant(1, 2, null, 1)),
    )

    @Test
    fun aHugeWidgetStillGetsABitmapWithinBudget() {
        val bitmap = GardenSnapshot.render(garden(6, 5), maxWidthPx = 4000)
        assertTrue(bitmap.width * bitmap.height <= GardenSnapshot.MAX_PIXELS)
    }

    @Test
    fun aTallExpandedGardenKeepsItsShapeWithinBudget() {
        val bitmap = GardenSnapshot.render(garden(12, 5))
        assertTrue(bitmap.width * bitmap.height <= GardenSnapshot.MAX_PIXELS)
        assertEquals(GardenSnapshot.aspect(12, 5), bitmap.height.toFloat() / bitmap.width, 0.02f)
    }

    @Test
    fun aSmallRequestIsHonoured() {
        assertEquals(120, GardenSnapshot.render(garden(6, 5), maxWidthPx = 120).width)
    }
}
