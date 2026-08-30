package app.maw629.homerelay.share

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.maw629.homerelay.HomeRelayApplication
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun singleFileShareQueuesItemAndShowsConfirmation() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
            composeRule.onNodeWithText("Queued 1 file for Home Relay").assertExists()
        }
    }

    @Test
    fun singleFileShareConfirmationHasContrastingSurface() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
            val surface = composeRule.onNodeWithTag("share-queue-surface")
                .captureToImage()
                .asAndroidBitmap()
            val background = surface.getPixel(surface.width / 2, surface.height / 2)
            composeRule.onNodeWithText("Queued 1 file for Home Relay").assertExists()

            assertTrue(
                "Share confirmation must contrast with its full-screen surface",
                surface.pixels().any { contrastRatio(background, it) >= 4.5 }
            )
        }
    }

    private fun sampleSendIntent() = Intent(Intent.ACTION_SEND)
        .setClass(
            ApplicationProvider.getApplicationContext(),
            ShareReceiverActivity::class.java
        )
        .setType("application/pdf")
        .putExtra(
            Intent.EXTRA_STREAM,
            Uri.parse("content://app.maw629.homerelay.share-test/report.pdf")
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private companion object {
        @JvmStatic
        @BeforeClass
        fun configureDestinationBeforeActivityLaunch() = runBlocking {
            ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                .container
                .destinationStore
                .setDestination("content://app.maw629.homerelay.share-test/tree/destination")
        }

        fun android.graphics.Bitmap.pixels(): Sequence<Int> = sequence {
            for (y in 0 until height) {
                for (x in 0 until width) yield(getPixel(x, y))
            }
        }

        fun contrastRatio(first: Int, second: Int): Double {
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
}
