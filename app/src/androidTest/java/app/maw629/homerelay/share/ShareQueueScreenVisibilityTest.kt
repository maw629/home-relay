package app.maw629.homerelay.share

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShareQueueScreenVisibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opaqueOrLowContrastCardMakesConfirmationUnreadableOverSourceApp() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.White)
            ) {
                ShareQueueScreen(
                    status = ShareQueueStatus.Queued(1),
                    darkTheme = true,
                    dynamicColor = false
                )
            }
        }

        val confirmation = composeRule.onNodeWithTag("share-status-card")
            .assertExists()
            .captureToImage()
            .asAndroidBitmap()
        val cardSurface = confirmation.getPixel(confirmation.width / 2, confirmation.height / 4)

        assertTrue(
            "An opaque or low-contrast card makes the confirmation unreadable over the source app",
            confirmation.pixels().any { contrastRatio(cardSurface, it) >= 4.5 }
        )
    }

    private fun android.graphics.Bitmap.pixels(): Sequence<Int> = sequence {
        for (y in 0 until height) {
            for (x in 0 until width) yield(getPixel(x, y))
        }
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        fun luminance(color: Int) = 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color))

        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }
}
