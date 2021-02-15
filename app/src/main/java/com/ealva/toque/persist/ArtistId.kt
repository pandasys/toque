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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.persist

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList

inline class ArtistId(override val id: Long) : PersistentId

inline fun Long.toArtistId(): ArtistId = ArtistId(this)
inline fun Int.toArtistId(): ArtistId = toLong().toArtistId()

inline class ArtistIdList(val idList: LongList) : Iterable<ArtistId> {
  inline val size: Int
    get() = idList.size

  inline operator fun plusAssign(artistId: ArtistId) {
    idList.add(artistId.id)
  }

  inline operator fun get(index: Int): ArtistId = ArtistId(idList.getLong(index))

  companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    const val DEFAULT_INITIAL_CAPACITY = 16
    operator fun invoke(capacity: Int = DEFAULT_INITIAL_CAPACITY): ArtistIdList =
      ArtistIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<ArtistId> = idIterator(idList, ::ArtistId)
}
