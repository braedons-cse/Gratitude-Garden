package com.gratitudegarden.app.data

/** XP for the day's first entry; later ones pay [XP_EXTRA_ENTRY]. Set by the server. */
const val XP_FIRST_ENTRY = 10

/** XP for each entry after the day's first. */
const val XP_EXTRA_ENTRY = 2

/**
 * The XP at which [level] starts: 10 * (level² - 1), so 0, 30, 80, 150, 240, 350 ...
 * Levels below 1 don't exist and start at 0.
 */
fun xpForLevel(level: Int): Int = startOf(level).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

// Long, so the step past the highest level an Int of XP can reach doesn't overflow.
private fun startOf(level: Int): Long = if (level <= 1) 0 else 10L * (level.toLong() * level - 1)

/**
 * The level [xp] puts someone at. Mirrors `level_for_xp()` in migration 20260925120000,
 * which a trigger on `profiles` applies to every XP write: change both or neither.
 * Integer steps rather than `sqrt`, so a threshold is never missed by a rounding error.
 */
fun levelForXp(xp: Int): Int {
    var level = 1
    while (xp >= startOf(level + 1)) level++
    return level
}

/** How far through [level] someone with [xp] is, for the progress bar. */
data class LevelProgress(val level: Int, val xpIntoLevel: Int, val xpForNext: Int) {
    val fraction: Float get() = if (xpForNext <= 0) 0f else xpIntoLevel.toFloat() / xpForNext
}

/**
 * Progress from [xp] alone. The level comes from the curve too, rather than the stored
 * `profiles.level`, so the bar and the number can't disagree.
 */
fun levelProgress(xp: Int): LevelProgress {
    val level = levelForXp(xp)
    val start = xpForLevel(level)
    return LevelProgress(level, xp - start, xpForLevel(level + 1) - start)
}
