package com.chituch.audioeditor.audio

import android.content.Context
import android.media.MediaPlayer
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
    private var onProgressChanged: ((Long) -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null
    private var onDurationReady: ((Long) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    val duration: Long get() = _duration
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val currentPosition: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun setOnProgressChanged(listener: (Long) -> Unit) {
        onProgressChanged = listener
    }

    fun setOnPlaybackComplete(listener: () -> Unit) {
        onPlaybackComplete = listener
    }

    fun setOnDurationReady(listener: (Long) -> Unit) {
        onDurationReady = listener
    }

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
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startProgressTracking()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressTracking()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        onProgressChanged?.invoke(positionMs)
    }

    fun release() {
        stopProgressTracking()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        _duration = 0L
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (mediaPlayer?.isPlaying == true) {
                onProgressChanged?.invoke(currentPosition)
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
