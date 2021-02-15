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

sealed class MediaEvent {
  data class MetadataUpdate(val metadata: MediaMetadata) : MediaEvent()
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
  val mediaEventFlow: Flow<MediaEvent>
  val playerEventFlow: Flow<MediaPlayerEvent>

  /**
   * Prepare the media and play. Media should be constructed with start paused option and this
   * [onPreparedTransition] determines if the media begins playing when it is prepared.
   */
  suspend fun prepareAndPlay(onPreparedTransition: PlayerTransition)
}
