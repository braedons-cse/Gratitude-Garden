package com.gratitudegarden.app.model

import java.time.LocalDate

/**
 * A question for the blank page (roadmap 1.4). One per day, the same everywhere it shows:
 * the new-entry sheet and the reminder. Never stored with the entry and never rewarded;
 * it's only there to get the first few words out.
 */
object Prompts {

    val ALL: List<String> = listOf(
        "Who helped you today, even in a small way?",
        "What made you smile today?",
        "What's something you're looking forward to?",
        "What's a comfort you'd miss if it were gone?",
        "Who's someone you haven't thanked yet?",
        "What went better than you expected?",
        "What's something beautiful you noticed?",
        "What did your body let you do today?",
        "What's a small thing that made today easier?",
        "Who made you laugh recently?",
        "What did you learn today?",
        "What's a place you're glad exists?",
        "What's something you have now that you once wished for?",
        "What's a kindness you saw, given or received?",
        "What did you eat today that you enjoyed?",
        "What's a skill you're grateful to have?",
        "Who's always there when you need them?",
        "What's a sound, smell or taste you loved today?",
        "What challenge taught you something?",
        "What part of your home do you love most?",
        "What's a memory that still makes you happy?",
        "What's something in nature you're grateful for?",
        "Who's a teacher, in any sense, you're thankful for?",
        "What's a simple pleasure you'd hate to lose?",
        "What did someone do for you that they didn't have to?",
        "What's something about today you'd like to remember?",
        "What's a book, song or film that stayed with you?",
        "What about yourself are you grateful for?",
        "What's a moment of calm you had today?",
        "What's a problem you no longer have?",
    )

    /**
     * The question for [day]. [offset] steps through the others ("another question"), and
     * wraps, so any offset is fine.
     */
    fun forDay(day: LocalDate, offset: Int = 0): String =
        ALL[Math.floorMod(day.toEpochDay() + offset, ALL.size.toLong()).toInt()]
}
