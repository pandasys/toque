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

package com.ealva.toque.service.queue

import com.ealva.brainzsvc.common.AlbumName
import com.ealva.brainzsvc.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.db.HasId
import com.ealva.toque.db.MediaId

interface QueueMediaItem : HasId {
  override val id: MediaId

  val isValid: Boolean

  val title: Title

  fun getArtist(preferAlbumArtist: Boolean): ArtistName

  val albumName: AlbumName

  val trackNumber: Int

  val duration: Millis

  val position: Millis

  val isPlaying: Boolean
}

object NullQueueMediaItem : QueueMediaItem {
  override val id: MediaId
    get() = MediaId.INVALID

  override val instanceId: Long
    get() = id.id

  override val isValid: Boolean
    get() = false

  override val title: Title
    get() = Title.UNKNOWN

  override fun getArtist(preferAlbumArtist: Boolean): ArtistName = ArtistName.UNKNOWN

  override val albumName: AlbumName
    get() = AlbumName.UNKNOWN

  override val trackNumber: Int
    get() = 0

  override val duration: Millis
    get() = Millis.ZERO

  override val position: Millis
    get() = Millis.ZERO

  override val isPlaying: Boolean
    get() = false
}
