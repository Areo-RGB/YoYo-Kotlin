package com.example.yoyo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import com.example.yoyo.domain.TestType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * [MediaPlayer]-backed engine. All player calls are confined to one
 * background thread so preparing a long recording never blocks the UI.
 */
class MediaPlayerAudioEngine(context: Context) : AudioEngine {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yoyo-audio").apply { isDaemon = true }
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    private var player: MediaPlayer? = null
    private var loadedType: TestType? = null
    private var volume = 1f

    @Volatile
    private var playing = false

    override val isPlaying: Boolean get() = playing

    override suspend fun load(type: TestType) = withContext(dispatcher) {
        releasePlayer()
        val next = MediaPlayer()
        try {
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            appContext.assets.openFd(type.audioAsset).use { fd ->
                next.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            next.setOnCompletionListener { playing = false }
            next.prepare()
            next.setVolume(volume, volume)
            player = next
            loadedType = type
        } catch (error: Exception) {
            next.release()
            throw error
        }
    }

    override suspend fun ensureLoaded(type: TestType) {
        if (withContext(dispatcher) { loadedType != type || player == null }) load(type)
    }

    override suspend fun seekTo(positionMs: Int) {
        // MediaPlayer seeks asynchronously; wait (briefly) for it to land so
        // playback starts from the requested beep rather than the old spot.
        val landed = CompletableDeferred<Unit>()
        withContext(dispatcher) {
            val current = player
            if (current == null) {
                landed.complete(Unit)
                return@withContext
            }
            current.setOnSeekCompleteListener { landed.complete(Unit) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                current.seekTo(positionMs.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                current.seekTo(positionMs)
            }
        }
        withTimeoutOrNull(SEEK_TIMEOUT_MS) { landed.await() }
    }

    override suspend fun start() = withContext(dispatcher) {
        val current = player ?: return@withContext
        current.start()
        playing = true
    }

    override suspend fun pause() = withContext(dispatcher) {
        playing = false
        player?.takeIf { it.isPlaying }?.pause()
        Unit
    }

    override suspend fun stop() = withContext(dispatcher) {
        // Pausing keeps the recording prepared for the next start; the start
        // path always seeks before playing.
        playing = false
        player?.takeIf { it.isPlaying }?.pause()
        Unit
    }

    override suspend fun currentPositionMs(): Int? = withContext(dispatcher) {
        player?.currentPosition
    }

    override suspend fun setVolume(volume: Float) = withContext(dispatcher) {
        this@MediaPlayerAudioEngine.volume = volume
        player?.setVolume(volume, volume)
        Unit
    }

    override fun release() {
        playing = false
        executor.execute { releasePlayer() }
        executor.shutdown()
    }

    private fun releasePlayer() {
        playing = false
        player?.release()
        player = null
        loadedType = null
    }

    private companion object {
        const val SEEK_TIMEOUT_MS = 750L
    }
}
