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
import com.ealva.toque.common.EntityArtwork
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.persist.MediaId
import kotlin.time.Duration

data class AudioDescription(
  val mediaId: MediaId,
  val title: Title,
  val duration: Duration,
  val rating: Rating,
  val album: AlbumTitle,
  val artist: ArtistName,
  override val localArtwork: Uri,
  override val remoteArtwork: Uri
) : EntityArtwork
