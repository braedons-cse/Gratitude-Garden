package com.gratitudegarden.app.model

/**
 * How the writer felt, one of five in order. An entry stores only [score] (the column
 * `mood`, 1–5, null for none), so the order is what insights can average and trend.
 */
enum class Mood(val score: Int, val label: String) {
    ROUGH(1, "rough"),
    LOW(2, "low"),
    OKAY(3, "okay"),
    GOOD(4, "good"),
    GREAT(5, "great");

    companion object {
        /** The mood a stored score means; null for none, or for a score outside 1–5. */
        fun of(score: Int?): Mood? = entries.firstOrNull { it.score == score }
    }
}
