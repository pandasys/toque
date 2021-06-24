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

@JvmInline
value class ArtistId(override val value: Long) : PersistentId

inline fun Long.toArtistId(): ArtistId = ArtistId(this)
inline fun Int.toArtistId(): ArtistId = toLong().toArtistId()

@JvmInline
value class ArtistIdList(val prop: LongList) : Iterable<ArtistId> {
  inline val size: Int
    get() = prop.size

  inline operator fun plusAssign(artistId: ArtistId) {
    prop.add(artistId.value)
  }

  inline operator fun get(index: Int): ArtistId = ArtistId(prop.getLong(index))

  companion object {
    inline operator fun invoke(capacity: Int = 16): ArtistIdList =
      ArtistIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<ArtistId> = idIterator(prop, ::ArtistId)
}
