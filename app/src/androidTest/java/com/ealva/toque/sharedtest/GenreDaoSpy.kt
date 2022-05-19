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

package com.ealva.toque.sharedtest

import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.AudioUpsertResults
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.GenreDaoEvent
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.db.GenreIdName
import com.ealva.toque.db.TextSearchType
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import kotlinx.coroutines.flow.SharedFlow
import com.github.michaelbull.result.Result

@Suppress("PropertyName")
class GenreDaoSpy : GenreDao {
  override val genreDaoEvents: SharedFlow<GenreDaoEvent>
    get() = TODO("Not yet implemented")

  override fun TransactionInProgress.deleteAll(): Long {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteGenresNotAssociateWithMedia(): Long {
    TODO("Not yet implemented")
  }

  var _getAllGenresCalled: Int = 0
  var _getAllGenresFilter: Filter? = null
  var _getAllGenresLimit: Limit? = null
  var _getAllGenresResult: Result<List<GenreDescription>, Throwable>? = null
  override suspend fun getAllGenres(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<GenreDescription>> {
    _getAllGenresCalled++
    _getAllGenresFilter = filter
    _getAllGenresLimit = limit
    return checkNotNull(_getAllGenresResult)
  }

  override suspend fun getAllGenreNames(limit: Limit): DaoResult<List<GenreIdName>> {
    TODO("Not yet implemented")
  }

  override suspend fun getNext(genreId: GenreId): DaoResult<GenreId> {
    TODO("Not yet implemented")
  }

  override suspend fun getPrevious(genreId: GenreId): DaoResult<GenreId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMin(): DaoResult<GenreId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMax(): DaoResult<GenreId> {
    TODO("Not yet implemented")
  }

  override suspend fun getRandom(): DaoResult<GenreId> {
    TODO("Not yet implemented")
  }

  override suspend fun getGenreSuggestions(
    partial: String,
    searchType: TextSearchType
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }

  override fun Transaction.replaceGenreMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    TODO("Not yet implemented")
  }
}
