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

package com.ealva.toque.db

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.common.runSuspendCatching
import com.ealva.toque.log.runLogging
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess

typealias PostUndoFunc = () -> Unit

private val LOG by lazyLogger(Memento::class)

interface Memento {
  @Suppress("unused")
  val hasBeenReleased: Boolean

  /**
   * Perform the undo. May only be invoked once. Throws IllegalStateException if already released
   */
  @Throws(IllegalStateException::class)
  suspend fun undo(): BoolResult

  /**
   * Release resources. Call is idempotent.
   */
  suspend fun release()

  /**
   * After the [undo] is executed, calls the [after] lambda if undo was successful. Often the after
   * function will update UI state to indicate an Undo. For example, the user deletes something and
   * is presented with a snackbar, or some other widget that shows an Undo button. If the widget is
   * never pressed, the UI element should call release. If the widget is pressed, undo is called.
   */
  fun postUndo(after: PostUndoFunc)
}

abstract class BaseMemento : Memento {
  private val postList: MutableList<PostUndoFunc> = mutableListOf()
  private var released: Boolean = false

  override val hasBeenReleased: Boolean
    get() = released

  override fun postUndo(after: PostUndoFunc) {
    check(!released) { "Memento is already released" }
    postList += after
  }

  final override suspend fun undo(): BoolResult {
    check(!released) { "Tried to undo a memento already released" }
    val postUndoList = postList.toList() // release clears the list, use a copy
    return runSuspendCatching { doUndo() }
      .mapError { DaoExceptionMessage(it) }
      .releaseWithResult()
      .onSuccess { success ->
        if (success) {
          postUndoList.forEach { postUndo ->
            LOG.runLogging({ "postUndo threw an exception" }) {
              postUndo()
            }
          }
        }
      }
  }

  /**
   * Memento implementations implement this function to perform the actual undo. Undo is often
   * invoked from a snackbar or other type of UI widget. If true is returned the postUndo
   * function(s) is invoked
   */
  protected abstract suspend fun doUndo(): Boolean

  /**
   * Idempotent - only calls [doRelease] 1 time
   */
  final override suspend fun release() {
    Ok(false).releaseWithResult()
  }

  private fun BoolResult.releaseWithResult() = apply {
    try {
      if (!released) {
        released = true
        postList.clear()
        doRelease(this)
      }
    } catch (e: Exception) {
      LOG.e(e) { +it("Exception in doRelease") }
    }
  }

  /**
   * This function is called either after undo or if the memento was released before undo was
   * called. This function will be called only once. Subclasses can implement this function to clean
   * up any resources held by the memento. If result is Ok the boolean represents success if true
   * or, if false, the undo was not attempted. If result is Err the DaoMessage is the error
   * information.
   */
  protected open fun doRelease(result: BoolResult) {}
}
