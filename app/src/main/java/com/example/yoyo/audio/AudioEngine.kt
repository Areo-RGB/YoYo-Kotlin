package com.example.yoyo.audio

import com.example.yoyo.domain.TestType

/**
 * Playback of the bundled protocol recordings. Every call may suspend while
 * the platform player does its work; the controller serialises them.
 */
interface AudioEngine {
    /** True while the recording is audibly advancing. */
    val isPlaying: Boolean

    /** Prepares the recording for [type], replacing any loaded one. */
    suspend fun load(type: TestType)

    /** Loads [type] only when it is not already the prepared recording. */
    suspend fun ensureLoaded(type: TestType)

    suspend fun seekTo(positionMs: Int)

    suspend fun start()

    suspend fun pause()

    suspend fun stop()

    /** Current playback position, or null when the player cannot report one. */
    suspend fun currentPositionMs(): Int?

    suspend fun setVolume(volume: Float)

    fun release()
}

/** Silent engine for tests and devices where playback is unavailable. */
class SilentAudioEngine : AudioEngine {
    override val isPlaying: Boolean = false
    override suspend fun load(type: TestType) = Unit
    override suspend fun ensureLoaded(type: TestType) = Unit
    override suspend fun seekTo(positionMs: Int) = Unit
    override suspend fun start() = Unit
    override suspend fun pause() = Unit
    override suspend fun stop() = Unit
    override suspend fun currentPositionMs(): Int? = null
    override suspend fun setVolume(volume: Float) = Unit
    override fun release() = Unit
}
