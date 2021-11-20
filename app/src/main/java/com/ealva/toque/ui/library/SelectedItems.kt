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
import androidx.compose.runtime.Immutable
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
 *
 * SelectedItems makes no guarantees on ordering of keys. To maintain order use SelectedItems to
 * filter the original list
 */
@Immutable
interface SelectedItems<E : Parcelable> : Parcelable {
  /** Does this set contain any keys */
  val hasSelection: Boolean

  /**
   * This class enters selection mode during construction, when the first [toggleSelection]
   * returns an instance containing a key. Since this class is immutable, the only way to exit
   * selection mode is via [turnOffSelectionMode] which returns an instance with no keys selected.
   */
  val inSelectionMode: Boolean

  /**
   * Set [inSelectionMode] to false and clear any selection
   */
  fun turnOffSelectionMode(): SelectedItems<E>

  /** If this [key] is currently selected, remove it, else add it */
  fun toggleSelection(key: E): SelectedItems<E>

  /** Clears any selection but does not [turnOffSelectionMode]  */
  fun clearSelection(): SelectedItems<E>

  /** Is this [key] selected */
  fun isSelected(key: E): Boolean

  val selectedCount: Int

  companion object {
    operator fun <E : Parcelable> invoke(keySet: Set<E> = emptySet()): SelectedItems<E> =
      SelectedItemsImpl(keySet = keySet, inSelectionMode = keySet.isNotEmpty())
  }
}

/**
 * Given a sequence of type T and a function [getKey] which can produce a key of type E possibly
 * in [selectedItems], filter this matching only elements whose key appears in [selectedItems].
 * If [selectedItems] has no selection, the original sequence is returned
 */
inline fun <T, E : Parcelable> Sequence<T>.filterWith(
  selectedItems: SelectedItems<E>, crossinline getKey: (T) -> E
): Sequence<T> {
  return if (selectedItems.hasSelection) {
    filter { selectedItems.isSelected(getKey(it)) }
  } else this
}

@Parcelize
private class SelectedItemsImpl<E : Parcelable>(
  private val keySet: Set<E>,
  override val inSelectionMode: Boolean
) : SelectedItems<E>, Parcelable {
  override val hasSelection: Boolean
    get() = keySet.isNotEmpty()

  override fun turnOffSelectionMode(): SelectedItems<E> = SelectedItemsImpl(emptySet(), false)

  override fun toggleSelection(key: E): SelectedItems<E> {
    val newSet = keySet.removeIfContainsElseAdd(key)
    return SelectedItemsImpl(newSet, inSelectionMode || newSet.isNotEmpty())
  }

  override fun clearSelection(): SelectedItems<E> = SelectedItemsImpl(emptySet(), inSelectionMode)

  override fun isSelected(key: E): Boolean = keySet.contains(key)
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SelectedItemsImpl<*>) return false

    if (keySet != other.keySet) return false
    if (inSelectionMode != other.inSelectionMode) return false

    return true
  }

  override fun hashCode(): Int {
    var result = keySet.hashCode()
    result = 31 * result + inSelectionMode.hashCode()
    return result
  }


  override val selectedCount: Int
    get() = keySet.size

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
 * If in selection mode returns true and turns off selection mode. Otherwise returns false.
 * Useful for "onBack"
 */
fun <E : Parcelable> MutableSelectedItemsFlow<E>.inSelectionModeThenTurnOff(): Boolean =
  inSelectionMode.alsoIf { turnOffSelectionMode() }


/** Clear any selection. Doesn't turn off selection mode */
fun <E : Parcelable> MutableSelectedItemsFlow<E>.clearSelection() {
  value = value.clearSelection()
}

/** Clears any selection and turns off selection mode */
fun <E : Parcelable> MutableSelectedItemsFlow<E>.turnOffSelectionMode() {
  value = value.turnOffSelectionMode()
}

/**
 * If in selection mode, toggle the selection of [key], else call [block] with
 * the [key]. Useful to toggle selection when in selection mode else perform an action
 */
inline fun <E : Parcelable> MutableSelectedItemsFlow<E>.ifInSelectionModeToggleElse(
  key: E, block: (E) -> Unit
) = if (inSelectionMode) toggleSelection(key) else block(key)

inline val <E : Parcelable> MutableSelectedItemsFlow<E>.inSelectionMode: Boolean
  get() = value.inSelectionMode

/** Creates a SelectedItems with every item in [keySet] selected and sets that instance. */
fun <E : Parcelable> MutableSelectedItemsFlow<E>.selectAll(keySet: Set<E>) {
  value = SelectedItems(keySet)
}
