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

package com.ealva.toque.service.controller

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.service.media.StarRating

sealed interface SessionControlEvent {

  object Prepare : SessionControlEvent {
    override fun toString(): String = "Prepare"
  }

  data class PrepareFromId(val mediaId: String, val extras: Bundle) : SessionControlEvent

  data class PrepareFromSearch(val query: String, val extras: Bundle) : SessionControlEvent

  data class PrepareFromUri(val uri: Uri, val extras: Bundle) : SessionControlEvent

  object Play : SessionControlEvent {
    override fun toString(): String = "Play"
  }

  data class PlayFromId(val mediaId: String, val extras: Bundle) : SessionControlEvent

  data class PlayFromSearch(val query: String, val extras: Bundle) : SessionControlEvent

  data class PlayFromUri(val uri: Uri, val extras: Bundle) : SessionControlEvent

  /**
   * When a list of QueueItem
   */
  data class SkipToQueueItem(val instanceId: InstanceId) : SessionControlEvent

  object Pause : SessionControlEvent {
    override fun toString(): String = "Pause"
  }

  object SkipToNext : SessionControlEvent {
    override fun toString(): String = "SkipToNext"
  }

  object SkipToPrevious : SessionControlEvent {
    override fun toString(): String = "SkipToPrevious"
  }

  object FastForward : SessionControlEvent {
    override fun toString(): String = "FastForward"
  }

  object Rewind : SessionControlEvent {
    override fun toString(): String = "Rewind"
  }

  object Stop : SessionControlEvent {
    override fun toString(): String = "Stop"
  }

  data class SeekTo(val position: Millis) : SessionControlEvent

  data class SetRating(val rating: StarRating, val extras: Bundle) : SessionControlEvent

  data class EnableCaption(val enable: Boolean) : SessionControlEvent

  data class Repeat(val repeatMode: RepeatMode) : SessionControlEvent

  data class Shuffle(val shuffleMode: ShuffleMode) : SessionControlEvent

  data class CustomAction(val action: String, val extras: Bundle) : SessionControlEvent

  data class RemoveItem(val item: MediaDescriptionCompat) : SessionControlEvent

  data class AddItemAt(val item: MediaDescriptionCompat, val pos: Int = -1) : SessionControlEvent {
    @Suppress("unused")
    val addToEnd = pos < 0
  }
}
