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
value class PlaylistId(override val value: Long) : PersistentId, Parcelable {
  inline operator fun compareTo(other: Long): Int = value.compareTo(other)

  companion object {
    val INVALID = PlaylistId(PersistentId.ID_INVALID)
  }
}

inline val Long.asPlaylistId: PlaylistId get() = PlaylistId(this)

@JvmInline
value class PlaylistIdList(val value: LongList) : Iterable<PlaylistId> {
  inline val size: Int
    get() = value.size

  inline operator fun plusAssign(artistId: PlaylistId) {
    value.add(artistId.value)
  }

  inline operator fun get(index: Int): PlaylistId = PlaylistId(value.getLong(index))

  companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    const val DEFAULT_INITIAL_CAPACITY = 16
    operator fun invoke(capacity: Int = DEFAULT_INITIAL_CAPACITY): PlaylistIdList =
      PlaylistIdList(LongArrayList(capacity))

    operator fun invoke(playlistId: PlaylistId): PlaylistIdList =
      PlaylistIdList(LongLists.singleton(playlistId.value))
  }

  override fun iterator(): Iterator<PlaylistId> = idIterator(value, ::PlaylistId)
}

inline val LongList.asPlaylistIdList: PlaylistIdList get() = PlaylistIdList(this)
