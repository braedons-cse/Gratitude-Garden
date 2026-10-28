package com.gratitudegarden.app

import com.gratitudegarden.app.model.MilestoneStep
import com.gratitudegarden.app.model.Milestones
import com.gratitudegarden.app.model.milestoneStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [milestoneStep] — when the streak earns its 7 / 30 / 100-day moment. The input is the
 * streak on the Garden's chip (0 once a run is broken) and the milestone last celebrated on
 * this device (null before the device has seen the streak).
 */
class MilestonesTest {

    @Test
    fun firstSightingOnlyRecordsWhatIsAlreadyReached() {
        assertEquals(MilestoneStep.Record(0), milestoneStep(0, null))
        assertEquals(MilestoneStep.Record(0), milestoneStep(6, null))
        assertEquals(MilestoneStep.Record(7), milestoneStep(7, null))
        assertEquals(MilestoneStep.Record(30), milestoneStep(40, null))
    }

    @Test
    fun reachingAMilestoneCelebratesIt() {
        assertEquals(MilestoneStep.None, milestoneStep(6, 0))
        assertEquals(MilestoneStep.Celebrate(7), milestoneStep(7, 0))
        assertEquals(MilestoneStep.Celebrate(30), milestoneStep(30, 7))
        assertEquals(MilestoneStep.Celebrate(100), milestoneStep(100, 30))
    }

    // Offline entries delivered together can step over the exact day.
    @Test
    fun jumpingPastAMilestoneStillCelebratesIt() {
        assertEquals(MilestoneStep.Celebrate(7), milestoneStep(8, 0))
        assertEquals(MilestoneStep.Celebrate(30), milestoneStep(31, 7))
    }

    // Two milestones at once shows only the higher one.
    @Test
    fun jumpingPastTwoCelebratesTheHigher() {
        assertEquals(MilestoneStep.Celebrate(30), milestoneStep(30, 0))
    }

    @Test
    fun aCelebratedMilestoneIsNotShownAgain() {
        assertEquals(MilestoneStep.None, milestoneStep(7, 7))
        assertEquals(MilestoneStep.None, milestoneStep(29, 7))
        assertEquals(MilestoneStep.None, milestoneStep(250, 100))
    }

    // A broken run reads 0 (or 1 once a new one starts); the next 7 is a new week worth celebrating.
    @Test
    fun aBrokenRunResetsSoTheNextWeekCountsAgain() {
        assertEquals(MilestoneStep.Record(0), milestoneStep(0, 7))
        assertEquals(MilestoneStep.Record(0), milestoneStep(1, 7))
        assertEquals(MilestoneStep.Celebrate(7), milestoneStep(7, 0))
        assertEquals(MilestoneStep.Record(7), milestoneStep(12, 30))
    }

    @Test
    fun reachedByIsTheHighestMilestoneAtOrBelow() {
        assertEquals(0, Milestones.reachedBy(0))
        assertEquals(7, Milestones.reachedBy(29))
        assertEquals(100, Milestones.reachedBy(365))
    }
}
