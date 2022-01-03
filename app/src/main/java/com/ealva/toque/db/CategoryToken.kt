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
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
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
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.albumDao,
      failureMsg = "Error getting random Album",
      orElse = { Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Album(it) },
      getId = { getRandom() }
    )

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.albumDao,
      failureMsg = "Error getting next Album",
      orElse = { Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Album(it) },
      getId = { getNext(persistentId) }
    )

    override suspend fun previousToken(
      audioMediaDao: AudioMediaDao
    ): CategoryToken = getCategoryToken(
      dao = audioMediaDao.albumDao,
      failureMsg = "Error getting previous Album",
      orElse = { Playlist(PlaylistId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Album(it) },
      getId = { getPrevious(persistentId) }
    )

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Album) All else getCategoryToken(
      dao = audioMediaDao.albumDao,
      failureMsg = "Error getting min Album",
      orElse = { Artist(ArtistId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Album(it) },
      getId = { getMin() }
    )

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Album) All else getCategoryToken(
      dao = audioMediaDao.albumDao,
      failureMsg = "Error getting max Album",
      orElse = { Playlist(PlaylistId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Album(it) },
      getId = { getMax() }
    )

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList = audioMediaDao
      .getMediaForAlbums(AlbumIdList(persistentId))
      .onFailure { cause -> LOG.e(cause) { it("Error getting media for %s.", persistentId) } }
      .getOrElse { MediaIdList.EMPTY_LIST }
  }

  @Parcelize
  data class Artist(
    override val persistentId: ArtistId,
    override val songListType: SongListType = SongListType.Artist
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.artistDao,
      failureMsg = "Error getting random Artist",
      orElse = { Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Artist(it) },
      getId = { getRandom() }
    )

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.artistDao,
      failureMsg = "Error getting next Artist",
      orElse = { Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Artist(it) },
      getId = { getNext(persistentId) }
    )

    override suspend fun previousToken(
      audioMediaDao: AudioMediaDao
    ): CategoryToken = getCategoryToken(
      dao = audioMediaDao.artistDao,
      failureMsg = "Error getting previous Artist",
      orElse = { Album(AlbumId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Artist(it) },
      getId = { getPrevious(persistentId) }
    )

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else getCategoryToken(
      dao = audioMediaDao.artistDao,
      failureMsg = "Error getting min Artist",
      orElse = { Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Artist(it) },
      getId = { getMin() }
    )

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else getCategoryToken(
      dao = audioMediaDao.artistDao,
      failureMsg = "Error getting max Artist",
      orElse = { Album(AlbumId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Artist(it) },
      getId = { getMax() }
    )

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList = audioMediaDao
      .getMediaForArtists(ArtistIdList(persistentId))
      .onFailure { cause -> LOG.e(cause) { it("Error getting media for %s.", persistentId) } }
      .getOrElse { MediaIdList.EMPTY_LIST }
  }

  @Parcelize
  data class Composer(
    override val persistentId: ComposerId,
    override val songListType: SongListType = SongListType.Composer
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.composerDao,
      failureMsg = "Error getting random Composer",
      orElse = { Genre(GenreId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Composer(it) },
      getId = { getRandom() }
    )

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.composerDao,
      failureMsg = "Error getting next Composer",
      orElse = { Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Composer(it) },
      getId = { getNext(persistentId) }
    )

    override suspend fun previousToken(
      audioMediaDao: AudioMediaDao
    ): CategoryToken = getCategoryToken(
      dao = audioMediaDao.composerDao,
      failureMsg = "Error getting previous Composer",
      orElse = { Artist(ArtistId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Composer(it) },
      getId = { getPrevious(persistentId) }
    )

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Composer) All else getCategoryToken(
      dao = audioMediaDao.composerDao,
      failureMsg = "Error getting min Composer",
      orElse = { Genre(GenreId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Composer(it) },
      getId = { getMin() }
    )

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Composer) All else getCategoryToken(
      dao = audioMediaDao.composerDao,
      failureMsg = "Error getting max Composer",
      orElse = { Artist(ArtistId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Composer(it) },
      getId = { getMax() }
    )

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      audioMediaDao.getMediaForComposers(ComposerIdList(persistentId))
        .onFailure { cause -> LOG.e(cause) { it("Error getting media for %s", persistentId) } }
        .getOrElse { MediaIdList.EMPTY_LIST }
  }

  @Parcelize
  data class Genre(
    override val persistentId: GenreId,
    override val songListType: SongListType = SongListType.Genre
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.genreDao,
      failureMsg = "Error getting random Genre",
      orElse = { Playlist(PlaylistId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Genre(it) },
      getId = { getRandom() }
    )

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.genreDao,
      failureMsg = "Error getting next Genre",
      orElse = { Composer(ComposerId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Genre(it) },
      getId = { getNext(persistentId) }
    )

    override suspend fun previousToken(
      audioMediaDao: AudioMediaDao
    ): CategoryToken = getCategoryToken(
      dao = audioMediaDao.genreDao,
      failureMsg = "Error getting previous Genre",
      orElse = { Composer(ComposerId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Genre(it) },
      getId = { getPrevious(persistentId) }
    )

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Genre) All else getCategoryToken(
      dao = audioMediaDao.genreDao,
      failureMsg = "Error getting min Genre",
      orElse = { Playlist(PlaylistId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Genre(it) },
      getId = { getMin() }
    )

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Genre) All else getCategoryToken(
      dao = audioMediaDao.genreDao,
      failureMsg = "Error getting max Genre",
      orElse = { Composer(ComposerId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Genre(it) },
      getId = { getMax() }
    )

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList =
      audioMediaDao.getMediaForGenres(GenreIdList(persistentId))
        .onFailure { cause -> LOG.e(cause) { it("Error getting media for %s", persistentId) } }
        .getOrElse { MediaIdList.EMPTY_LIST }
  }

  @Parcelize
  data class Playlist(
    override val persistentId: PlaylistId,
    override val songListType: SongListType = SongListType.PlayList
  ) : BaseCategoryToken {
    override suspend fun random(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.playlistDao,
      failureMsg = "Error getting random Playlist",
      orElse = { Album(AlbumId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Playlist(it) },
      getId = { getRandom() }
    )

    override suspend fun nextToken(audioMediaDao: AudioMediaDao): CategoryToken = getCategoryToken(
      dao = audioMediaDao.playlistDao,
      failureMsg = "Error getting next Playlist",
      orElse = { Album(AlbumId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Playlist(it) },
      getId = { getNext(persistentId) }
    )

    override suspend fun previousToken(
      audioMediaDao: AudioMediaDao
    ): CategoryToken = getCategoryToken(
      dao = audioMediaDao.playlistDao,
      failureMsg = "Error getting previous Playlist",
      orElse = { Genre(GenreId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Playlist(it) },
      getId = { getPrevious(persistentId) }
    )

    override suspend fun getMinToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else getCategoryToken(
      dao = audioMediaDao.playlistDao,
      failureMsg = "Error getting min Playlist",
      orElse = { Album(AlbumId.INVALID).getMinToken(audioMediaDao, this) },
      tokenMaker = { Playlist(it) },
      getId = { getMin() }
    )

    override suspend fun getMaxToken(
      audioMediaDao: AudioMediaDao,
      terminateOn: CategoryToken
    ): CategoryToken = if (terminateOn is Artist) All else getCategoryToken(
      dao = audioMediaDao.playlistDao,
      failureMsg = "Error getting max Playlist",
      orElse = { Genre(GenreId.INVALID).getMaxToken(audioMediaDao, this) },
      tokenMaker = { Playlist(it) },
      getId = { getMax() }
    )

    override suspend fun getAllMedia(audioMediaDao: AudioMediaDao): MediaIdList = audioMediaDao
      .getMediaForPlaylists(
        playlistIds = PlaylistIdList(persistentId),
        removeDuplicates = false // duplicates allowed unknown until added to queue
      )
      .onFailure { cause -> LOG.e(cause) { it("Error getting media for %s", persistentId) } }
      .getOrElse { MediaIdList.EMPTY_LIST }
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
      make(SongListType.getRandomType(), PersistentId.ID_INVALID).random(audioMediaDao)
  }
}

private inline fun <D, T> getCategoryToken(
  dao: D,
  failureMsg: String,
  orElse: () -> CategoryToken,
  tokenMaker: (T) -> CategoryToken,
  getId: D.() -> DaoResult<T>
): CategoryToken = dao
  .getId()
  .onFailure { cause -> LOG.e(cause) { it(failureMsg) } }
  .map { tokenMaker(it) }
  .getOrElse { orElse() }
