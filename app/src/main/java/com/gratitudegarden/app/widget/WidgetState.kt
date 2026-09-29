package com.gratitudegarden.app.widget

import com.gratitudegarden.app.data.Garden
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.StreakStatus
import com.gratitudegarden.app.data.UserStatsRow
import com.gratitudegarden.app.data.streakOn
import com.gratitudegarden.app.ui.sprites.growthStageToSprite
import java.time.LocalDate

/**
 * What the home-screen widget shows. Plain data, no bitmap: two equal states draw the same
 * picture, so an unchanged state never repaints or re-sends the widget.
 */
sealed interface WidgetState {
    data object SignedOut : WidgetState

    data class Ready(
        val streakDays: Int,
        /** Missed days that freezes cover: the snowflake instead of the flame, as on the Garden. */
        val streakHeld: Boolean,
        val thoughtsLeft: Int,
        val backdropSlug: String?,
        val gridRows: Int,
        val gridCols: Int,
        val plants: List<WidgetPlant>,
    ) : WidgetState
}

/** A plant at ([x], [y]) in the grid; [stage] is the sprite stage (1 sprout · 2 sapling · 3 mature). */
data class WidgetPlant(val x: Int, val y: Int, val slug: String?, val stage: Int)

/**
 * The widget's state from the same rows the Garden reads. [streakToday] is the day the
 * streak is judged on (the Garden's `streakNow`); [usedToday] counts deleted entries too,
 * like the cap. [slugs] maps item ids to slugs, for the plants' colours and the backdrop.
 */
fun widgetStateFrom(
    stats: UserStatsRow?,
    streakToday: LocalDate,
    garden: Garden?,
    plants: List<GardenPlantRow>,
    slugs: Map<String, String>,
    usedToday: Int,
    dailyCap: Int,
): WidgetState.Ready {
    val streak = stats?.streakOn(streakToday) ?: StreakStatus()
    return WidgetState.Ready(
        streakDays = streak.days,
        streakHeld = streak.heldByFreeze,
        thoughtsLeft = (dailyCap - usedToday).coerceAtLeast(0),
        backdropSlug = garden?.activeBackdropItemId?.let(slugs::get),
        gridRows = garden?.gridRows ?: DEFAULT_ROWS,
        gridCols = garden?.gridCols ?: DEFAULT_COLS,
        // Sorted so the same garden is the same state, whatever order Room returned it in.
        plants = plants
            .map { WidgetPlant(it.gridX, it.gridY, slugs[it.itemId], growthStageToSprite(it.growthStage)) }
            .sortedWith(compareBy({ it.y }, { it.x })),
    )
}

private const val DEFAULT_ROWS = 6
private const val DEFAULT_COLS = 5
