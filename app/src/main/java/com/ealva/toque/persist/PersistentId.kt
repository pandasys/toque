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
import com.ealva.ealvabrainz.brainz.data.isInvalid
import com.ealva.ealvabrainz.brainz.data.isValid
import com.ealva.toque.persist.PersistentId.Companion.ID_INVALID
import com.ealva.toque.persist.PersistentId.Companion.isValidId
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.Random
import javax.annotation.CheckReturnValue

/**
 * Interface for persistent IDs, used to define [value] property, the constant [ID_INVALID], and
 * extension functions [isValid] and [isInvalid] so expected inline subclasses do not need to define
 * these common functions.
 */
interface PersistentId<T> : Parcelable {
  val value: Long

  @Suppress("UNCHECKED_CAST")
  val actual: T
    get() = this as T

  companion object {
    const val ID_INVALID = -1L
    inline fun isValidId(id: Long): Boolean = id > 0
    val INVALID: PersistentId<*> = InvalidPersistentId

    @Parcelize
    private object InvalidPersistentId : PersistentId<InvalidPersistentId> {
      override val value: Long
        get() = ID_INVALID

    }
  }
}

inline val PersistentId<*>.isValid: Boolean
  get() = isValidId(value)

inline val PersistentId<*>.isInvalid: Boolean
  get() = !isValid

inline fun <reified T : PersistentId<T>> idIterator(
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

inline val Long.asMediaId: MediaId get() = MediaId(this)
inline val Int.asMediaId get() = toLong().asMediaId

@Parcelize
@JvmInline
value class MediaId(override val value: Long) : PersistentId<MediaId>, Parcelable {
  inline operator fun invoke(): Long = value

  companion object {
    val INVALID = MediaId(ID_INVALID)
  }
}

@Parcelize
@JvmInline
value class MediaIdList(val value: @RawValue LongList) : Iterable<MediaId>, Parcelable {
  inline fun isEmpty(): Boolean = size == 0
  inline fun isNotEmpty(): Boolean = !isEmpty()

  inline val size: Int
    get() = value.size

  inline operator fun plusAssign(mediaId: MediaId) {
    value.add(mediaId.value)
  }

  operator fun plus(rhs: MediaIdList): MediaIdList {
    val result = LongArrayList(value.size + rhs.value.size)
    result.addAll(value)
    result.addAll(rhs.value)
    return MediaIdList(result)
  }

  inline operator fun get(index: Int): MediaId = value.getLong(index).asMediaId

  /**
   * If size is > 1 returns a shuffled list, else returns this
   */
  @CheckReturnValue
  fun shuffle(): MediaIdList = if (size > 1) MediaIdList(LongLists.shuffle(value, random)) else this

  override fun iterator(): Iterator<MediaId> = idIterator(value, ::MediaId)

  override fun toString(): String = buildString {
    append("MediaIdList")
    append(value.toString())
  }

  fun removeDuplicates(): MediaIdList = MediaIdList(LongArrayList(LongLinkedOpenHashSet(value)))

  companion object {
    private val random: Random = Random()

    inline operator fun invoke(capacity: Int = 16): MediaIdList =
      MediaIdList(LongArrayList(capacity))

    operator fun invoke(mediaId: MediaId): MediaIdList =
      MediaIdList(LongLists.singleton(mediaId.value))

    val EMPTY_LIST = MediaIdList(LongLists.EMPTY_LIST)
  }
}

inline val LongList.asMediaIdList: MediaIdList get() = MediaIdList(this)

/**
 * Represents a unique instance of an item in a collection. As a collection may contain duplicate
 * items there must be a unique ID of some type to distinguish them in some instances, such
 * as moving items within a list from or from one list to another.
 *
 * Values >= zero are "valid", all negative values are considered "invalid"
 */
@Parcelize
@JvmInline
value class InstanceId(val value: Long) : Parcelable {
  /** True if [value] is >= 0, else false */
  inline val isValid: Boolean get() = value >= 0

  companion object {
    val INVALID = InstanceId(-1L)
  }
}

interface HasPersistentId<T> {
  /**
   * Unique persistent ID of instances of this interface
   */
  val id: PersistentId<T>
}

/**
 * Basically a marker interface indicating the instance has a unique, persistent [id].
 * This [id] is typically (always?) a row _id column which guarantees it's uniqueness and
 * persistence.
 */
interface HasId<T> : HasPersistentId<T> {

  /**
   * The "instance" ID is required as some persistent items may appear in lists more than once.
   * This is the unique ID for a particular instance represented by [id]. If duplicate instances
   * will not be needed, this field may be equal to [id], ie.[PersistentId.value]
   */
  val instanceId: InstanceId
}
