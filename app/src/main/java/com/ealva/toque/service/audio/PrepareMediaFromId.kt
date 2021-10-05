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

package com.ealva.toque.service.audio

import android.os.Bundle
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.SongListType
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.session.server.OnMediaType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.coroutines.binding.binding
import it.unimi.dsi.fastutil.longs.LongArrayList

private val LOG by lazyLogger(PrepareMediaFromId::class)

class PrepareMediaFromId(
  private val localAudioQueue: LocalAudioQueue,
  private val audioMediaDao: AudioMediaDao
) : OnMediaType<Unit> {
  override suspend fun onMedia(mediaId: MediaId, extras: Bundle, maxListSize: Long) =
    localAudioQueue.prepareNext(AudioIdList(MediaIdList(mediaId()), SongListType.All, "None"))

  override suspend fun onArtist(artistId: ArtistId, extras: Bundle, maxListSize: Long) {
    when (val result = binding<AudioIdList, DaoMessage> {
      val artistName = audioMediaDao.artistDao.getArtistName(artistId).bind()
      val list = audioMediaDao.getAllAudioFor(artistId, maxListSize).bind()
      AudioIdList(
        MediaIdList(list.mapTo(LongArrayList(list.size)) { it.mediaId.value }),
        SongListType.Artist,
        artistName.value
      )
    }) {
      is Ok -> localAudioQueue.prepareNext(result.value)
      is Err -> LOG.e { it(result.toString()) }
    }
  }

  override suspend fun onAlbum(albumId: AlbumId, extras: Bundle, maxListSize: Long) {
    audioMediaDao.getAllAudioFor(albumId, maxListSize)
  }

  override suspend fun onGenre(genreId: GenreId, extras: Bundle, maxListSize: Long) {
    audioMediaDao.getAllAudioFor(genreId, maxListSize)
  }

  override suspend fun onComposer(composerId: ComposerId, extras: Bundle, maxListSize: Long) {
    audioMediaDao.getAllAudioFor(composerId, maxListSize)
  }

  override suspend fun onPlaylist(playlistId: PlaylistId, extras: Bundle, maxListSize: Long) {
    LOG._e { it("onPlaylist id=%s extras=%s", playlistId, extras) }
  }
}
