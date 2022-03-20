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

package com.ealva.toque.prefs

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.persist.HasConstId

enum class EndOfQueueAction(
  override val id: Int,
  @StringRes val titleRes: Int
) : HasConstId {
  PlayNextList(1, R.string.PlayNextCategory) {
    override suspend fun getNextCategory(
      categoryToken: CategoryToken,
      shuffleMode: ShuffleMode,
      audioMediaDao: AudioMediaDao
    ) = doGetNextCategory(audioMediaDao, categoryToken, shuffleMode)
  },
  ShuffleNextList(2, R.string.ShuffleNextCategory) {
    override suspend fun getNextCategory(
      categoryToken: CategoryToken,
      shuffleMode: ShuffleMode,
      audioMediaDao: AudioMediaDao
    ) = doGetNextCategory(audioMediaDao, categoryToken, shuffleMode.ensureShuffleMedia())
  },
  Stop(3, R.string.Stop) {
    override suspend fun getNextCategory(
      categoryToken: CategoryToken,
      shuffleMode: ShuffleMode,
      audioMediaDao: AudioMediaDao
    ): CategoryMediaList = CategoryMediaList.EMPTY_ALL_LIST
  };

  abstract suspend fun getNextCategory(
    categoryToken: CategoryToken,
    shuffleMode: ShuffleMode,
    audioMediaDao: AudioMediaDao
  ): CategoryMediaList

  protected suspend fun doGetNextCategory(
    audioMediaDao: AudioMediaDao,
    categoryToken: CategoryToken,
    shuffleMode: ShuffleMode
  ) = audioMediaDao
    .getNextCategory(categoryToken, shuffleMode.shuffleLists)
    .maybeShuffle(shuffleMode)
}
