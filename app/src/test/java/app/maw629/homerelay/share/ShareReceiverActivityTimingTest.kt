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
