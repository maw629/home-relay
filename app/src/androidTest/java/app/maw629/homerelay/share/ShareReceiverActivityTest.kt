package app.maw629.homerelay.share

import android.app.Activity
import android.app.Application
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.maw629.homerelay.BuildConfig
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.MainActivity
import app.maw629.homerelay.data.UploadState
import java.io.File
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearQueueAndResetBlockingSource() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<HomeRelayApplication>()
        application.container.database.clearAllTables()
        resetBlockingSource()
    }

    @After
    fun releaseBlockingSource() {
        releaseBlockingSourceControl()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleFileShareQueuesItemAndShowsConfirmation() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun realShareStaysVisibleForTheConfiguredTerminalDurationThenFinishes() {
        assumeTerminalTimingDurationIsTestable()
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
            val terminalDisplayStartUptime = awaitTerminalDisplayStartUptime(scenario)
            recordTestTiming("terminal_display_start=$terminalDisplayStartUptime")

            assertVisibleBeforeConfiguredTerminalDeadline(scenario)
            assertFinishesByConfiguredTerminalDeadline(scenario, terminalDisplayStartUptime)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backDoesNotFinishReceiverWhileBlockingSourceIsPreparing() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened before Back is dispatched",
                awaitBlockingSourceStarted(BLOCKING_SOURCE_TIMEOUT_MILLIS)
            )

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            assertTrue(
                "Back must not finish a receiver whose source has not reached a durable outcome",
                scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED
            )
            releaseBlockingSourceControl()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun systemBackDoesNotFinishReceiverWhileBlockingSourceIsPreparing() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened before system Back is dispatched",
                awaitBlockingSourceStarted(BLOCKING_SOURCE_TIMEOUT_MILLIS)
            )

            assertTrue(
                "The Android global Back route must be available",
                InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
                )
            )
            waitForBackInterception(scenario)

            assertTrue(
                "System Back must not finish a receiver whose source is still preparing",
                scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED
            )
            assertEquals(
                "System Back must keep a preparing receiver foreground and interactive",
                androidx.lifecycle.Lifecycle.State.RESUMED,
                scenario.state
            )
            releaseBlockingSourceControl()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun blockingShareReservesPrivateTargetInDurableStagingRowBeforeReceiverCanFinish() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened after durable staging begins",
                awaitBlockingSourceStarted(BLOCKING_SOURCE_TIMEOUT_MILLIS)
            )

            val row = runBlocking {
                ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                    .container
                    .database
                    .uploadDao()
                    .observeAll()
                    .first()
                    .single()
            }

            assertEquals(UploadState.STAGING, row.state)
            assertEquals(
                File(
                    ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir,
                    "pending"
                ).canonicalFile,
                File(row.stagedPath).parentFile?.canonicalFile
            )
            assertNotEquals(
                "A receiver with only a reserved target must remain active until staging reaches an outcome",
                androidx.lifecycle.Lifecycle.State.DESTROYED,
                scenario.state
            )
            releaseBlockingSourceControl()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun completedShareKeepsItsPrivateTargetAndQueueRowBeforeReceiverCanFinish() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )

            val row = runBlocking {
                ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                    .container
                    .database
                    .uploadDao()
                    .observeAll()
                    .first()
                    .single()
            }

            assertEquals(UploadState.QUEUED, row.state)
            assertTrue(
                "Finishing before the staged target is durable would lose the sender's URI grant",
                File(row.stagedPath).isFile
            )
            assertNotEquals(
                "The terminal overlay must remain active while reporting the durable queued row",
                androidx.lifecycle.Lifecycle.State.DESTROYED,
                scenario.state
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shareReceiverDoesNotLaunchMainActivity() {
        val launchedActivities = mutableListOf<Class<out Activity>>()
        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {
                launchedActivities += activity::class.java
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        ApplicationProvider.getApplicationContext<HomeRelayApplication>()
            .registerActivityLifecycleCallbacks(lifecycleCallbacks)
        try {
            ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
                composeRule.waitUntilAtLeastOneExists(
                    hasText("Queued 1 file for Home Relay"),
                    5_000
                )
            }
        } finally {
            ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                .unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
        assertTrue(
            "Sharing must never create MainActivity while showing the receiver overlay",
            launchedActivities.none { it == MainActivity::class.java }
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recreationOfBlockingShareCreatesExactlyOneDurableQueueRow() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened before receiver recreation",
                awaitBlockingSourceStarted(BLOCKING_SOURCE_TIMEOUT_MILLIS)
            )

            scenario.recreate()
            releaseBlockingSourceControl()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )

            val rows = runBlocking {
                ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                    .container
                    .database
                    .uploadDao()
                    .observeAll()
                    .first()
            }
            assertEquals(
                "Recreating the receiver must not create a second row for the same shared URI",
                1,
                rows.size
            )
            assertTrue(
                "The one row must finish staging as queued or durable attention",
                rows.single().state == UploadState.QUEUED ||
                    rows.single().state == UploadState.NEEDS_ATTENTION
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun configuredTerminalDeadlineSurvivesReceiverRecreation() {
        assumeTerminalRecreationDurationIsTestable()
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
            val terminalDisplayStartUptime = awaitTerminalDisplayStartUptime(scenario)
            recordTestTiming("terminal_display_start=$terminalDisplayStartUptime")
            lateinit var originalActivity: ShareReceiverActivity
            scenario.onActivity { activity ->
                originalActivity = activity
            }

            SystemClock.sleep(preRecreationWaitMillis())
            recordTestTiming("recreate_requested")
            assertNotEquals(
                "The receiver must remain active long enough to recreate during its terminal display",
                androidx.lifecycle.Lifecycle.State.DESTROYED,
                scenario.state
            )
            scenario.recreate()
            recordTestTiming("recreate_finished")
            scenario.onActivity { recreatedActivity ->
                assertNotSame(
                    "The receiver recreation assertion requires a newly created activity",
                    originalActivity,
                    recreatedActivity
                )
            }

            assertFinishesByConfiguredTerminalDeadline(scenario, terminalDisplayStartUptime)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backFinishesReceiverAfterRealShareReachesTerminalStatus() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            assertTrue(
                "Back must finish a receiver after its real source reaches a durable terminal outcome",
                awaitDestroyed(scenario, 500)
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun statusCardIsCenteredInSafeDrawingCoordinates() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(hasTestTag("share-status-card"), 5_000)
            val cardBounds = composeRule.onNodeWithTag("share-status-card")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            lateinit var safeDrawingInsets: androidx.core.graphics.Insets
            scenario.onActivity { activity ->
                safeDrawingInsets = requireNotNull(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                ).getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
            }
            val safeDrawingCenterX = rootBounds.left +
                (safeDrawingInsets.left + rootBounds.width - safeDrawingInsets.right) / 2f
            val safeDrawingCenterY = rootBounds.top +
                (safeDrawingInsets.top + rootBounds.height - safeDrawingInsets.bottom) / 2f

            assertEquals(
                "Status card center must use the safe-drawing rectangle in Compose root coordinates",
                safeDrawingCenterX,
                cardBounds.center.x,
                1f
            )
            assertEquals(
                "Status card center must use the safe-drawing rectangle in Compose root coordinates",
                safeDrawingCenterY,
                cardBounds.center.y,
                1f
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun transparentReceiverLeavesHostBackgroundVisibleOutsideCardAndSystemBars() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ShareOverlayHostActivity::class.java).use { hostScenario ->
            instrumentation.waitForIdleSync()
            var hostStatusBarInset = 0
            var hostNavigationBarInset = 0
            hostScenario.onActivity { host ->
                assertTrue("The overlay host must render before its baseline is captured", host.window.decorView.isLaidOut)
                val insets = requireNotNull(
                    ViewCompat.getRootWindowInsets(host.window.decorView)
                )
                hostStatusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                hostNavigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            val hostBaseline = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertTrue("The host must render a status bar", hostStatusBarInset > 0)
            assertTrue("The host must render a navigation bar", hostNavigationBarInset > 0)
            val statusBarBackgroundPixel = findStableBaselinePixel(
                screenshot = hostBaseline,
                left = 0,
                top = 0,
                right = hostBaseline.width,
                bottom = hostStatusBarInset,
                regionName = "status bar"
            )
            val navigationBarBackgroundPixel = findStableBaselinePixel(
                screenshot = hostBaseline,
                left = 0,
                top = hostBaseline.height - hostNavigationBarInset,
                right = hostBaseline.width,
                bottom = hostBaseline.height,
                regionName = "navigation bar"
            )
            hostScenario.onActivity { host ->
                host.startActivity(sampleSendIntent())
            }

            composeRule.waitUntilAtLeastOneExists(hasTestTag("share-status-card"), 5_000)
            val cardBounds = composeRule.onNodeWithTag("share-status-card")
                .fetchSemanticsNode()
                .boundsInRoot
            val composeRootLocation = IntArray(2)
            var statusBarInset = 0
            var navigationBarInset = 0
            lateinit var receiver: ShareReceiverActivity
            instrumentation.runOnMainSync {
                receiver = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ShareReceiverActivity>()
                    .single()
                receiver.findViewById<ViewGroup>(android.R.id.content)
                    .getChildAt(0)
                    .getLocationOnScreen(composeRootLocation)
                val insets = requireNotNull(
                    ViewCompat.getRootWindowInsets(receiver.window.decorView)
                )
                statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertEquals(
                "The host baseline and receiver overlay must use the same display dimensions",
                hostBaseline.width,
                screenshot.width
            )
            assertEquals(
                "The host baseline and receiver overlay must use the same display dimensions",
                hostBaseline.height,
                screenshot.height
            )
            val cardLeftOnScreen = cardBounds.left + composeRootLocation[0]
            val cardRightOnScreen = cardBounds.right + composeRootLocation[0]
            val cardCenterYOnScreen = cardBounds.center.y + composeRootLocation[1]
            val outsideCardX = if (cardLeftOnScreen >= 16f) {
                (cardLeftOnScreen / 2f).toInt()
            } else {
                ((cardRightOnScreen + screenshot.width) / 2f).toInt()
            }
            val outsideCardY = cardCenterYOnScreen.toInt()
            assertEquals(
                "An opaque receiver window must not cover the source app outside the status card",
                hostBaseline.getPixel(outsideCardX, outsideCardY),
                screenshot.getPixel(outsideCardX, outsideCardY)
            )

            assertTrue("Receiver must render a status bar", statusBarInset > 0)
            assertTrue("Receiver must render a navigation bar", navigationBarInset > 0)
            assertEquals(
                "An opaque status-bar contrast scrim must not cover the source app",
                hostBaseline.getPixel(statusBarBackgroundPixel.x, statusBarBackgroundPixel.y),
                screenshot.getPixel(statusBarBackgroundPixel.x, statusBarBackgroundPixel.y)
            )
            assertEquals(
                "An opaque navigation-bar contrast scrim must not cover the source app",
                hostBaseline.getPixel(navigationBarBackgroundPixel.x, navigationBarBackgroundPixel.y),
                screenshot.getPixel(navigationBarBackgroundPixel.x, navigationBarBackgroundPixel.y)
            )
            assertEquals("Receiver status bar must be transparent", Color.TRANSPARENT, receiver.window.statusBarColor)
            assertEquals(
                "Receiver navigation bar must be transparent",
                Color.TRANSPARENT,
                receiver.window.navigationBarColor
            )
        }
    }

    private fun awaitDestroyed(
        scenario: ActivityScenario<ShareReceiverActivity>,
        timeoutMillis: Long
    ): Boolean {
        val startedAtMillis = SystemClock.uptimeMillis()
        while (true) {
            if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return true
            val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
            if (elapsedMillis < 0L || elapsedMillis >= timeoutMillis) {
                return scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED
            }
            SystemClock.sleep(minOf(TERMINAL_POLL_INTERVAL_MILLIS, timeoutMillis - elapsedMillis))
        }
    }

    private fun assertVisibleBeforeConfiguredTerminalDeadline(
        scenario: ActivityScenario<ShareReceiverActivity>
    ) {
        assertTrue(
            "The receiver must be visible when its terminal status is observed",
            scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED
        )
    }

    private fun assertFinishesByConfiguredTerminalDeadline(
        scenario: ActivityScenario<ShareReceiverActivity>,
        terminalDisplayStartUptime: Long
    ) {
        val latestAllowedElapsedMillis = configuredTerminalDurationMillis
            .coerceAtMost(Long.MAX_VALUE - TERMINAL_DEADLINE_TOLERANCE_MILLIS) +
            TERMINAL_DEADLINE_TOLERANCE_MILLIS
        val remainingWaitMillis = terminalDisplayRemainingMillis(
            terminalAtElapsedMillis = terminalDisplayStartUptime,
            displayDurationMillis = latestAllowedElapsedMillis,
            nowElapsedMillis = SystemClock.uptimeMillis()
        )
        recordTestTiming("finish_wait=$remainingWaitMillis")
        assertTrue(
            "The receiver must begin finishing within its configured ${configuredTerminalDurationMillis} ms terminal duration plus $TERMINAL_DEADLINE_TOLERANCE_MILLIS ms scheduling and polling tolerance",
            awaitFinishingOrDestroyed(scenario, remainingWaitMillis)
        )
        val elapsedMillis = SystemClock.uptimeMillis() - terminalDisplayStartUptime
        assertTrue(
            "The receiver must begin finishing within its configured ${configuredTerminalDurationMillis} ms terminal duration plus $TERMINAL_DEADLINE_TOLERANCE_MILLIS ms scheduling and polling tolerance",
            elapsedMillis >= 0L && elapsedMillis <= latestAllowedElapsedMillis
        )
    }

    private fun awaitTerminalDisplayStartUptime(
        scenario: ActivityScenario<ShareReceiverActivity>
    ): Long {
        val startedAtMillis = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - startedAtMillis < ACTIVITY_STATE_TIMEOUT_MILLIS) {
            var displayStartUptime: Long? = null
            scenario.onActivity { activity ->
                displayStartUptime = activity.terminalDisplayStartUptime
            }
            displayStartUptime?.let { return it }
            SystemClock.sleep(TERMINAL_POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("The terminal status card never recorded its display start")
    }

    private fun recordTestTiming(event: String) {
        android.util.Log.d(
            "HomeRelayShareReceiverTest",
            "uptime=${SystemClock.uptimeMillis()} event=$event"
        )
    }

    private fun waitForBackInterception(scenario: ActivityScenario<ShareReceiverActivity>) {
        val startedAtMillis = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - startedAtMillis < ACTIVITY_STATE_TIMEOUT_MILLIS) {
            var interceptedBackCount = 0
            scenario.onActivity { activity ->
                interceptedBackCount = activity.interceptedBackCount
            }
            if (interceptedBackCount > 0) return
            SystemClock.sleep(TERMINAL_POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("System Back was not intercepted while share staging was preparing")
    }

    private fun awaitFinishingOrDestroyed(
        scenario: ActivityScenario<ShareReceiverActivity>,
        timeoutMillis: Long
    ): Boolean {
        val startedAtMillis = SystemClock.uptimeMillis()
        while (true) {
            if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return true
            var isFinishing = false
            scenario.onActivity { activity -> isFinishing = activity.isFinishing }
            if (isFinishing) return true
            val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
            if (elapsedMillis < 0L || elapsedMillis >= timeoutMillis) return false
            SystemClock.sleep(minOf(TERMINAL_POLL_INTERVAL_MILLIS, timeoutMillis - elapsedMillis))
        }
    }

    private fun preRecreationWaitMillis(): Long =
        configuredTerminalDurationMillis - configuredTerminalDurationMillis / 4L

    private fun assumeTerminalTimingDurationIsTestable() {
        assumeTrue(
            "Skipping terminal timing behavior because configured display duration " +
                "${configuredTerminalDurationMillis} ms exceeds the " +
                "$MAXIMUM_TESTABLE_TERMINAL_DURATION_MILLIS ms testable maximum.",
            configuredTerminalDurationMillis <= MAXIMUM_TESTABLE_TERMINAL_DURATION_MILLIS
        )
    }

    private fun assumeTerminalRecreationDurationIsTestable() {
        assumeTrue(
            "Skipping terminal recreation behavior because configured display duration " +
                "${configuredTerminalDurationMillis} ms exceeds the " +
                "$MAXIMUM_TESTABLE_TERMINAL_DURATION_MILLIS ms testable maximum.",
            configuredTerminalDurationMillis <= MAXIMUM_TESTABLE_TERMINAL_DURATION_MILLIS
        )
        assumeTrue(
            "Skipping terminal recreation assertion because configured display duration " +
                "${configuredTerminalDurationMillis} ms is shorter than the " +
                "$MINIMUM_RECREATION_TEST_DURATION_MILLIS ms required for reliable recreation.",
            configuredTerminalDurationMillis >= MINIMUM_RECREATION_TEST_DURATION_MILLIS
        )
    }

    private fun resetBlockingSource() {
        assertTrue(
            "The test provider must accept a reset control call",
            requireNotNull(callProviderControl(SampleContentProvider.METHOD_RESET))
                .getBoolean(SampleContentProvider.RESULT_CONTROL_APPLIED)
        )
    }

    private fun awaitBlockingSourceStarted(timeoutMillis: Long): Boolean =
        requireNotNull(
            callProviderControl(
                SampleContentProvider.METHOD_AWAIT_STARTED,
                Bundle().apply {
                    putLong(SampleContentProvider.EXTRA_TIMEOUT_MILLIS, timeoutMillis)
                }
            )
        ).getBoolean(SampleContentProvider.RESULT_STARTED)

    private fun releaseBlockingSourceControl() {
        assertTrue(
            "The test provider must accept a release control call",
            requireNotNull(callProviderControl(SampleContentProvider.METHOD_RELEASE))
                .getBoolean(SampleContentProvider.RESULT_CONTROL_APPLIED)
        )
    }

    private fun callProviderControl(method: String, extras: Bundle? = null): Bundle? =
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.call(
            TEST_PROVIDER_CONTROL_URI,
            method,
            null,
            extras
        )

    private fun findStableBaselinePixel(
        screenshot: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        regionName: String
    ): ScreenPixel {
        val colors = HashMap<Int, Int>()
        for (y in top until bottom) {
            for (x in left until right) {
                val color = screenshot.getPixel(x, y)
                colors[color] = colors.getOrDefault(color, 0) + 1
            }
        }
        val stableColor = colors.maxByOrNull { it.value }?.key
            ?: throw AssertionError(
                "No pixels were found in the $regionName baseline region [$left,$top,$right,$bottom]"
            )
        for (y in top until bottom) {
            for (x in left until right) {
                if (screenshot.getPixel(x, y) == stableColor) return ScreenPixel(x, y)
            }
        }
        throw AssertionError(
            "No stable pixel was found in the $regionName baseline region [$left,$top,$right,$bottom]"
        )
    }

    private data class ScreenPixel(val x: Int, val y: Int)

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

    private fun blockingSendIntent() = Intent(Intent.ACTION_SEND)
        .setClass(
            ApplicationProvider.getApplicationContext(),
            ShareReceiverActivity::class.java
        )
        .setType("application/pdf")
        .putExtra(
            Intent.EXTRA_STREAM,
            Uri.parse("content://app.maw629.homerelay.share-test/blocking-report.pdf")
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private companion object {
        private const val BLOCKING_SOURCE_TIMEOUT_MILLIS = 5_000L
        private const val ACTIVITY_STATE_TIMEOUT_MILLIS = 5_000L
        private const val MAXIMUM_TESTABLE_TERMINAL_DURATION_MILLIS = 5_000L
        private const val MINIMUM_RECREATION_TEST_DURATION_MILLIS = 2_000L
        private const val TERMINAL_POLL_INTERVAL_MILLIS = 10L
        private const val TERMINAL_SCHEDULER_TOLERANCE_MILLIS = 500L
        private const val TERMINAL_DEADLINE_TOLERANCE_MILLIS =
            TERMINAL_SCHEDULER_TOLERANCE_MILLIS + TERMINAL_POLL_INTERVAL_MILLIS
        private val TEST_PROVIDER_CONTROL_URI =
            Uri.parse("content://app.maw629.homerelay.share-test/control")
        private val configuredTerminalDurationMillis = BuildConfig.SHARE_STATUS_DISPLAY_MILLIS

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
