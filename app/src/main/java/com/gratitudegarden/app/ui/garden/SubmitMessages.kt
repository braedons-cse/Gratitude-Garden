package com.gratitudegarden.app.ui.garden

import com.gratitudegarden.app.data.SubmitResult
import com.gratitudegarden.app.ui.toUserMessage

// What writing a thought says afterwards, wherever it was written: the Garden's toast and the
// reminder's inline reply share these, so the two never drift apart.

/** Server errors raised on purpose by the garden RPCs; null means "use the fallback". */
internal fun gardenErrorMessage(m: String): String? = when {
    "occupied" in m -> "That spot's already taken — try another."
    "out of bounds" in m -> "That's outside the garden."
    "do not own" in m -> "You don't own that seed yet."
    "daily entry cap" in m -> "That's all your thoughts for today. Come back tomorrow."
    "insufficient coins" in m -> "Not enough coins yet — plant more kind thoughts."
    "entry text required" in m -> "Write a few words first."
    else -> null
}

/**
 * [freezing]: the streak was being held by freezes, so this entry (the day's first) spends
 * them. The server decides the reward, so this reports what was actually awarded rather than
 * promising a fixed number.
 */
internal fun submitMessage(result: SubmitResult, freezing: Boolean): String = when (result) {
    is SubmitResult.Planted -> {
        val earned = listOfNotNull(result.coins?.let { "+$it coins" }, result.xp?.let { "+$it XP" })
        val note = if (earned.isEmpty()) "" else earned.joinToString(" · ", postfix = " · ")
        if (freezing) "${note}a streak freeze kept your streak going ❄️"
        else "${note}a kind thought planted 🌱"
    }
    SubmitResult.Saved -> "Saved in your journal. It'll be planted as soon as it syncs."
    is SubmitResult.Refused -> gardenErrorMessage(result.reason) ?: "The garden couldn't take that thought."
}

/** A submit that threw (the cap, most often). */
internal fun Throwable.toSubmitMessage(): String =
    toUserMessage("Couldn't save your thought", ::gardenErrorMessage)
