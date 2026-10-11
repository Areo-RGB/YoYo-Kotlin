package com.example.yoyo

import com.example.yoyo.domain.ProtocolDefinition
import com.example.yoyo.domain.ShuttlePhase
import com.example.yoyo.domain.TestType
import com.example.yoyo.domain.deriveRuntime
import com.example.yoyo.domain.formatTime
import com.example.yoyo.domain.protocolFor
import com.example.yoyo.domain.startLevelOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProtocolTest {
    /**
     * Beep onsets detected in the bundled recordings (audio position, ms).
     * The runtime must place each beep within [TOLERANCE] of these, otherwise
     * the on-screen phase drifts away from what the athletes hear.
     */
    private val measuredBeeps = mapOf(
        TestType.YoYoIr1 to mapOf(
            "14.1 start" to 231785,
            "18.1 start" to 854685,
            "23.8 finish" to 1715580,
        ),
        TestType.YoYoIr2 to mapOf(
            "20.1 start" to 208800,
            "23.1 finish" to 648620,
            "26.8 finish" to 1184745,
        ),
    )

    private fun beepMs(protocol: ProtocolDefinition, label: String): Int {
        val (level, edge) = label.split(" ")
        val index = protocol.shuttles.indexOfFirst { it.levelDisplay == level }
        val testMs = if (edge == "start") protocol.shuttleStartMs[index] else protocol.runEndMs[index]
        return testMs + protocol.audio.leadMs
    }

    @Test
    fun runtimeTimelineTracksTheRecordedYoYoBeeps() {
        measuredBeeps.forEach { (type, beeps) ->
            val protocol = protocolFor(type)
            beeps.forEach { (label, audioMs) ->
                val error = abs(beepMs(protocol, label) - audioMs)
                assertTrue("${type.label} $label off by $error ms", error < TOLERANCE)
            }
        }
    }

    @Test
    fun runtimeIsMonotonicAndCompletesAtTheEndOfTheTimeline() {
        for (type in TestType.entries) {
            val protocol = protocolFor(type)
            var previousIndex = 0
            var previousDistance = 0
            var elapsed = 0
            while (elapsed <= protocol.durationMs) {
                val runtime = deriveRuntime(protocol, elapsed)
                assertTrue(runtime.shuttleIndex >= previousIndex)
                assertTrue(runtime.currentDistanceMeters >= previousDistance)
                assertTrue(runtime.phaseProgress in 0.0..1.0)
                previousIndex = runtime.shuttleIndex
                previousDistance = runtime.currentDistanceMeters
                elapsed += 250
            }
            val end = deriveRuntime(protocol, protocol.durationMs)
            assertTrue(end.completed)
            assertEquals(protocol.maxDistanceMeters, end.currentDistanceMeters)
        }
    }

    @Test
    fun phaseBoundariesFollowTheRecordedBeepsNotTheNominalTable() {
        val protocol = protocolFor(TestType.YoYoIr1)
        val finish = protocol.runEndMs.last()
        assertEquals(ShuttlePhase.Running, deriveRuntime(protocol, finish - 1).phase)
        assertEquals(ShuttlePhase.Recovery, deriveRuntime(protocol, finish).phase)
    }

    @Test
    fun protocolDefinitionsMatchTheBundledSchedules() {
        assertEquals(91, protocolFor(TestType.YoYoIr1).shuttles.size)
        assertEquals(3640, protocolFor(TestType.YoYoIr1).maxDistanceMeters)
        assertEquals(2680, protocolFor(TestType.YoYoIr2).maxDistanceMeters)
        assertEquals(247, protocolFor(TestType.BeepTest).shuttles.size)
    }

    @Test
    fun deriveRuntimeHandlesNegativeCountdownCleanly() {
        val snapshot = deriveRuntime(protocolFor(TestType.BeepTest), -5000)
        assertFalse(snapshot.completed)
        assertEquals(0, snapshot.currentDistanceMeters)
        assertEquals(5.0, snapshot.phaseRemainingSeconds, 0.0)
        assertEquals(0.0, snapshot.progress, 0.0)
        assertEquals(0, snapshot.shuttleIndex)
    }

    @Test
    fun beepTestVo2MaxUsesTheLastCompletedShuttleSpeed() {
        val protocol = protocolFor(TestType.BeepTest)
        val runtime = deriveRuntime(protocol, protocol.shuttleStartMs[7])
        // The eighth shuttle is attempted at 9.0 km/h, but only 140 m at 8.0
        // km/h has been completed when the athlete is stopped here.
        assertEquals(140, runtime.currentDistanceMeters)
        assertEquals(9.0, runtime.shuttle.speedKmh, 0.0)
        assertEquals(27.4, protocol.vo2Max(runtime.currentDistanceMeters), 0.001)
    }

    @Test
    fun yoYoVo2MaxFormulas() {
        assertEquals(0.0, protocolFor(TestType.YoYoIr1).vo2Max(0), 0.0)
        assertEquals(46.5, protocolFor(TestType.YoYoIr1).vo2Max(1200), 0.001)
        assertEquals(58.9, protocolFor(TestType.YoYoIr2).vo2Max(1000), 0.001)
    }

    @Test
    fun startLevelsAreTheDistinctIr1SpeedLevels() {
        val options = startLevelOptions()
        assertEquals(listOf(5, 9, 11, 12, 13) + (14..23), options.map { it.speedLevel })
        assertEquals(0, options.first().startElapsedMs)
        assertEquals("Level 14 · 14.5 km/h", options[5].label)
        val protocol = protocolFor(TestType.YoYoIr1)
        assertEquals(protocol.shuttleStartMs[options[5].shuttleNumber - 1], options[5].startElapsedMs)
    }

    @Test
    fun formatsClockLikeTheFlutterApp() {
        assertEquals("00:00", formatTime(0))
        assertEquals("01:05", formatTime(65_999))
        assertEquals("-00:10", formatTime(-10_215))
    }

    private companion object {
        const val TOLERANCE = 40
    }
}
