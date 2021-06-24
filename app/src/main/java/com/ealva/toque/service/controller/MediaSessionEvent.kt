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

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.service.media.StarRating

sealed interface MediaSessionEvent {
  fun execute(controller: ToqueMediaController)

  object Prepare : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class PrepareFromId(val mediaId: String, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
//    /** Throws IllegalArgumentException if [mediaId] is not of a recognized type */
//    suspend fun accept(visitor: OnMediaType<Unit>) =
//      MediaSessionBrowser.handleMedia(mediaId, extras, visitor)
  }

  data class PrepareFromSearch(val query: String, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class PrepareFromUri(val uri: Uri, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object Play : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class PlayFromId(val mediaId: String, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
//    /** Throws IllegalArgumentException if [mediaId] is not of a recognized type */
//    suspend fun accept(visitor: OnMediaType<Unit>) =
//      MediaSessionBrowser.handleMedia(mediaId, extras, visitor)
  }

  data class PlayFromSearch(val query: String, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class PlayFromUri(val uri: Uri, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class SkipToQueueItem(val index: Long) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object Pause : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      controller.pause()
    }
  }

  object SkipToNext : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object SkipToPrevious : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object FastForward : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object Rewind : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  object Stop : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class SeekTo(val position: Millis) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class SetRating(val rating: StarRating, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class EnableCaption(val enable: Boolean) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class Repeat(val repeatMode: RepeatMode) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class Shuffle(val shuffleMode: ShuffleMode) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class CustomAction(val action: String, val extras: Bundle) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class RemoveItem(val item: MediaDescriptionCompat) : MediaSessionEvent {
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }

  data class AddItemAt(val item: MediaDescriptionCompat, val pos: Int = -1) : MediaSessionEvent {
    @Suppress("unused")
    val addToEnd = pos < 0
    override fun execute(controller: ToqueMediaController) {
      TODO("Not yet implemented")
    }
  }
}
