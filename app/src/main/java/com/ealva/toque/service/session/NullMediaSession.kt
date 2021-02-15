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

package com.ealva.toque.service.session

import android.app.PendingIntent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object NullMediaSession : MediaSession {
  override fun setSessionActivity(pi: PendingIntent) = Unit

  override var isActive: Boolean
    get() = false
    set(value) {}

  override fun release() = Unit

  override fun onMediaButton(handler: MediaButtonHandler) = Unit

  override val eventFlow: Flow<MediaSessionEvent>
    get() = flow { }

  override val token: MediaSessionCompat.Token
    get() = TODO("Not yet implemented")

  override fun setState(state: PlaybackStateCompat) = Unit
  override fun setMetadata(metadata: MediaMetadataCompat) = Unit
  override fun setQueue(queue: List<MediaSessionCompat.QueueItem>) = Unit
  override fun setQueueTitle(title: String) = Unit

  override fun setShuffle(shuffleMode: ShuffleMode) {
    TODO("Not yet implemented")
  }

  override fun setRepeat(repeatMode: RepeatMode) {
    TODO("Not yet implemented")
  }

  override val browser: MediaSessionBrowser
    get() = TODO("Not yet implemented")
}
