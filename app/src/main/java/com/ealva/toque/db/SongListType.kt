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

import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.toque.persist.HasConstId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

private val LOG by lazyLogger(SongListType::class)

@Suppress("unused")
private val allOrderByList: List<OrderByItem> by lazy {
  listOf(MediaTable.TITLE_ORDER)
}

/**
 * Don't ever change [id] or lists will be lost. New [id]s maybe added. A new [id] can be added and
 * mapped to from an old [id] if that ever becomes necessary.
 */
enum class SongListType(override val id: Int) : HasConstId {
  All(1) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String) =
      nextType().getNextList(dao, "")

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String) =
      previousType().getPreviousList(dao, "")

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList {
      LOG.e { it("SongListType.All.doGetRandomList() This method should not have been called!") }
      return nextType().getRandomList(dao)
    }
  },
  Album(2) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetNextList(dao) { dao.getNextAlbumList(AlbumTitle(name)) }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetPrevList(dao) { dao.getPreviousAlbumList(AlbumTitle(name)) }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList =
      doGetRandomList(dao) { dao.getRandomAlbumList() }
  },
  Artist(3) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetNextList(dao) { dao.getNextArtistList(ArtistName(name)) }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetPrevList(dao) { dao.getPreviousArtistList(ArtistName(name)) }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList =
      doGetRandomList(dao) { dao.getRandomArtistList() }
  },
  Composer(4) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetNextList(dao) { dao.getNextComposerList(ComposerName(name)) }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetPrevList(dao) { dao.getPreviousComposerList(ComposerName(name)) }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList =
      doGetRandomList(dao) { dao.getRandomComposerList() }
  },
  Genre(5) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetNextList(dao) { dao.getNextGenreList(GenreName(name)) }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList =
      doGetPrevList(dao) { dao.getPreviousGenreList(GenreName(name)) }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList =
      doGetRandomList(dao) { dao.getRandomGenreList() }
  },
  PlayList(7) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList {
      return nextType().getNextList(dao, "")
    }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList {
      return previousType().getPreviousList(dao, "")
    }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList {
      return nextType().getRandomList(dao)
    }
  },
  SmartPlaylist(8) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList {
      return nextType().getNextList(dao, "")
    }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList {
      return previousType().getPreviousList(dao, "")
    }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList {
      return nextType().getRandomList(dao)
    }
  },
  @Suppress("unused") External(9) {
    override suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList {
      return nextType().getNextList(dao, "")
    }

    override suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList {
      return previousType().getPreviousList(dao, "")
    }

    override suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList {
      return nextType().getRandomList(dao)
    }
  };

  abstract suspend fun getNextList(dao: AudioMediaDao, name: String): AudioIdList
  abstract suspend fun getPreviousList(dao: AudioMediaDao, name: String): AudioIdList
  protected abstract suspend fun doGetRandomList(dao: AudioMediaDao): AudioIdList

  suspend fun getRandomList(dao: AudioMediaDao): AudioIdList = doGetRandomList(dao)

  /** Public for test - should be package private :) */
  fun nextType(): SongListType = when (val nextIndex = generatingLists.indexOf(this) + 1) {
    in generatingLists.indices -> generatingLists[nextIndex]
    else -> generatingLists.first()
  }

  /** Public for test - should be package private :) */
  fun previousType(): SongListType = when (val nextIndex = generatingLists.indexOf(this) - 1) {
    in generatingLists.indices -> generatingLists[nextIndex]
    else -> generatingLists.last()
  }

  protected suspend inline fun doGetNextList(
    dao: AudioMediaDao,
    fetchList: () -> Result<AudioIdList, DaoMessage>
  ): AudioIdList = when (val result = fetchList()) {
    is Ok -> result.value
    is Err -> nextType().getNextList(dao, "")
  }

  protected suspend inline fun doGetPrevList(
    dao: AudioMediaDao,
    fetchList: () -> Result<AudioIdList, DaoMessage>
  ): AudioIdList = when (val result = fetchList()) {
    is Ok -> result.value
    is Err -> previousType().getPreviousList(dao, "")
  }

  protected suspend inline fun doGetRandomList(
    dao: AudioMediaDao,
    fetchList: () -> Result<AudioIdList, DaoMessage>
  ): AudioIdList = when (val result = fetchList()) {
    is Ok -> result.value
    is Err -> nextType().getRandomList(dao)
  }

  companion object {
    /**
     * Items in this list all possibly generate audio lists and are ordered for next/previous
     * selection.
     */
    val generatingLists = listOf(
      Album,
      Artist,
      Composer,
      Genre,
      //PlayList,  TODO Add these back in when they are producing items
      //SmartPlaylist
    )

    fun getRandomType(): SongListType = generatingLists.random().also { type ->
      LOG._e { it("getRandomType=%s", type) }
    }
  }
}
