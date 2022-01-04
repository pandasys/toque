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
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId

data class AudioQueueItemData(
  val id: MediaId,
  val title: Title,
  val albumTitle: AlbumTitle,
  val albumArtist: ArtistName,
  val location: Uri,
  val fileUri: Uri,
  val displayName: String,
  val albumId: AlbumId,
  val artists: Set<ArtistName>,
  val rating: Rating,
  val duration: Millis,
  val trackNumber: Int,
  val localAlbumArt: Uri,
  val albumArt: Uri
)
