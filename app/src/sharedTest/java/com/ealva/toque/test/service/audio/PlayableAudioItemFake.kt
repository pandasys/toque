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

package com.ealva.toque.test.service.audio

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.Title
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.toMediaId
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.PlayableItemEvent
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.queue.ForceTransition
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.common.Metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class PlayableAudioItemFake(
  override val eventFlow: Flow<PlayableItemEvent> = emptyFlow(),
  override val id: MediaId = 1.toMediaId(),
  override val instanceId: InstanceId = InstanceId(1),
  override var title: Title = Title.UNKNOWN,
  override var trackNumber: Int = 1,
  override var duration: Millis = Millis(100),
  override val albumTitle: AlbumTitle = AlbumTitle.UNKNOWN,
  override val albumArtist: ArtistName = ArtistName.UNKNOWN,
  override val rating: Rating = Rating.RATING_NONE,
  override val isValid: Boolean = true,
  override val isPlaying: Boolean = false,
  override val isPausable: Boolean = true,
  override val supportsFade: Boolean = false,
  override val position: Millis = Millis(0),
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL,
  override var fileUri: Uri = Uri.EMPTY
) : PlayableAudioItem {
  override lateinit var location: Uri
  override lateinit var albumArt: Uri
  override lateinit var localAlbumArt: Uri
  override val metadata: Metadata = Metadata.NullMetadata
  override fun play(forceTransition: ForceTransition) = Unit
  override fun stop() = Unit
  override fun pause(forceTransition: ForceTransition) = Unit
  override fun togglePlayPause() = Unit
  override fun seekTo(position: Millis) = Unit
  override fun shutdown() = Unit
  override fun duck() = Unit
  override fun endDuck() = Unit
  override fun shutdown(shutdownTransition: PlayerTransition) = Unit
  override fun prepareSeekMaybePlay(
    startPosition: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    timePlayed: Millis,
    countFrom: Millis,
    startPaused: StartPaused
  ) = Unit

  override fun cloneItem(): PlayableAudioItem = NullPlayableAudioItem

  override fun checkMarkSkipped() = Unit
  override fun setRating(newRating: Rating, allowFileUpdate: Boolean) = Unit
  override fun previousShouldRewind(): Boolean = false
  override fun reset(playNow: PlayNow) = Unit

  override val artist: ArtistName = ArtistName.UNKNOWN
}
