package app.maw629.homerelay.share

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixturePackagingTest {
    @Test
    fun overlayHostRunsInTheTargetAppProcess() {
        ActivityScenario.launch(ShareOverlayHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "app.maw629.homerelay",
                    activity.applicationContext.packageName
                )
            }
        }
    }

    @Test
    fun providerControlCallsReachTheTestProvider() {
        val contentResolver = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .contentResolver

        val resetResult = requireNotNull(
            contentResolver.call(
                TEST_PROVIDER_CONTROL_URI,
                SampleContentProvider.METHOD_RESET,
                null,
                null
            )
        )
        assertTrue(
            "Reset must return the provider's control response",
            resetResult.getBoolean(SampleContentProvider.RESULT_CONTROL_APPLIED)
        )

        val releaseResult = requireNotNull(
            contentResolver.call(
                TEST_PROVIDER_CONTROL_URI,
                SampleContentProvider.METHOD_RELEASE,
                null,
                null
            )
        )
        assertTrue(
            "Release must return the provider's control response",
            releaseResult.getBoolean(SampleContentProvider.RESULT_CONTROL_APPLIED)
        )
    }

    private companion object {
        private val TEST_PROVIDER_CONTROL_URI =
            Uri.parse("content://app.maw629.homerelay.share-test/control")
    }
}
