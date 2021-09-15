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

package com.ealva.toque.test.service.audio

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.toMediaId
import com.ealva.toque.service.audio.EqPresetSelector
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItemEvent
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.queue.PlayNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class PlayableAudioItemFake(
  override val eventFlow: Flow<PlayableAudioItemEvent> = emptyFlow(),
  override val id: MediaId = 1.toMediaId(),
  override val instanceId: Long = 1,
  override var title: Title = Title.UNKNOWN,
  override var trackNumber: Int = 1,
  override var duration: Millis = Millis.ONE_HUNDRED,
  override val albumTitle: AlbumTitle = AlbumTitle.UNKNOWN,
  override val albumId: AlbumId = AlbumId.INVALID,
  override val albumArtist: ArtistName = ArtistName.UNKNOWN,
  override val artistSet: Set<ArtistName> = setOf(ArtistName.UNKNOWN),
  override val rating: Rating = Rating.RATING_NONE,
  override val isValid: Boolean = true,
  override val isPlaying: Boolean = false,
  override val isPausable: Boolean = true,
  override val supportsFade: Boolean = false,
  override val isSeekable: Boolean = true,
  override val position: Millis = Millis.ZERO,
  override var volume: Volume = Volume.MAX,
  override var isMuted: Boolean = false,
  override var equalizer: EqPreset = EqPreset.NONE,
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL,
) : PlayableAudioItem {
  override lateinit var location: Uri
  override lateinit var albumArt: Uri
  override lateinit var localAlbumArt: Uri
  override suspend fun play(immediate: Boolean) = Unit
  override fun stop() = Unit
  override fun pause(immediate: Boolean) = Unit
  override suspend fun seekTo(position: Millis) = Unit
  override fun shutdown() = Unit
  override fun shutdown(shutdownTransition: PlayerTransition) = Unit
  override suspend fun reset(presetSelector: EqPresetSelector, playNow: PlayNow, position: Millis) =
    Unit

  override suspend fun prepareSeekMaybePlay(
    position: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused
  ) = Unit

  override fun cloneItem(): PlayableAudioItem = NullPlayableAudioItem
  override suspend fun applyEqualization(
    eqPresetSelector: EqPresetSelector,
    applyEdits: Boolean
  ) = Unit

  override fun checkMarkSkipped() = Unit
  override suspend fun setRating(newRating: Rating) = Unit
  override val artist: ArtistName = ArtistName.UNKNOWN
}
