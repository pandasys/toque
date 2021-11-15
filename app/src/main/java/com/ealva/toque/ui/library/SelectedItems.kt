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

package com.ealva.toque.ui.library

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.ealva.toque.common.alsoIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

typealias SelectedItemsFlow<E> = StateFlow<SelectedItems<E>>
typealias MutableSelectedItemsFlow<E> = MutableStateFlow<SelectedItems<E>>

/**
 * Represents items marks as "selected" within another container. This is basically an immutables
 * set of item keys. If a key is in the set [hasSelection] is true and [isSelected] for that
 * particular key is true. As this is a set, no duplicates are allowed. The key class [E] must have
 * correctly defined equals and hashCode functions.
 *
 * As this is immutable, [toggleSelection] returns a new SelectedItems instance
 */
interface SelectedItems<E : Parcelable> : Parcelable {
  /** Does this set contain any keys */
  val hasSelection: Boolean
  /** If this [key] is currently selected, remove it, else add it */
  fun toggleSelection(key: E): SelectedItems<E>
  /** Is this [key] selected */
  fun isSelected(key: E): Boolean

  companion object {
    operator fun <E : Parcelable> invoke(keySet: Set<E> = emptySet()): SelectedItems<E> =
      SelectedItemsImpl(keySet)
  }
}

@Parcelize
private class SelectedItemsImpl<E : Parcelable>(
  private val keySet: Set<E>
) : SelectedItems<E>, Parcelable {
  override val hasSelection: Boolean
    get() = keySet.isNotEmpty()

  override fun toggleSelection(key: E): SelectedItems<E> {
    return SelectedItemsImpl(keySet.removeIfContainsElseAdd(key))
  }

  override fun isSelected(key: E): Boolean = keySet.contains(key)
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SelectedItemsImpl<*>) return false

    if (keySet != other.keySet) return false

    return true
  }

  override fun hashCode(): Int {
    return keySet.hashCode()
  }
}

@Suppress("FunctionName")
fun <E : Parcelable> SelectedItemsFlow(set: SelectedItems<E> = SelectedItems()) =
  MutableStateFlow(set)

fun <E : Parcelable> MutableSelectedItemsFlow<E>.toggleSelection(key: E) {
  value = value.toggleSelection(key)
}

private fun <E> Set<E>.removeIfContainsElseAdd(item: E): Set<E> =
  if (contains(item)) this - item else this + item

/**
 * Collect the SelectedItems as a state value, which is a set of E
 */
@Composable
fun <E : Parcelable> SelectedItemsFlow<E>.asState(
  context: CoroutineContext = EmptyCoroutineContext
): State<SelectedItems<E>> = collectAsState(value, context)

/**
 * If there is a selection (set not empty) then clear the selection
 */
fun <E : Parcelable> MutableSelectedItemsFlow<E>.hasSelectionThenClear(): Boolean =
  hasSelection.alsoIf { value = SelectedItems() }

/**
 * If these is a selection (set not empty), add or remove the [key] as appropriate, else
 * call [block] with the [key]
 */
inline fun <E : Parcelable> MutableSelectedItemsFlow<E>.hasSelectionToggleElse(
  key: E, block: (E) -> Unit
) = if (hasSelection) toggleSelection(key) else block(key)

inline val <E : Parcelable> MutableSelectedItemsFlow<E>.hasSelection: Boolean
  get() = value.hasSelection
