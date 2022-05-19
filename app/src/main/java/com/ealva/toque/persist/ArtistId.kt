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
value class ArtistId(override val value: Long) : PersistentId<ArtistId>, Parcelable {
  companion object {
    val INVALID = ArtistId(PersistentId.ID_INVALID)
  }
}

inline val Long.asArtistId: ArtistId get() = ArtistId(this)

@JvmInline
value class ArtistIdList(val value: LongList) : Iterable<ArtistId> {
  inline val size: Int
    get() = value.size

  inline operator fun plusAssign(artistId: ArtistId) {
    value.add(artistId.value)
  }

  inline operator fun get(index: Int): ArtistId = ArtistId(value.getLong(index))

  companion object {
    inline operator fun invoke(capacity: Int = 16): ArtistIdList =
      ArtistIdList(LongArrayList(capacity))

    operator fun invoke(artistId: ArtistId): ArtistIdList =
      ArtistIdList(LongLists.singleton(artistId.value))
  }

  override fun iterator(): Iterator<ArtistId> = idIterator(value, ::ArtistId)
}

inline val LongList.asArtistIdList: ArtistIdList get() = ArtistIdList(this)
