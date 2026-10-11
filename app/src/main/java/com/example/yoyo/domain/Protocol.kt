package com.example.yoyo.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

enum class TestType(
    val key: String,
    val label: String,
    val fullName: String,
    val description: String,
    val badges: List<String>,
    val audioAsset: String,
    val vo2Formula: String,
) {
    YoYoIr1(
        key = "yoyoIr1",
        label = "Yo-Yo IR1",
        fullName = "Yo-Yo Intermittent Recovery Level 1",
        description = "Progressive intermittent 2 × 20 m shuttle test with 10-second active recovery between 40 m repetitions.",
        badges = listOf("2 × 20m Shuttles", "10s Active Rest", "Intermittent Recovery"),
        audioAsset = "audio/yoyo.m4a",
        vo2Formula = "VO₂max = Distance × 0.0084 + 36.4",
    ),
    YoYoIr2(
        key = "yoyoIr2",
        label = "Yo-Yo IR2",
        fullName = "Yo-Yo Intermittent Recovery Level 2",
        description = "High-intensity intermittent 2 × 20 m shuttle test with 10-second active recovery between 40 m repetitions.",
        badges = listOf("2 × 20m Shuttles", "10s Active Rest", "High Intensity"),
        audioAsset = "audio/yoyo_ir2.m4a",
        vo2Formula = "VO₂max = Distance × 0.0136 + 45.3",
    ),
    BeepTest(
        key = "beepTest",
        label = "Beep Test",
        fullName = "20m Multi-Stage Fitness Test",
        description = "Continuous 20 m shuttle run with increasing pace and no recovery interval.",
        badges = listOf("1 × 20m Shuttles", "No Rest", "21 Speed Levels"),
        audioAsset = "audio/beep_test.m4a",
        vo2Formula = "Adult Léger estimate: VO₂max = 5.857 × speed − 19.458",
    );

    companion object {
        fun fromKey(key: String?): TestType = when (key) {
            "yoyoIr2" -> YoYoIr2
            "beepTest" -> BeepTest
            else -> YoYoIr1
        }
    }
}

enum class TestState { Idle, Running, Paused, Completed }

enum class AthleteStatus(val key: String) {
    Running("running"),
    Warned("warned"),
    Eliminated("eliminated");

    companion object {
        fun fromKey(key: String?): AthleteStatus = entries.firstOrNull { it.key == key } ?: Running
    }
}

enum class ShuttlePhase { Running, Recovery }

data class Shuttle(
    val shuttleNumber: Int,
    val speedLevel: Int,
    val shuttleInLevel: Int,
    val speedKmh: Double,
    val cumulativeDistanceMeters: Int,
    val runDurationSeconds: Double,
    val recoveryDurationSeconds: Double,
) {
    val levelDisplay: String get() = "$speedLevel.$shuttleInLevel"
}

/**
 * How the bundled recording deviates from the nominal table. Measured by
 * detecting every beep in the audio file; per-interval error is tiny but it
 * accumulates to ~1 s over a full Yo-Yo IR1 if the app assumes ideal timing.
 */
data class RecordingTiming(
    /** Audio position of the first shuttle's start beep. */
    val leadMs: Int = 0,
    /** Recorded run duration divided by the nominal run duration. */
    val runScale: Double = 1.0,
    /** Recorded recovery duration, when it differs from the nominal 10 s. */
    val recoveryMs: Int? = null,
)

class ProtocolDefinition(
    val type: TestType,
    val shuttles: List<Shuttle>,
    val audio: RecordingTiming = RecordingTiming(),
) {
    val maxDistanceMeters: Int = shuttles.last().cumulativeDistanceMeters

    /**
     * Beep times on the test clock, taken from the bundled recording so the
     * on-screen phase never disagrees with what the athletes hear.
     */
    val shuttleStartMs: List<Int>
    val runEndMs: List<Int>
    val durationMs: Int

    init {
        val starts = ArrayList<Int>(shuttles.size)
        val ends = ArrayList<Int>(shuttles.size)
        var cursor = 0
        for (shuttle in shuttles) {
            val runMs = (shuttle.runDurationSeconds * audio.runScale * 1000).roundToInt()
            val recoveryMs = if (shuttle.recoveryDurationSeconds > 0) {
                audio.recoveryMs ?: (shuttle.recoveryDurationSeconds * 1000).roundToInt()
            } else {
                0
            }
            starts += cursor
            ends += cursor + runMs
            cursor += runMs + recoveryMs
        }
        shuttleStartMs = starts
        runEndMs = ends
        durationMs = cursor
    }

    fun vo2Max(distanceMeters: Int, speedKmh: Double? = null): Double {
        if (distanceMeters <= 0) return 0.0
        return when (type) {
            TestType.YoYoIr1 -> round1(distanceMeters * 0.0084 + 36.4)
            TestType.YoYoIr2 -> round1(distanceMeters * 0.0136 + 45.3)
            TestType.BeepTest -> round1(5.857 * (speedKmh ?: speedForDistance(distanceMeters)) - 19.458)
        }
    }

    private fun speedForDistance(distanceMeters: Int): Double =
        (shuttles.firstOrNull { it.cumulativeDistanceMeters >= distanceMeters } ?: shuttles.last()).speedKmh

    fun fitnessRating(distanceMeters: Int): String = when {
        distanceMeters >= 2400 -> "Elite (Professional)"
        distanceMeters >= 2000 -> "Excellent"
        distanceMeters >= 1600 -> "Good / Advanced"
        distanceMeters >= 1200 -> "Average / Intermediate"
        distanceMeters >= 800 -> "Below Average"
        else -> "Novice / Needs Improvement"
    }
}

data class StartLevelOption(
    val speedLevel: Int,
    val speedKmh: Double,
    val shuttleNumber: Int,
    val startElapsedMs: Int,
) {
    val label: String get() = "Level $speedLevel · ${speedKmh.fixed1()} km/h"
}

/** Rounds half away from zero, matching Dart's `roundToDouble`. */
private fun roundHalfAway(value: Double): Double = sign(value) * floor(abs(value) + 0.5)

internal fun round1(value: Double): Double = roundHalfAway(value * 10) / 10

/** Equivalent of Dart's `toStringAsFixed(1)`. */
fun Double.fixed1(): String = String.format(Locale.US, "%.1f", this)

private fun createYoYoShuttles(stages: List<Triple<Int, Double, Int>>): List<Shuttle> {
    val shuttles = mutableListOf<Shuttle>()
    var number = 1
    var distance = 0
    for ((level, speed, count) in stages) {
        for (inLevel in 1..count) {
            distance += 40
            shuttles += Shuttle(
                shuttleNumber = number++,
                speedLevel = level,
                shuttleInLevel = inLevel,
                speedKmh = speed,
                cumulativeDistanceMeters = distance,
                runDurationSeconds = 144 / speed,
                recoveryDurationSeconds = 10.0,
            )
        }
    }
    return shuttles
}

private fun createBeepShuttles(): List<Shuttle> {
    val stages = listOf(
        Triple(1, 8.0, 7),
        Triple(2, 9.0, 8),
        Triple(3, 9.5, 8),
        Triple(4, 10.0, 9),
        Triple(5, 10.5, 9),
        Triple(6, 11.0, 10),
        Triple(7, 11.5, 10),
        Triple(8, 12.0, 11),
        Triple(9, 12.5, 11),
        Triple(10, 13.0, 11),
        Triple(11, 13.5, 12),
        Triple(12, 14.0, 12),
        Triple(13, 14.5, 13),
        Triple(14, 15.0, 13),
        Triple(15, 15.5, 13),
        Triple(16, 16.0, 14),
        Triple(17, 16.5, 14),
        Triple(18, 17.0, 15),
        Triple(19, 17.5, 15),
        Triple(20, 18.0, 16),
        Triple(21, 18.5, 16),
    )
    val shuttles = mutableListOf<Shuttle>()
    var number = 1
    var distance = 0
    for ((level, speed, count) in stages) {
        for (inLevel in 1..count) {
            distance += 20
            shuttles += Shuttle(
                shuttleNumber = number++,
                speedLevel = level,
                shuttleInLevel = inLevel,
                speedKmh = speed,
                cumulativeDistanceMeters = distance,
                runDurationSeconds = 72 / speed,
                recoveryDurationSeconds = 0.0,
            )
        }
    }
    return shuttles
}

private val protocols: Map<TestType, ProtocolDefinition> = mapOf(
    TestType.YoYoIr1 to ProtocolDefinition(
        type = TestType.YoYoIr1,
        shuttles = createYoYoShuttles(
            listOf(
                Triple(5, 10.0, 1),
                Triple(9, 12.0, 1),
                Triple(11, 13.0, 2),
                Triple(12, 13.5, 3),
                Triple(13, 14.0, 4),
            ) + List(10) { index -> Triple(14 + index, 14.5 + index * 0.5, 8) },
        ),
        audio = RecordingTiming(runScale = 0.9992, recoveryMs = 10018),
    ),
    TestType.YoYoIr2 to ProtocolDefinition(
        type = TestType.YoYoIr2,
        shuttles = createYoYoShuttles(
            listOf(
                Triple(11, 13.0, 1),
                Triple(15, 15.0, 1),
                Triple(17, 16.0, 2),
                Triple(18, 16.5, 3),
                Triple(19, 17.0, 4),
                Triple(20, 17.5, 8),
                Triple(21, 18.0, 8),
                Triple(22, 18.5, 8),
                Triple(23, 19.0, 8),
                Triple(24, 19.5, 8),
                Triple(25, 20.0, 8),
                Triple(26, 20.5, 8),
            ),
        ),
        audio = RecordingTiming(runScale = 0.9996, recoveryMs = 10009),
    ),
    TestType.BeepTest to ProtocolDefinition(
        type = TestType.BeepTest,
        shuttles = createBeepShuttles(),
        audio = RecordingTiming(leadMs = 10215),
    ),
)

fun protocolFor(type: TestType): ProtocolDefinition = protocols.getValue(type)

private val startLevelOptionsList: List<StartLevelOption> by lazy {
    val protocol = protocolFor(TestType.YoYoIr1)
    val seen = mutableSetOf<Int>()
    protocol.shuttles.mapIndexedNotNull { index, shuttle ->
        if (seen.add(shuttle.speedLevel)) {
            StartLevelOption(
                speedLevel = shuttle.speedLevel,
                speedKmh = shuttle.speedKmh,
                shuttleNumber = shuttle.shuttleNumber,
                startElapsedMs = protocol.shuttleStartMs[index],
            )
        } else {
            null
        }
    }
}

fun startLevelOptions(): List<StartLevelOption> = startLevelOptionsList

data class RuntimeSnapshot(
    val completed: Boolean,
    val shuttle: Shuttle,
    val shuttleIndex: Int,
    val phase: ShuttlePhase,
    val elapsedMs: Int,
    val phaseRemainingSeconds: Double,
    /** Fraction of the current run or recovery that has elapsed (0–1). */
    val phaseProgress: Double = 0.0,
    val currentDistanceMeters: Int,
    val progress: Double,
)

fun deriveRuntime(protocol: ProtocolDefinition, rawElapsedMs: Int): RuntimeSnapshot {
    if (rawElapsedMs < 0) {
        return RuntimeSnapshot(
            completed = false,
            shuttle = protocol.shuttles.first(),
            shuttleIndex = 0,
            phase = ShuttlePhase.Running,
            elapsedMs = rawElapsedMs,
            phaseRemainingSeconds = -rawElapsedMs / 1000.0,
            currentDistanceMeters = 0,
            progress = 0.0,
        )
    }
    val elapsed = rawElapsedMs
    for (index in protocol.shuttles.indices) {
        val shuttle = protocol.shuttles[index]
        val start = protocol.shuttleStartMs[index]
        val runEnd = protocol.runEndMs[index]
        val end = if (index + 1 < protocol.shuttles.size) {
            protocol.shuttleStartMs[index + 1]
        } else {
            protocol.durationMs
        }
        if (elapsed < end) {
            val inRun = elapsed < runEnd
            val phaseEnd = if (inRun) runEnd else end
            return RuntimeSnapshot(
                completed = false,
                shuttle = shuttle,
                shuttleIndex = index,
                phase = if (inRun) ShuttlePhase.Running else ShuttlePhase.Recovery,
                elapsedMs = elapsed,
                phaseRemainingSeconds = max(0.0, (phaseEnd - elapsed) / 1000.0),
                phaseProgress = if (inRun) {
                    (elapsed - start).toDouble() / max(1, runEnd - start)
                } else {
                    (elapsed - runEnd).toDouble() / max(1, end - runEnd)
                },
                currentDistanceMeters = when {
                    inRun && index == 0 -> 0
                    inRun -> protocol.shuttles[index - 1].cumulativeDistanceMeters
                    else -> shuttle.cumulativeDistanceMeters
                },
                progress = min(1.0, elapsed.toDouble() / protocol.durationMs),
            )
        }
    }
    val last = protocol.shuttles.last()
    return RuntimeSnapshot(
        completed = true,
        shuttle = last,
        shuttleIndex = protocol.shuttles.size - 1,
        phase = if (last.recoveryDurationSeconds > 0) ShuttlePhase.Recovery else ShuttlePhase.Running,
        elapsedMs = elapsed,
        phaseRemainingSeconds = 0.0,
        phaseProgress = 1.0,
        currentDistanceMeters = last.cumulativeDistanceMeters,
        progress = 1.0,
    )
}

fun formatTime(milliseconds: Int): String {
    val isNegative = milliseconds < 0
    val total = abs(milliseconds) / 1000
    val minutes = total / 60
    val seconds = total % 60
    val formatted = "%02d:%02d".format(Locale.US, minutes, seconds)
    return if (isNegative) "-$formatted" else formatted
}
