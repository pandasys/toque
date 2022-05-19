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
import com.ealva.toque.db.ComposerDao
import com.ealva.toque.db.ComposerDaoEvent
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.TextSearchType
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import kotlinx.coroutines.flow.SharedFlow
import com.github.michaelbull.result.Result

@Suppress("PropertyName")
class ComposerDaoSpy : ComposerDao {
  override val composerDaoEvents: SharedFlow<ComposerDaoEvent>
    get() = TODO("Not yet implemented")

  override fun TransactionInProgress.deleteAll(): Long {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteComposersWithNoMedia(): Long {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.replaceMediaComposer(
    replaceComposerId: ComposerId,
    replaceMediaId: MediaId,
    createTime: Millis
  ) {
    TODO("Not yet implemented")
  }

  var _getAllComposersCalled: Int = 0
  var _getAllComposersFilter: Filter? = null
  var _getAllComposersLimit: Limit? = null
  var _getAllComposersResult: Result<List<ComposerDescription>, Throwable>? = null
  override suspend fun getAllComposers(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ComposerDescription>> {
    _getAllComposersCalled++
    _getAllComposersFilter = filter
    _getAllComposersLimit = limit
    return checkNotNull(_getAllComposersResult)
  }

  override suspend fun getNext(composerId: ComposerId): DaoResult<ComposerId> {
    TODO("Not yet implemented")
  }

  override suspend fun getPrevious(composerId: ComposerId): DaoResult<ComposerId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMin(): DaoResult<ComposerId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMax(): DaoResult<ComposerId> {
    TODO("Not yet implemented")
  }

  override suspend fun getRandom(): DaoResult<ComposerId> {
    TODO("Not yet implemented")
  }

  override suspend fun getComposerSuggestions(
    partial: String,
    searchType: TextSearchType
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }

  override fun Transaction.replaceComposerMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    TODO("Not yet implemented")
  }
}
