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

package com.ealva.toque.service.media

import com.ealva.toque.common.Millis
import com.ealva.toque.service.player.PlayerTransition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ParsedStatus(private val status: Int) {
  Unknown(-1),
  NotParsed(0),
  Skipped(1),
  Failed(2),
  Timeout(3),
  Done(4)
}

enum class MediaState(private val state: Int) {
  Unknown(-1),
  NothingSpecial(0),
  Opening(1),
  Playing(3),
  Paused(4),
  Stopped(5),
  Ended(6),
  Error(7)
}

enum class MetadataField(private val field: Int) {
  Unknown(-1),
  Title(0),
  Artist(1),
  Genre(2),
  Copyright(3),
  Album(4),
  TrackNumber(5),
  Description(6),
  Rating(7),
  Date(8),
  Setting(9),
  URL(10),
  Language(11),
  NowPlaying(12),
  Publisher(13),
  EncodedBy(14),
  ArtworkURL(15),
  TrackID(16),
  TrackTotal(17),
  Director(18),
  Season(19),
  Episode(20),
  ShowName(21),
  Actors(22),
  AlbumArtist(23),
  DiscNumber(24)
}

/**  */
sealed class MediaEvent {
  data class MetadataUpdate(val field: MetadataField) : MediaEvent()
}

sealed class MediaPlayerEvent {
  data class Prepared(val currentPosition: Millis, val duration: Millis) : MediaPlayerEvent()
  data class PositionUpdate(val currentPosition: Millis, val duration: Millis) : MediaPlayerEvent()
  data class Start(val firstStart: Boolean) : MediaPlayerEvent()
  data class Paused(val position: Millis) : MediaPlayerEvent()
  object Stopped : MediaPlayerEvent()
  object PlaybackComplete : MediaPlayerEvent()
  object Error : MediaPlayerEvent()
}

interface Media {
  /** If true the media is being streamed via network connection, else is local (on device) */
  val isStream: Boolean

  /** Indicates if the native media has parsed the file tag for metadata */
  val parsedStatus: StateFlow<ParsedStatus>

  val state: StateFlow<MediaState>
  val duration: StateFlow<Millis>
  val mediaEventFlow: Flow<MediaEvent>
  val playerEventFlow: Flow<MediaPlayerEvent>

  /**
   * Prepare the media and play. Media should be constructed with start paused option and this
   * [onPreparedTransition] determines if the media begins playing when it is prepared.
   */
  suspend fun prepareAndPlay(onPreparedTransition: PlayerTransition)
}
