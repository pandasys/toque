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
import kotlinx.parcelize.Parcelize

@Parcelize
@JvmInline
value class AlbumId(override val value: Long) : PersistentId, Parcelable {
  companion object {
    val INVALID = AlbumId(PersistentId.ID_INVALID)
  }
}

inline fun Long.toAlbumId(): AlbumId = AlbumId(this)
inline fun Int.toAlbumId(): AlbumId = toLong().toAlbumId()

@JvmInline
value class AlbumIdList(val prop: LongList) : Iterable<AlbumId> {
  inline val size: Int
    get() = prop.size

  inline operator fun plusAssign(genreId: AlbumId) {
    prop.add(genreId.value)
  }

  inline operator fun get(index: Int): AlbumId = AlbumId(prop.getLong(index))

  companion object {
    inline operator fun invoke(capacity: Int = 16): AlbumIdList =
      AlbumIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<AlbumId> = idIterator(prop, ::AlbumId)
}
