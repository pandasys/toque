/*
 * Copyright 2021 Eric A. Snell
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
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.Title
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.queue.MayFade
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.common.Metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

interface PlayableAudioItem : AudioItem {
  val eventFlow: Flow<PlayableItemEvent>

  val metadata: Metadata

  val position: Millis

  val isPlaying: Boolean

  val isPausable: Boolean

  val supportsFade: Boolean

  var playbackRate: PlaybackRate

  fun play(mayFade: MayFade)

  fun stop()

  fun pause(mayFade: MayFade)

  fun togglePlayPause()

  /**
   * Seek to a position within the valid playback range, which is [Metadata.playbackRange]. If
   * [position] falls outside that range this call is a NoOp.
   */
  fun seekTo(position: Millis)

  fun shutdown()

  fun duck()
  fun endDuck()

  /**
   * Prepare the player, seek to [startPosition] and when the player is "prepared", apply the
   * [onPreparedTransition]. If [playNow] is true, when the player has buffered sufficient media it
   * will auto play.
   *
   * If [startPaused] is true, which currently is always the case, the media and player are set so
   * that an immediate play happens which fills buffers, but doesn't begin actual playback. I think
   * this may have caused some audio glitches in the past - a small "blip" can be heard which causes
   * an apparent glitch in playback. May need to rethink this and only start paused when
   * transition is not immediate
   */
  fun prepareSeekMaybePlay(
    startPosition: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused = StartPaused(true)
  )

  fun cloneItem(): PlayableAudioItem

  fun shutdown(shutdownTransition: PlayerTransition)

  /**
   * Checks the playback position of the item and if it's in the "skipped" range, the skipped count
   * will be incremented and last skipped set to the current time.
   */
  fun checkMarkSkipped()

  /**
   * If rating is coming from an external source (not our app), allowFileUpdate should be false
   * because we may need to ask the user for permission (>=Android 11/SDK R)
   */
  fun setRating(newRating: Rating, allowFileUpdate: Boolean = false)

  fun previousShouldRewind(): Boolean
  fun reset(playNow: PlayNow)

  fun updateArtwork(albumId: AlbumId, albumArt: Uri, localAlbumArt: Uri)

  override val id: MediaId
    get() = metadata.id
  override val title: Title
    get() = metadata.title
  override val albumTitle: AlbumTitle
    get() = metadata.albumTitle
  override val albumArtist: ArtistName
    get() = metadata.albumArtist
  override val artist: ArtistName
    get() = metadata.artistName
  override val trackNumber: Int
    get() = metadata.trackNumber
  override val localAlbumArt: Uri
    get() = metadata.localAlbumArt
  override val albumArt: Uri
    get() = metadata.albumArt
  override val rating: Rating
    get() = metadata.rating
  override val location: Uri
    get() = metadata.location
  override val fileUri: Uri
    get() = metadata.fileUri
}

inline val PlayableAudioItem.isNotValid: Boolean
  get() = !isValid

sealed interface PlayableItemEvent {
  data class Prepared(
    val audioItem: PlayableAudioItem,
    val position: Millis,
    val duration: Millis
  ) : PlayableItemEvent

  data class PositionUpdate(
    val audioItem: PlayableAudioItem,
    val position: Millis,
    val duration: Millis
  ) : PlayableItemEvent

  data class Start(
    val audioItem: PlayableAudioItem,
    val firstStart: Boolean,
    val position: Millis,
  ) : PlayableItemEvent

  data class Paused(val audioItem: PlayableAudioItem, val position: Millis) : PlayableItemEvent
  data class Stopped(val audioItem: PlayableAudioItem) : PlayableItemEvent
  data class PlaybackComplete(val audioItem: PlayableAudioItem) : PlayableItemEvent
  data class Error(val audioItem: PlayableAudioItem) : PlayableItemEvent

  object None : PlayableItemEvent {
    override fun toString(): String = "None"
  }
}

object NullPlayableAudioItem : PlayableAudioItem {
  override val eventFlow: Flow<PlayableItemEvent> = emptyFlow()
  override val metadata: Metadata = Metadata.NullMetadata
  override val isValid: Boolean = false
  override val isPlaying: Boolean = false
  override val isPausable: Boolean = false
  override val supportsFade: Boolean = false
  override fun play(mayFade: MayFade) = Unit
  override fun stop() = Unit
  override fun pause(mayFade: MayFade) = Unit
  override fun togglePlayPause() = Unit
  override fun seekTo(position: Millis) = Unit
  override val position: Millis = Millis(0)
  override val duration: Duration = Duration.ZERO
  override val albumId: AlbumId = AlbumId.INVALID
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL
  override fun shutdown() = Unit
  override fun duck() = Unit
  override fun endDuck() = Unit
  override fun shutdown(shutdownTransition: PlayerTransition) = Unit
  override fun prepareSeekMaybePlay(
    startPosition: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused
  ) = Unit

  override fun cloneItem(): PlayableAudioItem = this
  override fun checkMarkSkipped() = Unit
  override fun setRating(newRating: Rating, allowFileUpdate: Boolean): Unit = Unit
  override fun previousShouldRewind(): Boolean = false
  override fun reset(playNow: PlayNow) = Unit
  override fun updateArtwork(albumId: AlbumId, albumArt: Uri, localAlbumArt: Uri) = Unit
  override val instanceId: InstanceId = InstanceId.INVALID
}
