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

package com.ealva.toque.service.audio

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.queue.PlayNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface PlayableAudioItem : AudioItem, HasId {
  val eventFlow: Flow<PlayableAudioItemEvent>

  val albumId: AlbumId
  val artistSet: Set<ArtistName>
  val position: Millis

  val isValid: Boolean

  val isPlaying: Boolean

  val isPausable: Boolean

  val isSeekable: Boolean

  val supportsFade: Boolean

  override val duration: Millis

  var volume: Volume

  var isMuted: Boolean

  /**
   * Directly set an Eq preset for this media regardless of any association the user has made. This
   * is useful for both editing of presets and is also currently possible from Now Playing
   */
  var equalizer: EqPreset

  var playbackRate: PlaybackRate

  suspend fun play(immediate: Boolean = false)

  fun stop()

  fun pause(immediate: Boolean = false)

  suspend fun seekTo(position: Millis)

  fun shutdown()

  suspend fun reset(presetSelector: EqPresetSelector, playNow: PlayNow, position: Millis)

  /**
   * Prepare the player, seek to [position] and when the player is "prepared", apply the
   * [onPreparedTransition]. If [playNow] is true, when the player has buffered sufficient media is
   * will auto play.
   *
   * If [startPaused] is true, which currently is always the case, the media and player are set so
   * that an immediate play happens which fill buffers, but doesn't begin actual playback.
   */
  suspend fun prepareSeekMaybePlay(
    position: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused = StartPaused(true)
  )

  fun cloneItem(): PlayableAudioItem

  fun shutdown(shutdownTransition: PlayerTransition)

  suspend fun applyEqualization(eqPresetSelector: EqPresetSelector, applyEdits: Boolean)

  /**
   * Checks the playback position of the item and if it's in the "skipped" range, the skipped count
   * will be incremented and last skipped set to the current time.
   */
  fun checkMarkSkipped()

  suspend fun setRating(newRating: Rating)
}

inline val PlayableAudioItem.isNotValid: Boolean
  get() = !isValid

sealed interface PlayableAudioItemEvent {
  data class Prepared(
    val audioItem: PlayableAudioItem,
    val currentPosition: Millis,
    val duration: Millis
  ) : PlayableAudioItemEvent

  data class PositionUpdate(
    val audioItem: PlayableAudioItem,
    val currentPosition: Millis,
    val duration: Millis
  ) : PlayableAudioItemEvent

  data class Start(
    val audioItem: PlayableAudioItem,
    val firstStart: Boolean,
    val currentPosition: Millis,
  ) : PlayableAudioItemEvent

  data class Paused(
    val audioItem: PlayableAudioItem,
    val currentPosition: Millis
  ) : PlayableAudioItemEvent

  data class Stopped(
    val audioItem: PlayableAudioItem,
    val currentPosition: Millis
  ) : PlayableAudioItemEvent

  data class PlaybackComplete(val audioItem: PlayableAudioItem) : PlayableAudioItemEvent
  data class Error(val audioItem: PlayableAudioItem) : PlayableAudioItemEvent
  object None : PlayableAudioItemEvent {
    override fun toString(): String = "None"
  }
}

object NullPlayableAudioItem : PlayableAudioItem {
  override val eventFlow: Flow<PlayableAudioItemEvent> = emptyFlow()
  override val isValid: Boolean = false
  override val isPlaying: Boolean = false
  override val isPausable: Boolean = false
  override val supportsFade: Boolean = false
  override suspend fun play(immediate: Boolean) = Unit
  override fun stop() = Unit
  override fun pause(immediate: Boolean) = Unit
  override val isSeekable: Boolean = false
  override suspend fun seekTo(position: Millis) = Unit
  override val position: Millis = Millis.ZERO
  override val duration: Millis = Millis.ZERO
  override var volume: Volume = Volume.MAX
  override var isMuted: Boolean = false
  override var equalizer: EqPreset = EqPreset.NONE
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL
  override fun shutdown() = Unit
  override fun shutdown(shutdownTransition: PlayerTransition) = Unit
  override suspend fun reset(
    presetSelector: EqPresetSelector,
    playNow: PlayNow,
    position: Millis
  ) = Unit

  override suspend fun prepareSeekMaybePlay(
    position: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused
  ) = Unit

  override fun cloneItem(): PlayableAudioItem = this
  override suspend fun applyEqualization(
    eqPresetSelector: EqPresetSelector,
    applyEdits: Boolean
  ) = Unit

  override fun checkMarkSkipped() = Unit
  override suspend fun setRating(newRating: Rating): Unit = Unit
  override val id: MediaId = MediaId.INVALID
  override val location: Uri
    get() = Uri.EMPTY
  override val title: Title = Title.UNKNOWN
  override val albumTitle: AlbumTitle = AlbumTitle.UNKNOWN
  override val albumId: AlbumId = AlbumId.INVALID
  override val albumArtist: ArtistName = ArtistName.UNKNOWN
  override val artist: ArtistName = ArtistName.UNKNOWN
  override val artistSet: Set<ArtistName> = setOf(ArtistName.UNKNOWN)
  override val rating: Rating = Rating.RATING_NONE
  override val trackNumber: Int = 1
  override val localAlbumArt: Uri = Uri.EMPTY
  override val albumArt: Uri = Uri.EMPTY
  override val instanceId: Long = 0
}
