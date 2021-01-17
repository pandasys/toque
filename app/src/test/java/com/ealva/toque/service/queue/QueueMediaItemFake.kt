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

class QueueMediaItemFake(
  override var isValid: Boolean = true,
  override var title: Title = Title.UNKNOWN,
  override var albumName: AlbumName = AlbumName.UNKNOWN,
  override var trackNumber: Int = 1,
  override var duration: Millis = Millis.ONE_HUNDRED,
  override var position: Millis = Millis.ZERO,
  override val isPlaying: Boolean = true
) : QueueMediaItem {
  var _getArtistName: ArtistName = ArtistName.UNKNOWN
  override fun getArtist(preferAlbumArtist: Boolean): ArtistName {
    return _getArtistName
  }
}
