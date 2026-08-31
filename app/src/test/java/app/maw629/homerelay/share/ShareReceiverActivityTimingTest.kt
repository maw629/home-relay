package app.maw629.homerelay.share

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTimingTest {
    @Test
    fun chunkedTerminalWaitDelaysUntilTheEntireConfiguredDurationHasElapsed() = runTest {
        var nowMillis = 10L
        val delays = mutableListOf<Long>()

        waitForTerminalDisplay(
            terminalAtElapsedMillis = 10L,
            displayDurationMillis = 2L * 86_400_000L,
            nowMillis = { nowMillis },
            delayMillis = { delayMillis ->
                delays += delayMillis
                nowMillis += delayMillis
            }
        )

        assertEquals(listOf(86_400_000L, 86_400_000L), delays)
    }

    @Test
    fun chunkedTerminalWaitDoesNotScheduleAnotherDelayAfterAnOverdueResume() = runTest {
        var nowMillis = 10L
        val delays = mutableListOf<Long>()

        waitForTerminalDisplay(
            terminalAtElapsedMillis = 10L,
            displayDurationMillis = 2L * 86_400_000L,
            nowMillis = { nowMillis },
            delayMillis = { delayMillis ->
                delays += delayMillis
                nowMillis += 2L * 86_400_000L + 1L
            }
        )

        assertEquals(listOf(86_400_000L), delays)
    }

    @Test
    fun chunkedTerminalWaitUsesActiveUptimeWhenElapsedTimeAdvancesDuringSleep() = runTest {
        var uptimeMillis = 10L
        var elapsedRealtimeMillis = 10L
        val delays = mutableListOf<Long>()

        waitForTerminalDisplay(
            terminalAtElapsedMillis = 10L,
            displayDurationMillis = 100L,
            nowMillis = { uptimeMillis },
            delayMillis = { delayMillis ->
                delays += delayMillis
                elapsedRealtimeMillis += 10_000L
                uptimeMillis += delayMillis
            }
        )

        assertEquals(
            "Elapsed time may advance during sleep while the uptime-based timer waits only for active time",
            listOf(100L),
            delays
        )
        assertEquals(10_010L, elapsedRealtimeMillis)
    }

    @Test
    fun zeroDurationFinishesWithoutSchedulingADelay() = runTest {
        var delayCalls = 0

        waitForTerminalDisplay(
            terminalAtElapsedMillis = 10L,
            displayDurationMillis = 0L,
            nowMillis = { 10L },
            delayMillis = { delayCalls++ }
        )

        assertEquals(0, delayCalls)
    }

    @Test
    fun delayChunkLimitsAnExtremeFiniteRemainingDuration() {
        assertEquals(
            86_400_000L,
            terminalDisplayDelayChunkMillis(Long.MAX_VALUE)
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
