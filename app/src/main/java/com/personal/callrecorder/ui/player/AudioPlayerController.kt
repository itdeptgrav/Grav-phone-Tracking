package com.personal.callrecorder.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Immutable snapshot of playback state for the UI. */
data class PlayerUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

/**
 * Thin wrapper around an ExoPlayer for single-file call playback. Owns the
 * player instance; the caller must invoke [release] when done (a DisposableEffect
 * in the detail screen does this).
 */
class AudioPlayerController(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isReady = playbackState == Player.STATE_READY ||
                        playbackState == Player.STATE_ENDED,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = "Cannot play recording")
            }
        })
    }

    /** Load an audio file. If missing, publishes an error and does not throw. */
    fun setFile(path: String?) {
        if (path.isNullOrBlank() || !File(path).let { it.exists() && it.length() > 0 }) {
            _state.value = PlayerUiState(error = "Recording file not found")
            return
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
    }

    fun play() = player.play()
    fun pause() = player.pause()

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0, player.duration.coerceAtLeast(0)))
        refreshPosition()
    }

    /** Skip forward/backward by [deltaMs] (negative to rewind). */
    fun skip(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    /** Called on a timer while visible to keep the scrubber in sync. */
    fun refreshPosition() {
        _state.value = _state.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0)
        )
    }

    fun release() {
        player.release()
    }
}
