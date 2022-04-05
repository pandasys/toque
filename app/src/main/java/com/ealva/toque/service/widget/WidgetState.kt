/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.service.widget

import android.graphics.Bitmap
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.R
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.fetch

data class WidgetState(
  val iconBitmap: Bitmap?,
  val title: Title,
  val album: AlbumTitle,
  val artist: ArtistName,
  val shuffleMode: ShuffleMode,
  val repeatMode: RepeatMode,
  val isPlaying: Boolean
) {
  companion object {
    val NullWidgetState = WidgetState(
      iconBitmap = null,
      title = Title(""),
      album = AlbumTitle(""),
      artist = ArtistName(""),
      shuffleMode = ShuffleMode.None,
      repeatMode = RepeatMode.None,
      isPlaying = false
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WidgetState) return false

    if (iconBitmap != other.iconBitmap) return false
    if (title != other.title) return false
    if (album != other.album) return false
    if (artist != other.artist) return false
    if (shuffleMode != other.shuffleMode) return false
    if (repeatMode != other.repeatMode) return false
    if (isPlaying != other.isPlaying) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iconBitmap?.hashCode() ?: 0
    result = 31 * result + title.hashCode()
    result = 31 * result + album.hashCode()
    result = 31 * result + artist.hashCode()
    result = 31 * result + shuffleMode.hashCode()
    result = 31 * result + repeatMode.hashCode()
    result = 31 * result + isPlaying.hashCode()
    return result
  }
}
