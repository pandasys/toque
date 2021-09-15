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
package com.ealva.toque.persist

import android.util.SparseArray
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

private val LOG by lazyLogger(ConstIdValues::class)
private val ENUM_VALUES_MAP: MutableMap<Class<*>, ConstIdValues<*>> = Reference2ObjectOpenHashMap()

/**
 * Return the enum with [id] or [defaultValue] if the enum with [id] could not be found
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> KClass<T>.reify(id: Int, defaultValue: T): T where T : Enum<T>, T : HasConstId {
  return java.reify(id, defaultValue)
}

/**
 * Return the enum with [id] or null if the enum with [id] could not be found
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> KClass<T>.reify(id: Int): T? where T : Enum<T>, T : HasConstId {
  return java.reify(id)
}

inline fun <reified T> Int?.toEnum(defaultValue: T): T where T : Enum<T>, T : HasConstId {
  return if (this == null) defaultValue else defaultValue.javaClass.reify(this, defaultValue)
}

/**
 * Return the enum with [id]. If not found an [IllegalArgumentException] is thrown
 */
@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class)
inline fun <T> KClass<T>.reifyRequire(id: Int): T where T : Enum<T>, T : HasConstId =
  java.reifyRequire(id)

/**
 * Return the enum with [id] or [defaultValue] if the enum with [id] could not be found
 */
fun <T> Class<T>.reify(id: Int, defaultValue: T): T where T : Enum<T>, T : HasConstId {
  return try {
    getEnumValues().fromId(id) ?: defaultValue
  } catch (e: Exception) {
    LOG.e(e) { it("Class=%s", this) }
    defaultValue
  }
}

/**
 * Return the enum with [id] or null if the enum with [id] could not be found
 */
fun <T> Class<T>.reify(id: Int): T? where T : Enum<T>, T : HasConstId = getEnumValues().fromId(id)

/**
 * Return the enum with [id]. If not found an [IllegalArgumentException] is thrown
 */
@Throws(IllegalArgumentException::class)
fun <T> Class<T>.reifyRequire(id: Int): T where T : Enum<T>, T : HasConstId =
  getEnumValues().fromId(id) ?: throw IllegalArgumentException("No such enum $name with id=$id")

/**
 * Map doesn't have computeIfAbsent() hence this dirty version
 */
@Suppress("UNCHECKED_CAST")
private fun <T> Class<T>.getEnumValues(): ConstIdValues<T> where T : Enum<T>, T : HasConstId {
  return (ENUM_VALUES_MAP[this] ?: makeConstIdValues()) as ConstIdValues<T>
}

private fun <T> Class<T>.makeConstIdValues() where T : Enum<T>, T : HasConstId =
  ConstIdValues(enumConstants).also { enumValues -> ENUM_VALUES_MAP[this] = enumValues }

@OptIn(ExperimentalContracts::class)
private inline fun <T : HasConstId> Array<T>?.requireNotEmptyAndDistinct(msg: () -> Any): Array<T> {
  contract {
    returns() implies (this@requireNotEmptyAndDistinct != null)
  }

  if (this == null) throw IllegalArgumentException("Is null, ${msg()}")
  if (isEmpty()) throw IllegalArgumentException("Is empty, ${msg()}")
  if (distinctBy { it.id }.size != size) throw IllegalArgumentException("IDs not unique, ${msg()}")
  return this
}

private const val ITERATIVE_MAX = 8
private const val EMPTY_ENUM_ERROR_MSG =
  "enum must implement HasConstId with unique IDs and define at least 1 constant"

/**
 * Implementations keep a list of an Enum's values and attempt to optimize lookups (find enum
 * instance based on [HasConstId.id]). The value of [HasConstId.id] must never change, however
 * an implementation of this class could be a wrapper around another [ConstIdValues] instance and
 * provide a mapping from an "old" enum instance to a "new" instance.
 */
interface ConstIdValues<T> where T : Enum<T>, T : HasConstId {
  fun fromId(id: Int): T?

  companion object {
    private val VALID_INDEXED_RANGE = 0..32

    operator fun <T> invoke(values: Array<T>?): ConstIdValues<T> where T : Enum<T>, T : HasConstId =
      make(values.requireNotEmptyAndDistinct { EMPTY_ENUM_ERROR_MSG })

    private fun <T> make(values: Array<T>): ConstIdValues<T> where T : Enum<T>, T : HasConstId =
      if (values.allIdsInRange()) EnumValuesIndexed(values) else nonIndexed(values)

    private fun <T> Array<T>.allIdsInRange(): Boolean where T : Enum<T>, T : HasConstId =
      all { it.id in VALID_INDEXED_RANGE }

    private fun <T> nonIndexed(
      values: Array<T>
    ): ConstIdValues<T> where T : Enum<T>, T : HasConstId =
      if (values.size <= ITERATIVE_MAX) EnumValuesIterative(values) else EnumValuesSparse(values)
  }
}

/**
 * This is a fallback from [EnumValuesIndexed]. If the list of enum constants is relatively small,
 * we'll just iterate them
 */
private class EnumValuesIterative<T>(
  private val values: Array<T>
) : ConstIdValues<T> where T : Enum<T>, T : HasConstId {
  override fun fromId(id: Int): T? = values.firstOrNull { value -> value.id == id }
}

/**
 * This is a fallback from [EnumValuesIndexed]. If the list of enum constants is relatively large
 * or one ore more ids are large, we'll use a [SparseArray] which will keep an array of the ids
 * and do a binary search on lookup.
 */
private class EnumValuesSparse<T>(values: Array<T>) :
  ConstIdValues<T> where T : Enum<T>, T : HasConstId {

  private val sparse = SparseArray<T>(values.size).apply {
    values.forEach { value -> put(value.id, value) }
  }

  override fun fromId(id: Int): T? = sparse.get(id, null)
}

/**
 * Keeps an list where the id represents an index, so no searching. May waste a little space
 * but since the ids are programmer assigned, they will typically be small and sequential
 */
private class EnumValuesIndexed<T>(
  values: Array<T>
) : ConstIdValues<T> where T : Enum<T>, T : HasConstId {
  private val listSize = requireNotNull(values.maxByOrNull { it.id }).id + 1
  private val indices = 0 until listSize
  private val list: List<T> = MutableList(listSize) { values[0] }.apply {
    values.forEach { value ->
      require(value.id in indices) { "${value.id} not in $indices" }
      set(value.id, value)
    }
  }

  override fun fromId(id: Int): T? = if (id in indices) list[id] else null
}
