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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.persist

import android.os.Parcelable
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import kotlinx.parcelize.Parcelize

@Parcelize
@JvmInline
value class GenreId(override val value: Long) : PersistentId<GenreId>, Parcelable {
  companion object {
    val INVALID = GenreId(PersistentId.ID_INVALID)
  }
}

inline val Long.asGenreId: GenreId get() = GenreId(this)

@JvmInline
value class GenreIdList(val value: LongList) : Iterable<GenreId> {
  inline val isEmpty: Boolean
    get() = size == 0

  inline val isNotEmpty: Boolean
    get() = !isEmpty

  inline val size: Int
    get() = value.size

  inline operator fun plusAssign(genreId: GenreId) {
    value.add(genreId.value)
  }

  inline operator fun get(index: Int): GenreId = GenreId(value.getLong(index))

  companion object {
    inline operator fun invoke(capacity: Int = 16): GenreIdList =
      GenreIdList(LongArrayList(capacity))

    operator fun invoke(genreId: GenreId): GenreIdList =
      GenreIdList(LongLists.singleton(genreId.value))
  }

  override fun iterator(): Iterator<GenreId> = idIterator(value, ::GenreId)
}

inline val LongList.asGenreIdList: GenreIdList get() = GenreIdList(this)
