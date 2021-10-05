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

package com.ealva.toque.service.session.common

import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.service.media.PlayState

data class PlaybackState(
  val playState: PlayState,
  val position: Millis,
  val playbackRate: PlaybackRate,
  val itemInstanceId: Long,
  val actions: PlaybackActions
) {
  companion object {
    val NullPlaybackState = PlaybackState(
      playState = PlayState.None,
      position = Millis(0),
      playbackRate = PlaybackRate.NORMAL,
      itemInstanceId = 0L,
      actions = PlaybackActions.DEFAULT
    )
  }
}
