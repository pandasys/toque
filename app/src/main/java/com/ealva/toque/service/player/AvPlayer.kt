/*
 * Copyright 2021 eAlva.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.toque.service.player

import com.ealva.toque.common.Micros
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Volume
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.EqPreset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface AvPlayer {
  val eventFlow: SharedFlow<AvPlayerEvent>

  val isSeekable: Boolean
  val isPausable: Boolean
  val isValid: Boolean
  val equalizer: EqPreset
  val duration: Millis
  val time: Millis
  val isPlaying: Boolean
  val isPaused: Boolean
  val isShutdown: Boolean
  var volume: Volume
  var isMuted: Boolean
  var playbackRate: PlaybackRate
  /** Player audio delay in Microseconds */
  var audioDelay: Micros
  /** Player subtitle delay in Microseconds */
  var spuDelay: Micros
  val isVideoPlayer: Boolean
  val isAudioPlayer: Boolean

  fun playStartPaused()
  fun play(immediate: Boolean = false)
  fun pause(immediate: Boolean = false)
  fun seek(position: Millis)
  fun stop()
  fun shutdown()

  fun setEqualizer(newEq: EqPreset, applyEdits: Boolean)
  fun transitionTo(transition: PlayerTransition)

  //  val vlcVout: IVLCVout
//  val audioTracks: List<TheMediaController.TrackInfo>
//  fun setAudioTrack(id: Int)
//  val spuTracks: List<TheMediaController.TrackInfo>
//  fun setSpuTrack(id: Int)

  companion object {
    // For some operations, any time >= (Media.duration - DURATION_OFFSET_CONSIDERED_END) is
    // considered the end of the media. For example, seeking to Media.duration during playback will
    // cause an immediate jump to the next media, which will frustrate the user trying to seek
    // within the bounds of 0..Media.duration.
    const val OFFSET_CONSIDERED_END = 600L

    val DEFAULT_VOLUME_RANGE = Volume.NONE..Volume.MAX
  }
}

/**
 * Events which emanate from [AvPlayer.eventFlow] regarding the state of the player
 */
sealed interface AvPlayerEvent {
  /**
   * The player has been prepared, which currently means it has progressed far enough through
   * opening and buffering that it is considered "playable". [position] is within 0..[duration]. The
   * starting playback position is not always Millis(0).
   */
  data class Prepared(val position: Millis, val duration: Millis) : AvPlayerEvent
  /**
   * The position within the media has changed. [position] is within 0..[duration]. If [isPlaying]
   * this PositionUpdate is due to media playing else it's due to user seeking.
   */
  data class PositionUpdate(
    val position: Millis,
    val duration: Millis,
    val isPlaying: Boolean
  ) : AvPlayerEvent

  /**
   * Playback has started. If [firstStart] is true it's the first time the user has initiated
   * playback, either via pressing play or manually/automatically transition from one media to
   * the next.
   */
  data class Start(val firstStart: Boolean) : AvPlayerEvent

  /**
   * Playback has been paused. The [position] is the time reported by the underlying MediaPlayer
   */
  data class Paused(val position: Millis) : AvPlayerEvent

  /**
   * Playback has been stopped. This differs from paused in that resources are released as if
   * playback will not resume. The underlying player should support resumption but must reacquire
   * necessary resources and prepare for playback.
   */
  data class Stopped(val position: Millis) : AvPlayerEvent

  /**
   * Playback has reached the end of the media.
   */
  object PlaybackComplete : AvPlayerEvent {
    override fun toString(): String = "PlaybackComplete"
  }

  /**
   * The was an error during playback. The player isn't valid anymore and playback has stopped.
   */
  object Error : AvPlayerEvent {
    override fun toString(): String = "Error"
  }

  /**
   * Placeholder for primordial event if necessary, such as a StateFlow of some type. Currently,
   * player implementations use a SharedFlow to support replay for a client which is not
   * well-established when the flow starts.
   */
  object None : AvPlayerEvent {
    override fun toString(): String = "None"
  }
}
object NullAvPlayer : AvPlayer {
  override val eventFlow: SharedFlow<AvPlayerEvent> = MutableSharedFlow()
  override val isSeekable: Boolean = false
  override val isPausable: Boolean = false
  override val isValid: Boolean = false
  override val equalizer: EqPreset = EqPreset.NONE
  override val duration: Millis = Millis(0)
  override val time: Millis = Millis(0)
  override val isPlaying: Boolean = false
  override val isPaused: Boolean = false
  override val isShutdown: Boolean = false
  override var volume: Volume = Volume.MAX
  override var isMuted: Boolean = false
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL
  override var audioDelay: Micros = Micros(0)
  override var spuDelay: Micros = Micros(0)
  override val isVideoPlayer: Boolean = false
  override val isAudioPlayer: Boolean = true
  override fun playStartPaused() = Unit
  override fun play(immediate: Boolean) {}
  override fun pause(immediate: Boolean) {}
  override fun seek(position: Millis) {}
  override fun stop() {}
  override fun shutdown() {}
  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {}
  override fun transitionTo(transition: PlayerTransition) {}
}
