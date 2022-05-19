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

package com.ealva.toque.ui.library.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.persist.MediaId
import kotlin.time.Duration

@Immutable
interface PlaylistSongInfo : SongInfo {
  val position: Int

  companion object {
    operator fun invoke(
      position: Int,
      id: MediaId,
      title: Title,
      duration: Duration,
      rating: Rating,
      album: AlbumTitle,
      artist: ArtistName,
      artwork: Uri
    ): PlaylistSongInfo = PlaylistSongInfoData(
      position,
      id,
      title,
      duration,
      rating,
      album,
      artist,
      artwork
    )

    @Immutable
    data class PlaylistSongInfoData(
      override val position: Int,
      override val id: MediaId,
      override val title: Title,
      override val duration: Duration,
      override val rating: Rating,
      override val album: AlbumTitle,
      override val artist: ArtistName,
      override val artwork: Uri
    ) : PlaylistSongInfo
  }
}
