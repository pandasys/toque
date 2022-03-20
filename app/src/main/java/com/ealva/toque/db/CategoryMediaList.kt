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
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import it.unimi.dsi.fastutil.longs.LongList
import kotlinx.parcelize.Parcelize
import javax.annotation.CheckReturnValue

/**
 * A list of media associated with a particular Category. If the list contains media from multiple
 * instances of a Category, eg. multiple Albums, multiple Playlists, etc, the [token] should
 * represent the last Category instance added to the list.
 *
 * For example, if there are Albums A-Z and the media for Albums B, E, and S are added to a
 * CategoryMediaList, the last CategoryToken would represent the S Album. Navigation to the
 * next category would result in Album T.
 *
 * Category representing media added from All media or External media, should navigate
 * to the first instance of the first category (currently Album).
 */
@Parcelize
data class CategoryMediaList(val idList: MediaIdList, val token: CategoryToken) : Parcelable {
  companion object {
    val EMPTY_ALL_LIST = CategoryMediaList(MediaIdList.EMPTY_LIST, CategoryToken.All)

    operator fun invoke(
      mediaId: MediaId,
      categoryToken: CategoryToken
    ): CategoryMediaList = CategoryMediaList(MediaIdList(mediaId), categoryToken)
  }

  /**
   * If size is 1 or 0 returns self, otherwise returns a copy of this with [idList] shuffled.
   */
  @CheckReturnValue
  fun shuffled(): CategoryMediaList = if (size < 2) this else copy(idList = idList.shuffle())

  /**
   * Returns a shuffled version of this depending on [shuffleMode]
   */
  @CheckReturnValue
  fun maybeShuffle(shuffleMode: ShuffleMode): CategoryMediaList =
    if (shuffleMode.shuffleMedia.value) shuffled() else this

  inline val list: LongList get() = idList.value
  inline val size: Int get() = idList.size
  inline val isEmpty: Boolean get() = idList.isEmpty()
  inline val isNotEmpty: Boolean get() = idList.isNotEmpty()
}
