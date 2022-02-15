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

package com.ealva.toque.db

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.Title
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.MediaId

@Immutable
data class FullAudioInfo(
  val mediaId: MediaId,
  val title: Title,
  val album: AlbumTitle,
  val albumArtist: ArtistName,
  val songArtist: ArtistName,
  val genre: GenreName,
  val track: Int,
  val totalTracks: Int,
  val disc: Int,
  val totalDiscs: Int,
  val duration: Millis,
  val year: Int,
  val composer: ComposerName,
  val rating: StarRating,
  val playCount: Int,
  val lastPlayed: Millis,
  val skippedCount: Int,
  val lastSkipped: Millis,
  val dateAdded: Millis,
  val location: Uri,
  val file: Uri,
  val albumId: AlbumId,
  val albumArtistId: ArtistId,
  val albumArt: Uri,
  val localAlbumArt: Uri
)
