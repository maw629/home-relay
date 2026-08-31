package app.maw629.homerelay.share

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTimingTest {
    @Test
    fun nextHandlerDelayUsesTheFirstBoundedChunk() {
        assertEquals(
            86_400_000L,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = 10L,
                displayDurationMillis = 2L * 86_400_000L,
                nowElapsedMillis = 10L
            )
        )
    }

    @Test
    fun nextHandlerDelayUsesTheRemainingDurationAfterAChunk() {
        assertEquals(
            86_400_000L,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = 10L,
                displayDurationMillis = 2L * 86_400_000L,
                nowElapsedMillis = 86_400_010L
            )
        )
    }

    @Test
    fun nextHandlerDelayIsAbsentWhenTheTerminalDeadlineHasPassed() {
        assertEquals(
            null,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = 10L,
                displayDurationMillis = 100L,
                nowElapsedMillis = 111L
            )
        )
    }

    @Test
    fun nextHandlerDelayUsesFullDurationBeforeAnyActiveUptimePasses() {
        assertEquals(
            100L,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = 10L,
                displayDurationMillis = 100L,
                nowElapsedMillis = 10L
            )
        )
    }

    @Test
    fun zeroDurationDoesNotScheduleAHandlerCallback() {
        assertEquals(
            null,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = 10L,
                displayDurationMillis = 0L,
                nowElapsedMillis = 10L
            )
        )
    }

    @Test
    fun nextHandlerDelayDoesNotOverflowForAnExtremeFiniteDuration() {
        assertEquals(
            86_400_000L,
            terminalDisplayNextDelayMillis(
                terminalAtElapsedMillis = Long.MAX_VALUE - 10L,
                displayDurationMillis = Long.MAX_VALUE,
                nowElapsedMillis = Long.MAX_VALUE - 5L
            )
        )
    }

    @Test
    fun remainingDisplayTimeNearLongMaximumDoesNotOverflowOrFinishEarly() {
        assertEquals(
            Long.MAX_VALUE - 5L,
            terminalDisplayRemainingMillis(
                terminalAtElapsedMillis = Long.MAX_VALUE - 10L,
                displayDurationMillis = Long.MAX_VALUE,
                nowElapsedMillis = Long.MAX_VALUE - 5L
            )
        )
    }

    @Test
    fun remainingDisplayTimeExpiresWhenElapsedSubtractionWraps() {
        assertEquals(
            0L,
            terminalDisplayRemainingMillis(
                terminalAtElapsedMillis = Long.MIN_VALUE,
                displayDurationMillis = Long.MAX_VALUE,
                nowElapsedMillis = Long.MAX_VALUE
            )
        )
    }
}
