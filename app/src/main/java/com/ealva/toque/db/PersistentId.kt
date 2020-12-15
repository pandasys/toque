/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.db

import com.ealva.ealvabrainz.brainz.data.isInvalid
import com.ealva.ealvabrainz.brainz.data.isValid
import com.ealva.toque.common.debugRequire
import com.ealva.toque.db.PersistentId.Companion.ID_INVALID
import com.ealva.toque.db.PersistentId.Companion.isValidId
import it.unimi.dsi.fastutil.longs.LongList

/**
 * Interface for persistent IDs, used to define [id] property, the constant [ID_INVALID], and
 * extension functions [isValid] and [isInvalid] so expected inline subclasses do not need to define
 * this common functions.
 */
interface PersistentId {
  val id: Long

  companion object {
    const val ID_INVALID = 0L

    inline fun isValidId(id: Long): Boolean = id > ID_INVALID
  }
}

inline val PersistentId.isValid: Boolean
  get() = isValidId(id)

inline val PersistentId.isInvalid: Boolean
  get() = !isValid

inline fun <reified T : PersistentId> idIterator(
  idList: LongList,
  crossinline maker: (Long) -> T
): Iterator<T> {
  val iterator = idList.iterator()
  @Suppress("IteratorNotThrowingNoSuchElementException")
  return object : Iterator<T> {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): T = maker(iterator.nextLong())
  }
}

/**
 * Prefer making a MediaId this way to get a little error checking
 */
inline fun Long.toMediaId(): MediaId {
  debugRequire(isValidId(this)) { "All IDs must be greater than 0 to be valid" }
  return MediaId(this)
}

inline class MediaId(override val id: Long) : PersistentId {
  companion object {
    val INVALID = MediaId(ID_INVALID)
  }
}
