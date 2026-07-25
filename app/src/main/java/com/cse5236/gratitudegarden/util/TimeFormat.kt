package com.cse5236.gratitudegarden.util

import java.util.Locale

/**
 * Format a 24-hour [hour]:[minute] as a 12-hour clock string like `"8:00 PM"`.
 *
 * Extracted out of `MeScreen` so this pure logic can be unit-tested on the host JVM
 * without dragging in Compose (Non-UI unit test #3).
 *
 * Optimization / bug fix: the original built the string with the locale-default
 * `"%d:%02d %s".format(...)`, which emits *localized* digits under number systems
 * like Arabic-Indic (e.g. `٨:٠٠ PM`). This is UI chrome, not localized content, so we
 * pin [Locale.US] to always produce ASCII digits. The 12-hour / AM-PM arithmetic is
 * unchanged. See docs/unit-test-optimizations.md.
 */
internal fun formatTime(hour: Int, minute: Int): String {
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
}
