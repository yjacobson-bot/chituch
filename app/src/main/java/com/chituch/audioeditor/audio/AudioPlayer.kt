package com.chituch.audioeditor.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var _duration: Long = 0L
    private var previewEndMs: Long = Long.MAX_VALUE

    private var onProgressChanged: ((Long) -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null
    private var onDurationReady: ((Long) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var loopStartMs: Long = -1L
    private var _isLooping: Boolean = false
    private var _speed: Float = 1f

    val duration: Long get() = _duration
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val currentPosition: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun setOnProgressChanged(listener: (Long) -> Unit) { onProgressChanged = listener }
    fun setOnPlaybackComplete(listener: () -> Unit) { onPlaybackComplete = listener }
    fun setOnDurationReady(listener: (Long) -> Unit) { onDurationReady = listener }

    fun loadAudio(uri: Uri) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            _duration = duration.toLong()
            onDurationReady?.invoke(_duration)
            setOnCompletionListener {
                stopProgressTracking()
                onPlaybackComplete?.invoke()
            }
        }
        applySpeed()
    }

    fun play() {
        previewEndMs = Long.MAX_VALUE
        mediaPlayer?.let { if (!it.isPlaying) { it.start(); startProgressTracking() } }
    }

    fun playSegment(startMs: Long, endMs: Long, loop: Boolean = false) {
        previewEndMs = endMs
        loopStartMs = startMs
        _isLooping = loop
        mediaPlayer?.let {
            it.seekTo(startMs.toInt())
            if (!it.isPlaying) it.start()
            startProgressTracking()
        }
    }

    fun setLooping(loop: Boolean) { _isLooping = loop }

    fun pause() {
        mediaPlayer?.let { if (it.isPlaying) { it.pause(); stopProgressTracking() } }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        onProgressChanged?.invoke(positionMs)
    }

    fun setSpeed(speed: Float) {
        _speed = speed
        applySpeed()
    }

    fun release() {
        stopProgressTracking()
        mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        mediaPlayer = null
        _duration = 0L
    }

    private fun applySpeed() {
        mediaPlayer?.let {
            try {
                val params = PlaybackParams().setSpeed(_speed)
                it.playbackParams = params
            } catch (_: Exception) {}
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (mediaPlayer?.isPlaying == true) {
                val pos = currentPosition
                onProgressChanged?.invoke(pos)
                if (pos >= previewEndMs) {
                    if (_isLooping && loopStartMs >= 0) {
                        mediaPlayer?.seekTo(loopStartMs.toInt())
                    } else {
                        pause()
                        previewEndMs = Long.MAX_VALUE
                        onPlaybackComplete?.invoke()
                        break
                    }
                }
                delay(50)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
