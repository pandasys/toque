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

package com.ealva.toque.db

import android.os.Parcel
import android.os.Parcelable
import com.ealva.toque.persist.MediaIdList
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import java.io.Serializable
import java.util.Random

data class AudioIdList(
  val idList: MediaIdList,
  val listType: SongListType,
  val listName: String
) : Parcelable {
  companion object {
    @Suppress("unused") // not true
    @JvmField
    val CREATOR = createParcel { AudioIdList(it) }
    private val random = Random()
  }

  private constructor(parcelIn: Parcel) : this(
    MediaIdList(parcelIn.readSerializable() as LongList),
    parcelIn.readSerializable() as SongListType,
    parcelIn.readString() ?: ""
  )

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeSerializable(idList.value as Serializable)
    dest.writeSerializable(listType)
    dest.writeString(listName)
  }

  override fun describeContents() = 0

  /**
   * @return If [idList] size is > 1 returns a copy of this list shuffled, else returns this
   */
  fun shuffled(): AudioIdList {
    return if (idList.size > 1) {
      this.copy(idList = MediaIdList(LongLists.shuffle(LongArrayList(idList.value), random)))
    } else {
      this
    }
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

val EMPTY_MEDIA_ID_LIST = AudioIdList(MediaIdList(LongLists.EMPTY_LIST), SongListType.All, "")
