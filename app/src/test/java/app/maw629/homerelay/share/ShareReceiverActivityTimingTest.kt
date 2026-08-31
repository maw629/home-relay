package app.maw629.homerelay.share

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTimingTest {
    @Test
    fun remainingDisplayTimeNearLongMaximumDoesNotOverflowOrFinishEarly() {
        assertEquals(
            Long.MAX_VALUE - 5L,
            terminalDisplayRemainingMillis(
                terminalAtMillis = Long.MAX_VALUE - 10L,
                displayDurationMillis = Long.MAX_VALUE,
                nowMillis = Long.MAX_VALUE - 5L
            )
        )
    }

    @Test
    fun remainingDisplayTimeExpiresWhenElapsedSubtractionWraps() {
        assertEquals(
            0L,
            terminalDisplayRemainingMillis(
                terminalAtMillis = Long.MIN_VALUE,
                displayDurationMillis = Long.MAX_VALUE,
                nowMillis = Long.MAX_VALUE
            )
        )
    }
}
