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
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import kotlinx.parcelize.Parcelize
import java.util.Random

@Parcelize
data class AudioIdList(
  val idList: MediaIdList,
  val namedType: NamedSongListType,
) : Parcelable {
  inline val listType: SongListType get() = namedType.listType
  inline val listName: String get() = namedType.listName

  companion object {
    val EMPTY_ALL_LIST = AudioIdList(MediaIdList(LongLists.EMPTY_LIST), NamedSongListType.EMPTY_ALL)

    private val random = Random()

    operator fun invoke(
      mediaId: MediaId,
      listName: String,
      listType: SongListType
    ): AudioIdList = AudioIdList(MediaIdList(mediaId), NamedSongListType(listName, listType))

    operator fun invoke(
      mediaIdList: MediaIdList,
      listName: String,
      listType: SongListType
    ): AudioIdList = AudioIdList(mediaIdList, NamedSongListType(listName, listType))
  }

  /**
   * @return If [idList] size is > 1 returns a copy of this list shuffled, else returns this
   */
  fun shuffled(): AudioIdList = if (idList.size > 1) {
    copy(idList = MediaIdList(LongLists.shuffle(LongArrayList(idList.value), random)))
  } else {
    this
  }

  inline val list: LongList
    get() = idList.value
  inline val size: Int
    get() = idList.size
  inline val isEmpty: Boolean
    get() = idList.isEmpty()
  inline val isNotEmpty: Boolean
    get() = idList.isNotEmpty()
}
