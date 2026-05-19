package com.trybsportowy.sync

/** Result of one sync attempt (CLAUDE.md §10.3). */
sealed class SyncOutcome {
    data object NothingToPush : SyncOutcome()
    data class Pushed(val synced: Int, val failed: Int) : SyncOutcome()
    data class RetryableFailure(val reason: String) : SyncOutcome()
    data object AuthFailed : SyncOutcome()
}
