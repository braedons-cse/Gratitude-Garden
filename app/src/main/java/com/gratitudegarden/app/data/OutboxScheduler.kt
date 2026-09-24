package com.gratitudegarden.app.data

/**
 * Delivers the journal outbox in the background, so a change made offline goes out once
 * there's a connection even if the app isn't running by then. The repository asks after
 * every journal write, and cancels on sign-out along with the data.
 */
interface OutboxScheduler {
    fun schedule()
    fun cancel()

    /** Delivery only while the app runs (tests, and anything that drains by hand). */
    object None : OutboxScheduler {
        override fun schedule() {}
        override fun cancel() {}
    }
}
