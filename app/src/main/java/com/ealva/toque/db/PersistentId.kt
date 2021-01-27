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

    val INVALID = object : PersistentId {
      override val id: Long
        get() = ID_INVALID
    }
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

/**
 * Primary for test
 */
inline fun Int.toMediaId() = this.toLong().toMediaId()

inline class MediaId(override val id: Long) : PersistentId {
  companion object {
    val INVALID = MediaId(ID_INVALID)
  }
}

/**
 * Basically a marker interface indicating the instance has a unique, persistent [id].
 * This [id] is typically (always?) a row _id column which guarantees it's uniqueness and
 * persistence.
 */
interface HasId {
  /**
   * Unique persistent ID of instances of this interface
   */
  val id: PersistentId
  /**
   * The "instance" ID is required as some persistent items may appear in lists more than once.
   * This is the unique ID for a particular instance represented by [id]. If duplicate instances
   * will not be needed, this field may be equal to [id]
   */
  val instanceId: Long
}
