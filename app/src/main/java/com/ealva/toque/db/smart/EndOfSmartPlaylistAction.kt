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

package com.ealva.toque.db.smart

import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.MediaIdList

enum class EndOfSmartPlaylistAction(override val id: Int, private val stringRes: Int) : HasConstId {
  Replay(1, R.string.Replay) {
    override suspend fun makeCategoryMediaList(
      categoryToken: CategoryToken,
      getAllMedia: suspend () -> MediaIdList,
      endOfQueue: suspend () -> CategoryMediaList
    ) = CategoryMediaList(getAllMedia(), categoryToken)
  },
  Reshuffle(2, R.string.Reshuffle) {
    override suspend fun makeCategoryMediaList(
      categoryToken: CategoryToken,
      getAllMedia: suspend () -> MediaIdList,
      endOfQueue: suspend () -> CategoryMediaList
    ) = CategoryMediaList(getAllMedia(), categoryToken).shuffled()
  },
  EndOfQueueAction(3, R.string.EndOfQueueAction) {
    override suspend fun makeCategoryMediaList(
      categoryToken: CategoryToken,
      getAllMedia: suspend () -> MediaIdList,
      endOfQueue: suspend () -> CategoryMediaList
    ) = endOfQueue()
  };

  abstract suspend fun makeCategoryMediaList(
    categoryToken: CategoryToken,
    getAllMedia: suspend () -> MediaIdList,
    endOfQueue: suspend () -> CategoryMediaList
  ): CategoryMediaList

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES = values().asList()
  }
}
