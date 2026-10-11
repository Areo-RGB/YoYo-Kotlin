package com.example.yoyo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yoyo.AppController
import com.example.yoyo.AppTab
import com.example.yoyo.domain.AthleteStatus
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.TestType
import com.example.yoyo.result
import com.example.yoyo.session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-port-mdpi")
class TrackerUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun launch(configure: (AppController) -> Unit = {}): AppController {
        val controller = AppController(scope = scope, monotonicMs = SystemClock::uptimeMillis)
        configure(controller)
        runBlocking { controller.initialize() }
        rule.setContent { YoYoTheme { TrackerShell(controller) } }
        rule.waitForIdle()
        return controller
    }

    /** Writes the current screen to build/screenshots for visual review. */
    private fun screenshot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = rule.runOnIdle {
            val root = rule.activity.window.decorView
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun phonePortraitRendersEveryTabAndProtocol() {
        val controller = launch()
        rule.onAllNodesWithText("Yo-Yo IR1")[0].assertIsDisplayed()
        rule.onAllNodesWithText("Yo-Yo IR2")[0].assertIsDisplayed()
        rule.onAllNodesWithText("Beep Test")[0].assertIsDisplayed()
        rule.onNodeWithText("Athlete roster").assertIsDisplayed()
        rule.onNodeWithText("16/16 selected").assertIsDisplayed()
        rule.onNodeWithText("Start").assertIsDisplayed()
        screenshot("01-roster")

        rule.onAllNodesWithText("Yo-Yo IR2")[0].performClick()
        rule.waitForIdle()
        assertEquals(TestType.YoYoIr2, controller.selectedTestType)

        for (tab in listOf(AppTab.Live, AppTab.Results, AppTab.History, AppTab.Settings)) {
            rule.runOnIdle { controller.selectTab(tab) }
            rule.waitForIdle()
            screenshot("02-tab-${tab.name.lowercase()}")
        }

        rule.onNodeWithContentDescription("Open navigation").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Navigation").assertIsDisplayed()
        screenshot("03-drawer")
        rule.onNodeWithText("Roster").performClick()
        rule.waitForIdle()
        assertEquals(AppTab.Setup, controller.activeTab)
    }

    @Test
    fun startSwitchesToLiveHudAndTilesRecordMissesThenFinish() {
        val controller = launch()
        rule.onNodeWithText("Start").performClick()
        rule.waitForIdle()

        assertEquals(AppTab.Live, controller.activeTab)
        assertEquals(TestState.Running, controller.testState)
        rule.onNodeWithText("Finish test").assertIsDisplayed()
        rule.onNodeWithText("Pause").assertIsDisplayed()
        rule.onNodeWithText("RUN").assertIsDisplayed()

        val first = controller.selectedAthletes.first()
        rule.onNodeWithText(first.name).performClick()
        rule.waitForIdle()
        assertEquals(AthleteStatus.Warned, controller.athletes.first { it.id == first.id }.status)
        rule.onNodeWithText("Tap again = out").assertIsDisplayed()
        screenshot("04-live-warned")

        rule.onNodeWithText(first.name).performClick()
        rule.waitForIdle()
        val finished = controller.athletes.first { it.id == first.id }
        assertEquals(AthleteStatus.Eliminated, finished.status)
        assertEquals(controller.runtime.currentDistanceMeters, finished.finalDistanceMeters)

        // Finished athletes drop below the active group.
        val lastActive = controller.selectedAthletes.last { it.status != AthleteStatus.Eliminated }
        val finishedAt = rule.onNodeWithText(first.name).fetchSemanticsNode().positionInRoot
        val lastActiveAt = rule.onNodeWithText(lastActive.name).fetchSemanticsNode().positionInRoot
        assertTrue(
            finishedAt.y > lastActiveAt.y || (finishedAt.y == lastActiveAt.y && finishedAt.x > lastActiveAt.x),
        )

        rule.onNodeWithText("Pause").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
        rule.onNodeWithText("Resume").assertIsDisplayed()
        screenshot("05-live-paused")
        rule.onNodeWithText("Resume").performClick()
        rule.waitForIdle()

        // Finish asks for confirmation before ending the test for everyone.
        rule.onNodeWithText("Finish test").performClick()
        rule.waitForIdle()
        screenshot("06-finish-dialog")
        rule.onNodeWithText("Keep running").performClick()
        rule.waitForIdle()
        assertEquals(TestState.Running, controller.testState)

        rule.onNodeWithText("Finish test").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Finish").performClick()
        rule.waitForIdle()
        assertEquals(TestState.Completed, controller.testState)
        assertEquals(AppTab.Results, controller.activeTab)

        rule.onNodeWithText("Save to history").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Saved to history").assertIsDisplayed()
        assertEquals(1, controller.sessions.size)
        screenshot("07-results-saved")
    }

    @Test
    fun resultsTabShowsTheSavedHistoryLeaderboardAndSwitchesMetric() {
        val controller = launch {
            it.sessions.addAll(
                listOf(
                    session("recent", listOf(result("Alex", 400), result("Sam", 310))),
                    session("older", listOf(result("Sam", 290))),
                ),
            )
        }
        rule.runOnIdle { controller.selectTab(AppTab.Results) }
        rule.waitForIdle()
        rule.onNodeWithText("Player leaderboard").assertIsDisplayed()
        rule.onNodeWithText("AVG DISTANCE (M) ↓").assertIsDisplayed()
        rule.onNodeWithText("400.0").assertIsDisplayed()
        assertEquals("Alex", controller.leaderboard.first().playerName)
        screenshot("08-leaderboard")

        rule.onNodeWithContentDescription("Rank by total distance").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("TOTAL (M) ↓").assertIsDisplayed()
        rule.onNodeWithContentDescription("Rank by average distance").assertExists()
        assertEquals("Sam", controller.leaderboard.first().playerName)

        rule.runOnIdle { controller.selectTab(AppTab.History) }
        rule.waitForIdle()
        rule.onNodeWithText("Test recent").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Best distance").assertIsDisplayed()
        screenshot("09-history-expanded")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun wideLayoutUsesTheDesktopRail() {
        launch()
        rule.onNodeWithText("Live Test").assertIsDisplayed()
        rule.onAllNodesWithText("Yo-Yo IR2")[0].assertIsDisplayed()
        screenshot("10-wide-roster")
    }
}
