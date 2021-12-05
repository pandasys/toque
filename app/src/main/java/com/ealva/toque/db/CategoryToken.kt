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

package com.ealva.toque.db

import android.os.Parcelable
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.AlbumIdList
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.ComposerIdList
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(CategoryToken::class)

/**
 * Represents an instance of a Category (grouping) of media, such as Album, Artist. One use is
 * to track the last category added to the Up Next queue. This allows movement to "next category"
 * and "previous category".
 */
sealed interface CategoryToken : Parcelable {
  val persistentId: PersistentId
  val songListType: SongListType

  suspend fun write(writer: suspend (SongListType, Long) -> Unit) {
    writer(songListType, persistentId.value)
  }

  suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken
  suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken
  suspend fun getMinToken(audioMediaDao: AudioMediaDao, terminateOn: CategoryToken): CategoryToken
  suspend fun getMaxToken(audioMediaDao: AudioMediaDao, terminateOn: CategoryToken): CategoryToken
  suspend fun getRandom(audioMediaDao: AudioMediaDao): CategoryToken

  suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList

  interface BaseCategoryToken : CategoryToken {
    override suspend fun getRandom(audioMediaDao: AudioMediaDao): CategoryToken =
      makeRandom(audioMediaDao)

    suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken
  }

  @Parcelize
  object All : BaseCategoryToken {
    override val songListType: SongListType
      get() = SongListType.All

    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken =
      throw NotImplementedError("All invalid")

    override val persistentId: PersistentId
      get() = PersistentId.INVALID

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken =
      Album(AlbumId.INVALID).getMinToken(audioMediaDao, terminateOn = this)

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken =
      Playlist(PlaylistId.INVALID).getMaxToken(audioMediaDao, this)

    /** Returns self, prevents infinite loop */
    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = this

    /** Returns self, prevents infinite loop */
    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = this

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      MediaIdList.EMPTY_LIST
  }

  @Parcelize
  private data class Album(
    override val persistentId: AlbumId,
    override val songListType: SongListType = SongListType.Album
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.albumDao.getRandom()) {
        is Ok -> Album(result.value)
        is Err -> Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.albumDao.getNext(persistentId)) {
        is Ok -> Album(result.value)
        is Err -> Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.albumDao.getPrevious(persistentId)) {
        is Ok -> Album(result.value)
        is Err -> Playlist(PlaylistId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Album) All else
      when (val result = audioMediaDao.albumDao.getMin()) {
        is Ok -> Album(result.value)
        is Err -> Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Album) All else
      when (val result = audioMediaDao.albumDao.getMax()) {
        is Ok -> Album(result.value)
        is Err -> Playlist(PlaylistId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      when (val result = audioMediaDao.getMediaForAlbums(AlbumIdList(persistentId))) {
        is Ok -> result.value
        is Err -> MediaIdList.EMPTY_LIST.also { LOG.e { it("%s", result.error) } }
      }
  }

  @Parcelize
  data class Artist(
    override val persistentId: ArtistId,
    override val songListType: SongListType = SongListType.Artist
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.artistDao.getRandom()) {
        is Ok -> Artist(result.value)
        is Err -> Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.artistDao.getNext(persistentId)) {
        is Ok -> Artist(result.value)
        is Err -> Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken =
      when (val result = audioMediaDao.artistDao.getPrevious(persistentId)) {
        is Ok -> Artist(result.value)
        is Err -> Album(AlbumId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else
      when (val result = audioMediaDao.artistDao.getMin()) {
        is Ok -> Artist(result.value)
        is Err -> Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else
      when (val result = audioMediaDao.artistDao.getMax()) {
        is Ok -> Artist(result.value)
        is Err -> Album(AlbumId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      when (val result = audioMediaDao.getMediaForArtists(ArtistIdList(persistentId))) {
        is Ok -> result.value
        is Err -> MediaIdList.EMPTY_LIST.also { LOG.e { it("%s", result.error) } }
      }
  }

  @Parcelize
  data class Composer(
    override val persistentId: ComposerId,
    override val songListType: SongListType = SongListType.Composer
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.composerDao.getRandom()) {
        is Ok -> Composer(result.value)
        is Err -> Genre(GenreId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.composerDao.getNext(persistentId)) {
        is Ok -> Composer(result.value)
        is Err -> Genre(GenreId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.composerDao.getPrevious(persistentId)) {
        is Ok -> Composer(result.value)
        is Err -> Artist(ArtistId.INVALID).getMaxToken(audioMediaDao, this)
      }
    }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Composer) All else
      when (val result = audioMediaDao.composerDao.getMin()) {
        is Ok -> Composer(result.value)
        is Err -> Genre(GenreId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Composer) All else
      when (val result = audioMediaDao.composerDao.getMax()) {
        is Ok -> Composer(result.value)
        is Err -> Artist(ArtistId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList {
      return when (val result = audioMediaDao.getMediaForComposers(ComposerIdList(persistentId))) {
        is Ok -> result.value
        is Err -> MediaIdList.EMPTY_LIST.also { LOG.e { it("%s", result.error) } }
      }
    }
  }

  @Parcelize
  data class Genre(
    override val persistentId: GenreId,
    override val songListType: SongListType = SongListType.Genre
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.genreDao.getRandom()) {
        is Ok -> Genre(result.value)
        is Err -> Playlist(PlaylistId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.genreDao.getNext(persistentId)) {
        is Ok -> Genre(result.value)
        is Err -> Playlist(PlaylistId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.genreDao.getPrevious(persistentId)) {
        is Ok -> Genre(result.value)
        is Err -> Composer(ComposerId.INVALID).getMaxToken(audioMediaDao, this)
      }
    }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Genre) All else
      when (val result = audioMediaDao.genreDao.getMin()) {
        is Ok -> Genre(result.value)
        is Err -> Playlist(PlaylistId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Genre) All else
      when (val result = audioMediaDao.genreDao.getMax()) {
        is Ok -> Genre(result.value)
        is Err -> Composer(ComposerId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList {
      return when (val result = audioMediaDao.getMediaForGenres(GenreIdList(persistentId))) {
        is Ok -> result.value
        is Err -> MediaIdList.EMPTY_LIST.also { LOG.e { it("%s", result.error) } }
      }
    }
  }

  @Parcelize
  data class Playlist(
    override val persistentId: PlaylistId,
    override val songListType: SongListType = SongListType.PlayList
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.playlistDao.getRandom()) {
        is Ok -> Playlist(result.value)
        is Err -> Album(AlbumId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.playlistDao.getNext(persistentId)) {
        is Ok -> Playlist(result.value)
        is Err -> Album(AlbumId.INVALID).getMinToken(audioMediaDao, this)
      }
    }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return when (val result = audioMediaDao.playlistDao.getPrevious(persistentId)) {
        is Ok -> Playlist(result.value)
        is Err -> Genre(GenreId.INVALID).getMaxToken(audioMediaDao, this)
      }
    }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Playlist) All else
      when (val result = audioMediaDao.playlistDao.getMin()) {
        is Ok -> Playlist(result.value)
        is Err -> Album(AlbumId.INVALID).getMinToken(audioMediaDao, this)
      }

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Playlist) All else
      when (val result = audioMediaDao.playlistDao.getMax()) {
        is Ok -> Playlist(result.value)
        is Err -> Genre(GenreId.INVALID).getMaxToken(audioMediaDao, this)
      }

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList {
      return when (
        val result = audioMediaDao.getMediaForPlaylists(
          playlistIds = PlaylistIdList(persistentId),
          removeDuplicates = false // duplicates allowed unknown until added to queue
        )
      ) {
        is Ok -> result.value
        is Err -> MediaIdList.EMPTY_LIST.also { LOG.e { it("%s", result.error) } }
      }
    }
  }

  /**
   * External should behave the same as [All]
   */
  @Parcelize
  object External : BaseCategoryToken {
    override val songListType: SongListType
      get() = SongListType.External

    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken {
      throw NotImplementedError("External invalid")
    }

    override val persistentId: PersistentId
      get() = PersistentId.INVALID

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return All.nextToken(audioMediaDao)
    }

    override suspend fun previousToken(audioMediaDao: AudioMediaDao): CategoryToken {
      return All.previousToken(audioMediaDao)
    }

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = All.getMinToken(audioMediaDao, terminateOn)

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = All.getMaxToken(audioMediaDao, terminateOn)

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      All.getAllMedia(audioMediaDao)
  }

  companion object {
    operator fun invoke(songListType: SongListType, instanceId: Long): CategoryToken =
      make(songListType, instanceId)

    operator fun invoke(albumId: AlbumId): CategoryToken = Album(albumId)
    operator fun invoke(artistId: ArtistId): CategoryToken = Artist(artistId)
    operator fun invoke(composerId: ComposerId): CategoryToken = Composer(composerId)
    operator fun invoke(genreId: GenreId): CategoryToken = Genre(genreId)
    operator fun invoke(playlistId: PlaylistId): CategoryToken = Playlist(playlistId)

    private fun make(songListType: SongListType, id: Long): BaseCategoryToken {
      return when (songListType) {
        SongListType.All -> All
        SongListType.Album -> Album(AlbumId(id), songListType)
        SongListType.Artist -> Artist(ArtistId(id), songListType)
        SongListType.Composer -> Composer(ComposerId(id), songListType)
        SongListType.Genre -> Genre(GenreId(id), songListType)
        SongListType.PlayList -> Playlist(PlaylistId(id), songListType)
        SongListType.External -> External
      }
    }

    suspend fun makeRandom(audioMediaDao: AudioMediaDao): CategoryToken =
      make(SongListType.getRandomType(), PersistentId.ID_INVALID)
        .random(audioMediaDao)
  }
}
