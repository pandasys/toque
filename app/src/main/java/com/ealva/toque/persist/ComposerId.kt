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
value class ComposerId(override val value: Long) : PersistentId<ComposerId>, Parcelable {
  companion object {
    val INVALID = ComposerId(PersistentId.ID_INVALID)
  }
}

inline val Long.asComposerId: ComposerId get() = ComposerId(this)

@JvmInline
value class ComposerIdList(val value: LongList) : Iterable<ComposerId> {
  inline val size: Int
    get() = value.size

  inline operator fun plusAssign(genreId: ComposerId) {
    value.add(genreId.value)
  }

  inline operator fun get(index: Int): ComposerId = ComposerId(value.getLong(index))

  companion object {
    operator fun invoke(capacity: Int = 16): ComposerIdList =
      ComposerIdList(LongArrayList(capacity))

    operator fun invoke(composerId: ComposerId): ComposerIdList =
      ComposerIdList(LongLists.singleton(composerId.value))
  }

  override fun iterator(): Iterator<ComposerId> = idIterator(value, ::ComposerId)
}

inline val LongList.asComposerIdList: ComposerIdList get() = ComposerIdList(this)
