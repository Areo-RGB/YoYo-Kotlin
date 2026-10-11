package com.example.yoyo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yoyo.audio.AudioEngine
import com.example.yoyo.audio.SilentAudioEngine
import com.example.yoyo.domain.Athlete
import com.example.yoyo.domain.AthleteStatus
import com.example.yoyo.domain.LeaderboardEntry
import com.example.yoyo.domain.LeaderboardMetric
import com.example.yoyo.domain.RuntimeSnapshot
import com.example.yoyo.domain.SessionResult
import com.example.yoyo.domain.TestSession
import com.example.yoyo.domain.TestState
import com.example.yoyo.domain.TestType
import com.example.yoyo.domain.buildLeaderboard
import com.example.yoyo.domain.defaultRoster
import com.example.yoyo.domain.deriveRuntime
import com.example.yoyo.domain.fixed1
import com.example.yoyo.domain.protocolFor
import com.example.yoyo.domain.startLevelOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class AppTab(val label: String, val shortLabel: String) {
    Setup("Roster", "Roster"),
    Live("Live Test", "Live"),
    Results("Results", "Results"),
    History("History", "History"),
    Settings("Settings", "Settings"),
}

private data class UndoEntry(val athlete: Athlete, val description: String)

/**
 * Owns the roster, the running protocol clock, audio sync and saved history.
 * All state is Compose snapshot state, so the UI recomposes on change.
 * Must be used from a single (main) thread; audio work is serialised.
 */
class AppController(
    private val scope: CoroutineScope,
    private val audio: AudioEngine = SilentAudioEngine(),
    private val store: StateStore = InMemoryStateStore(),
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val encodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val athletes = mutableStateListOf<Athlete>()
    val sessions = mutableStateListOf<TestSession>()
    private val undoStack = mutableStateListOf<UndoEntry>()

    var cachedAudio by mutableStateOf(setOf<TestType>())
        private set
    var activeTab by mutableStateOf(AppTab.Setup)
        private set
    var selectedTestType by mutableStateOf(TestType.YoYoIr1)
        internal set
    var leaderboardMetric by mutableStateOf(LeaderboardMetric.AverageDistance)
        private set
    var testState by mutableStateOf(TestState.Idle)
        internal set

    private var runtimeState by mutableStateOf(deriveRuntime(protocolFor(TestType.YoYoIr1), 0))
    var runtime: RuntimeSnapshot
        get() = runtimeState
        internal set(value) {
            runtimeState = value
            updateCanRecord()
        }

    var elapsedMs = 0
        internal set
    var selectedYoYoStartLevel by mutableStateOf(5)
        private set
    var soundEnabled by mutableStateOf(true)
        private set
    var fullscreen by mutableStateOf(true)
        private set
    var initialized by mutableStateOf(false)
        private set
    var sessionSavedId by mutableStateOf<String?>(null)
        private set

    /** Keep the display awake while the protocol clock is running. */
    var keepScreenOn by mutableStateOf(false)
        private set

    /**
     * Whether Miss/Out can be recorded right now (running, countdown over).
     * Separate state so athlete tiles don't recompose on every clock tick.
     */
    var canRecord by mutableStateOf(false)
        private set

    private var clockStartedAt: Long? = null
    private var clockOffsetMs = 0
    private var timerJob: Job? = null
    private val audioMutex = Mutex()
    private var audioSyncInFlight = false
    private var ticksSinceAudioSync = 0
    private var audioSession = 0
    private var persisting = false
    private var persistQueued = false
    private var idCounter = 0

    private fun updateCanRecord() {
        canRecord = testState == TestState.Running && runtime.elapsedMs >= 0
    }

    private fun setTestStateValue(state: TestState) {
        testState = state
        updateCanRecord()
    }

    private val isActiveTest: Boolean
        get() = testState == TestState.Running || testState == TestState.Paused

    val selectedAthletes: List<Athlete> get() = athletes.filter { it.isSelected }

    val results: List<Athlete>
        get() = athletes
            .filter { it.isSelected && it.finalDistanceMeters != null }
            .sortedBy { it.rank ?: 999 }

    val leaderboard: List<LeaderboardEntry> get() = buildLeaderboard(sessions, leaderboardMetric)

    val undoDescription: String? get() = undoStack.lastOrNull()?.description

    fun toggleLeaderboardMetric() {
        leaderboardMetric = if (leaderboardMetric == LeaderboardMetric.AverageDistance) {
            LeaderboardMetric.TotalDistance
        } else {
            LeaderboardMetric.AverageDistance
        }
    }

    suspend fun initialize() {
        val raw = try {
            store.read()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
        if (raw != null) {
            try {
                val data = json.parseToJsonElement(raw).jsonObject
                val roster = (data["roster"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                val saved = (data["sessions"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                val parsedAthletes = roster.map { Athlete.fromJson(it).resetForTest() }
                val parsedSessions = saved.map(TestSession::fromJson)
                athletes.addAll(parsedAthletes)
                sessions.addAll(parsedSessions)
            } catch (error: Exception) {
                // Corrupt local state should not prevent the app from opening.
            }
        }
        if (athletes.isEmpty()) athletes.addAll(defaultRoster())
        initialized = true
        applyAudioVolume()
        for (type in TestType.entries) queueAudioWarmup(type)
        queueAudioWarmup(selectedTestType)
    }

    fun selectTab(tab: AppTab) {
        activeTab = tab
    }

    fun setTestType(type: TestType) {
        if (isActiveTest) return
        selectedTestType = type
        setTestStateValue(TestState.Idle)
        runtime = deriveRuntime(protocolFor(type), 0)
        elapsedMs = 0
        resetAllAthletes()
        undoStack.clear()
        sessionSavedId = null
        queueAudioWarmup(type)
    }

    fun setStartLevel(level: Int) {
        if (isActiveTest) return
        if (startLevelOptions().any { it.speedLevel == level }) selectedYoYoStartLevel = level
    }

    /** Shared guard for roster edits: never mid-test; a finished test reverts to idle. */
    private inline fun editRoster(block: () -> Unit) {
        if (isActiveTest) return
        if (testState == TestState.Completed) setTestStateValue(TestState.Idle)
        block()
        persist()
    }

    fun toggleAthlete(id: String) = editRoster {
        val index = athletes.indexOfFirst { it.id == id }
        if (index >= 0) athletes[index] = athletes[index].copy(isSelected = !athletes[index].isSelected)
    }

    fun selectAll() = editRoster {
        athletes.updateAll { it.copy(isSelected = true) }
    }

    fun deselectAll() = editRoster {
        athletes.updateAll { it.copy(isSelected = false) }
    }

    fun resetRoster() = editRoster {
        athletes.clear()
        athletes.addAll(defaultRoster())
    }

    fun addAthlete(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        editRoster {
            val micros = wallClockMs() * 1000 + (idCounter++ % 1000)
            athletes.add(Athlete(id = "athlete_$micros", name = clean.take(40)))
        }
    }

    fun removeAthlete(id: String) = editRoster {
        athletes.removeAll { it.id == id }
    }

    private fun resetAllAthletes() {
        athletes.updateAll { it.resetForTest() }
    }

    private fun audioPositionForElapsed(testElapsedMs: Int): Int =
        max(0, testElapsedMs + protocolFor(selectedTestType).audio.leadMs)

    fun startTest() {
        if (testState != TestState.Idle || selectedAthletes.isEmpty()) return
        val protocol = protocolFor(selectedTestType)
        val start = if (selectedTestType == TestType.YoYoIr1) {
            (startLevelOptions().firstOrNull { it.speedLevel == selectedYoYoStartLevel }
                ?: startLevelOptions().first()).startElapsedMs
        } else {
            -protocol.audio.leadMs
        }
        resetAllAthletes()
        undoStack.clear()
        sessionSavedId = null
        elapsedMs = start
        clockOffsetMs = start
        clockStartedAt = monotonicMs()
        val session = ++audioSession
        testState = TestState.Running
        runtime = deriveRuntime(protocol, elapsedMs)
        activeTab = AppTab.Live
        keepScreenOn = true
        startTimer()
        startAudio(session)
    }

    private fun queueAudio(block: suspend () -> Unit): Job = scope.launch {
        audioMutex.withLock {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Audio is optional; the monotonic clock keeps running without it.
            }
        }
    }

    private fun queueAudioWarmup(type: TestType) {
        queueAudio {
            audio.load(type)
            cachedAudio = cachedAudio + type
        }
    }

    private fun startAudio(session: Int) {
        queueAudio {
            if (!isAudioSessionActive(session)) return@queueAudio
            audio.ensureLoaded(selectedTestType)
            if (!isAudioSessionActive(session)) return@queueAudio
            audio.seekTo(audioPositionForElapsed(currentClockMs()))
            if (!isAudioSessionActive(session)) return@queueAudio
            audio.setVolume(if (soundEnabled) 1f else 0f)
            audio.start()
        }
    }

    private fun isAudioSessionActive(session: Int): Boolean =
        session == audioSession && testState == TestState.Running

    private fun applyAudioVolume() {
        val volume = if (soundEnabled) 1f else 0f
        scope.launch {
            try {
                audio.setVolume(volume)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun currentClockMs(): Int =
        clockOffsetMs + (clockStartedAt?.let { (monotonicMs() - it).toInt() } ?: 0)

    private fun tick() {
        if (testState != TestState.Running) return
        if (++ticksSinceAudioSync >= 5) {
            ticksSinceAudioSync = 0
            syncClockToAudio()
        }
        elapsedMs = currentClockMs()
        runtime = deriveRuntime(protocolFor(selectedTestType), elapsedMs)
        if (runtime.completed) completeNaturally()
    }

    /**
     * The athletes run to the audio, so the audio position is the master clock.
     * The stopwatch only interpolates between samples; this pulls it back onto
     * the recording after start/resume latency, audio-focus interruptions or
     * crystal drift between the audio output and the CPU clock.
     */
    private fun syncClockToAudio() {
        if (audioSyncInFlight || !audio.isPlaying) return
        audioSyncInFlight = true
        val session = audioSession
        scope.launch {
            try {
                // Skip this sample rather than queue behind a seek or load.
                if (!audioMutex.tryLock()) return@launch
                try {
                    val before = currentClockMs()
                    val position = audio.currentPositionMs()
                    val after = currentClockMs()
                    if (position == null || position <= 0 || !audio.isPlaying ||
                        !isAudioSessionActive(session) || after - before > 60
                    ) {
                        return@launch
                    }
                    val audioClockMs = position - protocolFor(selectedTestType).audio.leadMs
                    val drift = audioClockMs - (before + after) / 2
                    if (abs(drift) > 20) clockOffsetMs += drift
                } finally {
                    audioMutex.unlock()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Keep the stopwatch running on its own if the platform cannot report.
            } finally {
                audioSyncInFlight = false
            }
        }
    }

    private fun stopClock() {
        timerJob?.cancel()
        timerJob = null
        clockStartedAt = null
        keepScreenOn = false
    }

    private fun completeNaturally() {
        ++audioSession
        val atRuntime = runtime
        finalizeRemaining(atRuntime)
        rankAthletes()
        elapsedMs = atRuntime.elapsedMs
        setTestStateValue(TestState.Completed)
        stopClock()
        queueAudio { audio.stop() }
        persist()
    }

    fun pauseTest() {
        if (testState != TestState.Running) return
        elapsedMs = currentClockMs()
        clockOffsetMs = elapsedMs
        stopClock()
        setTestStateValue(TestState.Paused)
        queueAudio { audio.pause() }
    }

    fun resumeTest() {
        if (testState != TestState.Paused) return
        clockOffsetMs = elapsedMs
        clockStartedAt = monotonicMs()
        setTestStateValue(TestState.Running)
        val session = ++audioSession
        val resumeAt = elapsedMs
        keepScreenOn = true
        startTimer()
        queueAudio {
            audio.seekTo(audioPositionForElapsed(resumeAt))
            if (isAudioSessionActive(session)) audio.start()
        }
    }

    fun stopAndFinishTest() {
        if (!isActiveTest) return
        ++audioSession
        if (testState == TestState.Running) elapsedMs = currentClockMs()
        runtime = deriveRuntime(protocolFor(selectedTestType), elapsedMs)
        finalizeRemaining(runtime)
        rankAthletes()
        stopClock()
        queueAudio { audio.stop() }
        setTestStateValue(TestState.Completed)
        activeTab = AppTab.Results
        persist()
    }

    fun resetTest() {
        ++audioSession
        stopClock()
        queueAudio { audio.stop() }
        setTestStateValue(TestState.Idle)
        elapsedMs = 0
        runtime = deriveRuntime(protocolFor(selectedTestType), 0)
        resetAllAthletes()
        undoStack.clear()
        sessionSavedId = null
        activeTab = AppTab.Setup
    }

    private fun indexOf(id: String): Int = athletes.indexOfFirst { it.id == id }

    fun markMiss(id: String) {
        if (testState != TestState.Running || runtime.elapsedMs < 0) return
        val index = indexOf(id)
        val athlete = athletes.getOrNull(index) ?: return
        if (!athlete.isSelected || athlete.status == AthleteStatus.Eliminated) return
        undoStack.add(UndoEntry(athlete, "Undo action for ${athlete.name}"))
        athletes[index] = if (selectedTestType == TestType.BeepTest) {
            val currentShuttle = runtime.shuttle.shuttleNumber
            if (athlete.lastMissShuttle == currentShuttle - 1 || athlete.lastMissShuttle == currentShuttle) {
                finalized(athlete, runtime)
            } else {
                warned(athlete)
            }
        } else if (athlete.status == AthleteStatus.Warned) {
            finalized(athlete, runtime)
        } else {
            warned(athlete)
        }
        rankAthletes()
    }

    fun eliminate(id: String) {
        if (testState != TestState.Running || runtime.elapsedMs < 0) return
        val index = indexOf(id)
        val athlete = athletes.getOrNull(index) ?: return
        if (!athlete.isSelected || athlete.status == AthleteStatus.Eliminated) return
        undoStack.add(UndoEntry(athlete, "Restore ${athlete.name}"))
        athletes[index] = finalized(athlete, runtime)
        rankAthletes()
    }

    private fun warned(athlete: Athlete): Athlete = athlete.copy(
        status = AthleteStatus.Warned,
        consecutiveMisses = 1,
        lastMissShuttle = runtime.shuttle.shuttleNumber,
        warningDistanceMeters = runtime.currentDistanceMeters,
        warningLevel = runtime.shuttle.levelDisplay,
        warningTimestampMs = runtime.elapsedMs,
    )

    private fun finalized(athlete: Athlete, atRuntime: RuntimeSnapshot): Athlete {
        val protocol = protocolFor(selectedTestType)
        val distance = if (atRuntime.completed) protocol.maxDistanceMeters else atRuntime.currentDistanceMeters
        return athlete.copy(
            status = AthleteStatus.Eliminated,
            finalDistanceMeters = distance,
            finalLevel = atRuntime.shuttle.levelDisplay,
            finishTimestampMs = max(0, atRuntime.elapsedMs),
            // For the beep test, the achieved distance maps to the last completed
            // shuttle. Passing the attempted shuttle speed would overstate VO₂max
            // whenever an athlete fails during a faster shuttle.
            vo2Max = protocol.vo2Max(distance),
        )
    }

    private fun finalizeRemaining(atRuntime: RuntimeSnapshot) {
        athletes.updateAll { athlete ->
            if (athlete.isSelected && athlete.finalDistanceMeters == null) finalized(athlete, atRuntime) else athlete
        }
    }

    private fun rankAthletes() {
        val finished = athletes
            .filter { it.finalDistanceMeters != null }
            .sortedWith(
                compareByDescending<Athlete> { it.finalDistanceMeters ?: 0 }
                    .thenBy { it.finishTimestampMs ?: 0 },
            )
        val ranks = HashMap<String, Int>()
        var previousDistance = -1
        var previousRank = 0
        finished.forEachIndexed { index, athlete ->
            val distance = athlete.finalDistanceMeters ?: 0
            val rank = if (distance == previousDistance) previousRank else index + 1
            ranks[athlete.id] = rank
            previousDistance = distance
            previousRank = rank
        }
        athletes.updateAll { athlete ->
            val rank = ranks[athlete.id]
            if (rank != null && rank != athlete.rank) athlete.copy(rank = rank) else athlete
        }
    }

    fun undoLastAction() {
        val entry = undoStack.removeLastOrNull() ?: return
        val index = indexOf(entry.athlete.id)
        if (index < 0) return
        athletes[index] = entry.athlete
        rankAthletes()
    }

    fun changeSoundEnabled(value: Boolean) {
        soundEnabled = value
        applyAudioVolume()
    }

    fun changeFullscreen(value: Boolean) {
        fullscreen = value
    }

    fun cacheAudio(type: TestType) {
        if (testState != TestState.Idle) return
        queueAudioWarmup(type)
    }

    fun saveSession() {
        val currentResults = results
        if (testState != TestState.Completed || sessionSavedId != null || currentResults.isEmpty()) return
        val nowMs = wallClockMs()
        val best = currentResults.first()
        val session = TestSession(
            id = (nowMs * 1000).toString(),
            testType = selectedTestType,
            title = "${selectedTestType.label} ${shortDate(nowMs)}",
            timestampMs = nowMs,
            durationSeconds = max(0, (elapsedMs / 1000.0).roundToInt()),
            maxDistanceAchieved = best.finalDistanceMeters ?: 0,
            maxLevelAchieved = best.finalLevel ?: "",
            totalAthletesCount = selectedAthletes.size,
            completedAthletesCount = currentResults.size,
            results = currentResults.map { athlete ->
                SessionResult(
                    athleteName = athlete.name,
                    finalDistanceMeters = athlete.finalDistanceMeters ?: 0,
                    finalLevel = athlete.finalLevel ?: "",
                    rank = athlete.rank ?: 0,
                    vo2Max = athlete.vo2Max ?: 0.0,
                    warningDistanceMeters = athlete.warningDistanceMeters,
                    warningLevel = athlete.warningLevel,
                )
            },
        )
        sessions.add(0, session)
        sessionSavedId = session.id
        persist()
    }

    fun deleteSession(id: String) {
        sessions.removeAll { it.id == id }
        persist()
    }

    fun makeCsv(): String {
        fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        val rows = mutableListOf(
            listOf("Rank", "Athlete", "Distance (m)", "Level", "VO2max", "Warning Distance (m)"),
        )
        for (athlete in results) {
            rows += listOf(
                athlete.rank?.toString() ?: "",
                athlete.name,
                (athlete.finalDistanceMeters ?: 0).toString(),
                athlete.finalLevel ?: "",
                (athlete.vo2Max ?: 0.0).fixed1(),
                athlete.warningDistanceMeters?.toString() ?: "",
            )
        }
        return rows.joinToString("\n") { row -> row.joinToString(",", transform = ::quote) }
    }

    fun sessionSummary(session: TestSession): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(session.timestampMs))
        return (
            listOf("${session.testType.label}: ${session.title}", stamp, "---") +
                session.results.map { result ->
                    "${result.rank}. ${result.athleteName}: ${result.finalDistanceMeters} m " +
                        "(Lvl ${result.finalLevel}, VO2max ${result.vo2Max.fixed1()})"
                }
            ).joinToString("\n")
    }

    private fun persist() {
        if (!initialized) return
        persistQueued = true
        if (persisting) return
        persisting = true
        scope.launch { flushPersistence() }
    }

    private suspend fun flushPersistence() {
        try {
            while (persistQueued) {
                persistQueued = false
                val savedSessions = sessions.toList()
                val roster = athletes.map { it.resetForTest() }
                val encoded = withContext(encodeDispatcher) { encodeState(savedSessions, roster) }
                store.write(encoded)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A later state change will schedule another persistence attempt.
        } finally {
            persisting = false
        }
    }

    fun dispose() {
        stopClock()
        audio.release()
    }

    companion object {
        private const val TICK_MS = 100L
        private val json = Json { ignoreUnknownKeys = true }

        internal fun encodeState(sessions: List<TestSession>, roster: List<Athlete>): String =
            buildJsonObject {
                put("sessions", buildJsonArray { sessions.forEach { add(it.toJson()) } })
                put("roster", buildJsonArray { roster.forEach { add(it.toJson()) } })
            }.toString()

        /** M/D/YYYY, as the session titles have always used. */
        fun shortDate(epochMs: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
            return "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.YEAR)}"
        }
    }
}

/** In-place map that writes only changed elements, so unchanged rows keep their identity. */
private inline fun <T> MutableList<T>.updateAll(transform: (T) -> T) {
    for (index in indices) {
        val current = this[index]
        val next = transform(current)
        if (next != current) this[index] = next
    }
}
