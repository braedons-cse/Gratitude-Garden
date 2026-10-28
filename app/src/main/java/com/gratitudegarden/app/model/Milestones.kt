package com.gratitudegarden.app.model

/**
 * Streak lengths that get their own celebration (roadmap 1.3). Nothing is paid for one; it's
 * the moment, and a card to share.
 */
object Milestones {

    val ALL: List<Int> = listOf(7, 30, 100)

    /** The highest milestone a [streak] has reached, or 0 below the first. */
    fun reachedBy(streak: Int): Int = ALL.lastOrNull { it <= streak } ?: 0
}

/** What to do with a synced streak, given the milestone last celebrated on this device. */
sealed interface MilestoneStep {
    /** Remember [value] as celebrated without showing anything. */
    data class Record(val value: Int) : MilestoneStep

    data class Celebrate(val days: Int) : MilestoneStep

    data object None : MilestoneStep
}

/**
 * [streak] is the one on the Garden's chip: it keeps its number while freezes hold it, and
 * reads 0 once a run is broken, even before the next entry starts a new one. So a run that
 * was already over by the time this device synced it isn't celebrated. [celebrated] is null
 * until this device has seen the user's streak.
 *
 * - First sighting: record what's already reached, so signing in at day 40 isn't a party.
 * - A broken run (the streak fell below what was celebrated): record, so the next one earns
 *   its 7 again.
 * - Past a new milestone: celebrate the highest one reached. Offline entries delivered
 *   together can jump straight over a milestone.
 */
fun milestoneStep(streak: Int, celebrated: Int?): MilestoneStep {
    val reached = Milestones.reachedBy(streak)
    return when {
        celebrated == null || streak < celebrated ->
            if (reached == celebrated) MilestoneStep.None else MilestoneStep.Record(reached)
        reached > celebrated -> MilestoneStep.Celebrate(reached)
        else -> MilestoneStep.None
    }
}
