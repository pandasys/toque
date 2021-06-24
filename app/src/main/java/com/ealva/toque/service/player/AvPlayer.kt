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
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.MediaPlayerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AvPlayer {
  val eventFlow: Flow<MediaPlayerEvent>

  val isValid: Boolean
  fun setEqualizer(newEq: EqPreset, applyEdits: Boolean)
  val equalizer: EqPreset
  val isPausable: Boolean
  fun pause(immediate: Boolean = false)
  fun play(immediate: Boolean = false)
  fun shutdown()
  val duration: Millis
  val isSeekable: Boolean
  fun seek(position: Millis)
  val time: Millis
  val isPlaying: Boolean
  val isPaused: Boolean
  val isShutdown: Boolean
  var volume: Volume
  var isMuted: Boolean
  val isVideoPlayer: Boolean
  val isAudioPlayer: Boolean
  fun stop()
  val isPrepared: Boolean

  //  val vlcVout: IVLCVout
//  val audioTracks: List<TheMediaController.TrackInfo>
//  fun setAudioTrack(id: Int)
//  val spuTracks: List<TheMediaController.TrackInfo>
//  fun setSpuTrack(id: Int)

  /**
   * Player audio delay in Microseconds
   */
  var audioDelay: Micros

  /**
   * Player subtitle delay in Microseconds
   */
  var spuDelay: Micros

  fun transitionTo(transition: PlayerTransition)

  //  val media: AvMedia?
  var playbackRate: PlaybackRate

  companion object {
    // For some operations, any time >= (Media.duration - DURATION_OFFSET_CONSIDERED_END) is
    // considered the end of the media. For example, seeking to Media.duration during playback will
    // cause an immediate jump to the next media, which will frustrate the user trying to seek
    // within the bounds of 0..Media.duration.
    const val DURATION_OFFSET_CONSIDERED_END = 600L

    val DEFAULT_VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED
  }
}

object NullAvPlayer : AvPlayer {
  override val eventFlow: Flow<MediaPlayerEvent> = emptyFlow()
  override val isValid: Boolean = false
  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {}
  override val equalizer: EqPreset = EqPreset.NULL
  override val isPausable: Boolean = false
  override fun pause(immediate: Boolean) {}
  override fun play(immediate: Boolean) {}
  override fun shutdown() {}
  override val duration: Millis = Millis.ZERO
  override val isSeekable: Boolean = false
  override fun seek(position: Millis) {}
  override val time: Millis = Millis.ZERO
  override val isPlaying: Boolean = false
  override val isPaused: Boolean = false
  override val isShutdown: Boolean = true
  override var volume: Volume = Volume.ONE_HUNDRED
  override var isMuted: Boolean = false
  override val isVideoPlayer: Boolean = false
  override val isAudioPlayer: Boolean = true
  override fun stop() {}
  override val isPrepared: Boolean = false
  override var audioDelay: Micros = Micros.ZERO
  override var spuDelay: Micros = Micros.ZERO
  override fun transitionTo(transition: PlayerTransition) {}
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL
}
