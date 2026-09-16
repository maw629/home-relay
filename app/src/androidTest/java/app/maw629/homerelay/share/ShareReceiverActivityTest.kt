package app.maw629.homerelay.share

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.maw629.homerelay.HomeRelayApplication
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @Test
    fun singleFileShareFinishesAfterQueueing() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            val deadline = System.currentTimeMillis() + 5_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            assertEquals("Share receiver should finish after queueing", Lifecycle.State.DESTROYED, scenario.state)
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
    }
}
