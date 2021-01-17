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

package com.ealva.toque.service.controller

interface ToqueMediaController {
  fun play(immediate: Boolean = false)

  /**
   * Stop the current media item.
   */
  fun stop()

  /**
   * Pause current media item
   */
  fun pause(immediate: Boolean = false)

  /**
   * If toggle state from play to pause
   */
  fun togglePlayPause()

  /**
   * Go to next media item. If streaming then there is no next media item.
   */
  fun nextSong()

  /**
   * Go to previous media item. If streaming there is no previous media item.
   */
  fun previousSong()

  /** Go to the index of the up next queue and play if currently playing */
  fun goToQueueIndexMaybePlay(index: Int)
}
