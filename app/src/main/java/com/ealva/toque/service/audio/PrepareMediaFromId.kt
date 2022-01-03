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

package com.ealva.toque.service.audio

import android.os.Bundle
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Limit
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.session.server.OnMediaType
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import it.unimi.dsi.fastutil.longs.LongArrayList

private val LOG by lazyLogger(PrepareMediaFromId::class)

class PrepareMediaFromId(
  private val localAudioQueue: LocalAudioQueue,
  private val audioMediaDao: AudioMediaDao
) : OnMediaType<Unit> {
  override suspend fun onMedia(mediaId: MediaId, extras: Bundle, limit: Limit) =
    localAudioQueue.prepareNext(CategoryMediaList(mediaId, CategoryToken.External))

  override suspend fun onArtist(artistId: ArtistId, extras: Bundle, limit: Limit) {
    audioMediaDao.getArtistAudio(artistId, limit = limit)
      .map { list ->
        CategoryMediaList(
          MediaIdList(list.mapTo(LongArrayList(list.size)) { it.mediaId.value }),
          CategoryToken(artistId)
        )
      }
      .onSuccess { localAudioQueue.prepareNext(it) }
      .onFailure { cause -> LOG.e(cause) { it("Error getArtistAudio") } }
  }

  override suspend fun onAlbum(albumId: AlbumId, extras: Bundle, limit: Limit) {
    audioMediaDao.getAlbumAudio(albumId, null, limit = limit)
  }

  override suspend fun onGenre(genreId: GenreId, extras: Bundle, limit: Limit) {
    audioMediaDao.getGenreAudio(genreId, limit = limit)
  }

  override suspend fun onComposer(composerId: ComposerId, extras: Bundle, limit: Limit) {
    audioMediaDao.getComposerAudio(composerId = composerId, limit = limit)
  }

  override suspend fun onPlaylist(playlistId: PlaylistId, extras: Bundle, limit: Limit) {
    LOG._e { it("onPlaylist id=%s extras=%s", playlistId, extras) }
  }
}
