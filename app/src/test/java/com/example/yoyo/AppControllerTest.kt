package com.example.yoyo

import com.example.yoyo.audio.AudioEngine
import com.example.yoyo.domain.Athlete
import com.example.yoyo.domain.AthleteStatus
import com.example.yoyo.domain.LeaderboardMetric
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.TestType
import com.example.yoyo.domain.deriveRuntime
import com.example.yoyo.domain.protocolFor
import com.example.yoyo.domain.startLevelOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records calls and lets tests choose the reported playback position. */
private class FakeAudioEngine : AudioEngine {
    val calls = mutableListOf<String>()
    var position: Int? = null
    var playing = false
    override val isPlaying: Boolean get() = playing
    override suspend fun load(type: TestType) {
        calls += "load:${type.key}"
    }
    override suspend fun ensureLoaded(type: TestType) {
        calls += "ensure:${type.key}"
    }
    override suspend fun seekTo(positionMs: Int) {
        calls += "seek:$positionMs"
    }
    override suspend fun start() {
        calls += "start"
        playing = true
    }
    override suspend fun pause() {
        calls += "pause"
        playing = false
    }
    override suspend fun stop() {
        calls += "stop"
        playing = false
    }
    override suspend fun currentPositionMs(): Int? = position
    override suspend fun setVolume(volume: Float) {
        calls += "volume:$volume"
    }
    override fun release() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    private var nowMs = 1_000L
    private val audio = FakeAudioEngine()

    private fun TestScope.controller(store: StateStore = InMemoryStateStore()) = AppController(
        scope = backgroundScope,
        audio = audio,
        store = store,
        monotonicMs = { nowMs },
        wallClockMs = { 1_700_000_000_000L },
        encodeDispatcher = StandardTestDispatcher(testScheduler),
    )

    /** Moves both the fake monotonic clock and the coroutine scheduler forward. */
    private fun TestScope.advance(ms: Long) {
        nowMs += ms
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun deletingHistoryUpdatesTheDerivedLeaderboard() = runTest {
        val controller = controller()
        controller.sessions.addAll(
            listOf(
                session("one", listOf(result("A", 300), result("B", 250))),
                session("two", listOf(result("A", 100))),
            ),
        )
        assertEquals("B", controller.leaderboard.first().playerName)
        controller.deleteSession("two")
        assertEquals("A", controller.leaderboard.first().playerName)
        assertEquals(300.0, controller.leaderboard.first().averageDistanceMeters, 0.0)
    }

    @Test
    fun switchingLeaderboardMetricChangesRanksWithoutModifyingHistory() = runTest {
        val controller = controller()
        controller.sessions.addAll(
            listOf(
                session("recent", listOf(result("Alex", 400), result("Sam", 310))),
                session("older", listOf(result("Sam", 290))),
            ),
        )
        assertEquals("Alex", controller.leaderboard.first().playerName)
        controller.toggleLeaderboardMetric()
        assertEquals(LeaderboardMetric.TotalDistance, controller.leaderboardMetric)
        assertEquals("Sam", controller.leaderboard.first().playerName)
        assertEquals(600, controller.leaderboard.first().totalDistanceMeters)
        controller.toggleLeaderboardMetric()
        assertEquals(LeaderboardMetric.AverageDistance, controller.leaderboardMetric)
        assertEquals("Alex", controller.leaderboard.first().playerName)
        assertEquals(2, controller.sessions.size)
    }

    @Test
    fun setTestTypeAllowsSwitchingWhenCompletedOrIdle() = runTest {
        val controller = controller()
        controller.testState = TestState.Completed
        controller.setTestType(TestType.YoYoIr2)
        assertEquals(TestType.YoYoIr2, controller.selectedTestType)
        assertEquals(TestState.Idle, controller.testState)
        controller.setTestType(TestType.BeepTest)
        assertEquals(TestType.BeepTest, controller.selectedTestType)
        assertEquals(TestState.Idle, controller.testState)
    }

    @Test
    fun beepTestEliminatesOnSameShuttleSecondMiss() = runTest {
        val controller = controller()
        controller.athletes.add(Athlete(id = "a1", name = "Test Runner"))
        controller.selectedTestType = TestType.BeepTest
        controller.testState = TestState.Running

        controller.markMiss("a1")
        assertEquals(AthleteStatus.Warned, controller.athletes.first().status)
        assertEquals(controller.runtime.shuttle.shuttleNumber, controller.athletes.first().lastMissShuttle)

        controller.markMiss("a1")
        assertEquals(AthleteStatus.Eliminated, controller.athletes.first().status)
        assertNotNull(controller.athletes.first().finalDistanceMeters)
    }

    @Test
    fun beepTestFinishScoresTheLastCompletedShuttle() = runTest {
        val protocol = protocolFor(TestType.BeepTest)
        val controller = controller()
        controller.athletes.add(Athlete(id = "a1", name = "Test Runner"))
        controller.selectedTestType = TestType.BeepTest
        controller.testState = TestState.Paused
        controller.elapsedMs = protocol.shuttleStartMs[7]
        controller.stopAndFinishTest()

        val athlete = controller.athletes.single()
        assertEquals(140, athlete.finalDistanceMeters)
        assertEquals(27.4, athlete.vo2Max!!, 0.001)
        assertEquals(TestState.Completed, controller.testState)
        assertEquals(AppTab.Results, controller.activeTab)
    }

    @Test
    fun beepTestIgnoresMissAndOutDuringTheCountdown() = runTest {
        val controller = controller()
        controller.athletes.add(Athlete(id = "a1", name = "Test Runner"))
        controller.selectedTestType = TestType.BeepTest
        controller.testState = TestState.Running
        controller.runtime = deriveRuntime(protocolFor(TestType.BeepTest), -1000)

        controller.markMiss("a1")
        controller.eliminate("a1")

        assertEquals(AthleteStatus.Running, controller.athletes.single().status)
        assertNull(controller.athletes.single().finalDistanceMeters)
    }

    @Test
    fun initializeRestoresDefaultRosterAndWarmsAudio() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        assertTrue(controller.initialized)
        assertEquals(16, controller.athletes.size)
        assertEquals(TestType.entries.toSet(), controller.cachedAudio)
        // Warmups run in order and finish on the selected protocol.
        assertEquals("load:yoyoIr1", audio.calls.last { it.startsWith("load") })
    }

    @Test
    fun startTestRunsTheClockAndKeepsTheScreenOnUntilFinished() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()

        controller.startTest()
        runCurrent()
        assertEquals(TestState.Running, controller.testState)
        assertEquals(AppTab.Live, controller.activeTab)
        assertTrue(controller.keepScreenOn)
        assertTrue(controller.canRecord)
        assertTrue(audio.calls.containsAll(listOf("ensure:yoyoIr1", "seek:0", "start")))

        advance(5_000)
        assertEquals(5_000, controller.elapsedMs)
        assertEquals(5_000, controller.runtime.elapsedMs)

        controller.pauseTest()
        runCurrent()
        assertFalse(controller.keepScreenOn)
        assertFalse(controller.canRecord)
        assertEquals("pause", audio.calls.last())
        advance(10_000)
        assertEquals(5_000, controller.elapsedMs)

        controller.resumeTest()
        runCurrent()
        assertTrue(controller.keepScreenOn)
        assertEquals(listOf("seek:5000", "start"), audio.calls.takeLast(2))
        advance(1_000)
        assertEquals(6_000, controller.elapsedMs)

        controller.stopAndFinishTest()
        runCurrent()
        assertFalse(controller.keepScreenOn)
        assertEquals(TestState.Completed, controller.testState)
        assertEquals("stop", audio.calls.last())
    }

    @Test
    fun ir1StartsFromTheChosenLevelAndSeeksTheRecording() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        val level14 = startLevelOptions().first { it.speedLevel == 14 }
        controller.setStartLevel(14)
        controller.startTest()
        runCurrent()
        assertEquals(level14.startElapsedMs, controller.runtime.elapsedMs)
        assertEquals("14.1", controller.runtime.shuttle.levelDisplay)
        assertTrue("seek:${level14.startElapsedMs}" in audio.calls)
    }

    @Test
    fun beepTestStartsWithTheRecordedCountdown() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.setTestType(TestType.BeepTest)
        controller.startTest()
        runCurrent()
        assertEquals(-10_215, controller.runtime.elapsedMs)
        assertFalse(controller.canRecord)
        assertTrue("seek:0" in audio.calls)
        advance(10_300)
        assertTrue(controller.canRecord)
    }

    @Test
    fun missWarnsThenSecondMissFinishesAndUndoRestores() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.startTest()
        runCurrent()
        val protocol = protocolFor(TestType.YoYoIr1)
        advance(protocol.shuttleStartMs[3].toLong() + 500)

        val id = controller.selectedAthletes.first().id
        controller.markMiss(id)
        val warned = controller.athletes.first { it.id == id }
        assertEquals(AthleteStatus.Warned, warned.status)
        assertEquals("11.2", warned.warningLevel)
        assertEquals("Undo action for ${warned.name}", controller.undoDescription)

        controller.eliminate(id)
        val out = controller.athletes.first { it.id == id }
        assertEquals(AthleteStatus.Eliminated, out.status)
        assertEquals(protocol.shuttles[2].cumulativeDistanceMeters, out.finalDistanceMeters)
        assertEquals(1, out.rank)
        assertEquals(1, controller.results.size)

        controller.undoLastAction()
        assertEquals(AthleteStatus.Warned, controller.athletes.first { it.id == id }.status)
        controller.undoLastAction()
        assertEquals(AthleteStatus.Running, controller.athletes.first { it.id == id }.status)
        assertNull(controller.undoDescription)
    }

    @Test
    fun ranksShareTiesAndBreakByFinishTime() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.startTest()
        runCurrent()
        val ids = controller.selectedAthletes.map { it.id }
        val protocol = protocolFor(TestType.YoYoIr1)

        advance(protocol.runEndMs[1].toLong() + 100)
        controller.eliminate(ids[0])
        controller.eliminate(ids[1])
        advance(protocol.runEndMs[3].toLong() - protocol.runEndMs[1] + 100)
        controller.eliminate(ids[2])

        val byId = controller.athletes.associateBy { it.id }
        assertEquals(1, byId.getValue(ids[2]).rank)
        assertEquals(2, byId.getValue(ids[0]).rank)
        assertEquals(2, byId.getValue(ids[1]).rank)
    }

    @Test
    fun protocolCompletesNaturallyAtTheEndOfTheRecording() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.setTestType(TestType.YoYoIr2)
        controller.startTest()
        runCurrent()
        advance(protocolFor(TestType.YoYoIr2).durationMs.toLong() + 200)

        assertEquals(TestState.Completed, controller.testState)
        assertFalse(controller.keepScreenOn)
        val max = protocolFor(TestType.YoYoIr2).maxDistanceMeters
        assertTrue(controller.results.all { it.finalDistanceMeters == max && it.rank == 1 })
    }

    @Test
    fun clockFollowsTheAudioPositionWhenItDrifts() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.startTest()
        runCurrent()
        // The recording is 300 ms behind the stopwatch (e.g. output latency).
        audio.position = 700
        advance(1_000)
        // Five ticks trigger a sync; the next tick reads the corrected clock.
        advance(100)
        assertEquals(800, controller.elapsedMs)

        // Small differences are ignored to avoid jitter.
        audio.position = controller.elapsedMs + 500 + 10
        advance(500)
        assertEquals(1_300, controller.elapsedMs)
    }

    @Test
    fun saveSessionPersistsAndRestoresHistoryAndRoster() = runTest {
        val store = InMemoryStateStore()
        val controller = controller(store)
        controller.initialize()
        runCurrent()
        controller.addAthlete("  Zed   Newcomer  ")
        controller.toggleAthlete("athlete_1")
        controller.startTest()
        runCurrent()
        advance(protocolFor(TestType.YoYoIr1).runEndMs[4].toLong() + 100)
        controller.stopAndFinishTest()
        controller.saveSession()
        runCurrent()

        val saved = controller.sessions.single()
        assertEquals(saved.id, controller.sessionSavedId)
        assertEquals(16, saved.results.size)
        assertEquals(16, saved.totalAthletesCount)
        assertTrue(saved.title.startsWith("Yo-Yo IR1 "))
        controller.saveSession()
        assertEquals(1, controller.sessions.size)

        val restored = controller(store)
        restored.initialize()
        runCurrent()
        assertEquals(17, restored.athletes.size)
        assertEquals("Zed   Newcomer", restored.athletes.last().name)
        assertFalse(restored.athletes.first().isSelected)
        assertTrue(restored.athletes.all { it.status == AthleteStatus.Running && it.finalDistanceMeters == null })
        assertEquals(saved, restored.sessions.single())
    }

    @Test
    fun corruptStateFallsBackToTheDefaultRoster() = runTest {
        val controller = controller(InMemoryStateStore("{not json"))
        controller.initialize()
        runCurrent()
        assertEquals(16, controller.athletes.size)
    }

    @Test
    fun rosterIsLockedDuringATest() = runTest {
        val controller = controller()
        controller.initialize()
        runCurrent()
        controller.startTest()
        controller.addAthlete("Late")
        controller.removeAthlete("athlete_1")
        controller.deselectAll()
        assertEquals(16, controller.athletes.size)
        assertEquals(16, controller.selectedAthletes.size)
    }

    @Test
    fun csvQuotesEveryField() = runTest {
        val controller = controller()
        controller.athletes.add(Athlete(id = "a1", name = "Quote \"Q\" Person"))
        controller.testState = TestState.Paused
        controller.elapsedMs = protocolFor(TestType.YoYoIr1).runEndMs[0] + 10
        controller.stopAndFinishTest()
        assertEquals(
            "\"Rank\",\"Athlete\",\"Distance (m)\",\"Level\",\"VO2max\",\"Warning Distance (m)\"\n" +
                "\"1\",\"Quote \"\"Q\"\" Person\",\"40\",\"5.1\",\"36.7\",\"\"",
            controller.makeCsv(),
        )
    }
}
