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

package com.ealva.toque.persist

import android.os.Parcelable
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import kotlinx.parcelize.Parcelize

@Parcelize
@JvmInline
value class AlbumId(override val value: Long) : PersistentId, Parcelable {
  companion object {
    val INVALID = AlbumId(PersistentId.ID_INVALID)
  }
}

inline val Long.asAlbumId: AlbumId get() = AlbumId(this)

@JvmInline
value class AlbumIdList(val value: LongList) : Iterable<AlbumId> {
  inline val size: Int get() = value.size

  inline val isNotEmpty: Boolean get() = value.size > 0

  operator fun plusAssign(genreId: AlbumId) {
    value.add(genreId.value)
  }

  operator fun get(index: Int): AlbumId = AlbumId(value.getLong(index))

  companion object {
    operator fun invoke(capacity: Int = 16): AlbumIdList =
      AlbumIdList(LongArrayList(capacity))

    operator fun invoke(albumId: AlbumId): AlbumIdList =
      AlbumIdList(LongLists.singleton(albumId.value))
  }

  override fun iterator(): Iterator<AlbumId> = idIterator(value, ::AlbumId)
}

inline val LongList.asAlbumIdList: AlbumIdList get() = AlbumIdList(this)
