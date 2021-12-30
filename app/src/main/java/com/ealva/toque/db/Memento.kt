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

package com.ealva.toque.db

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger

private val LOG by lazyLogger(Memento::class)

interface Memento {
  /** True if [undo] or [release] has been called */
  val isReleased: Boolean

  /** Perform the undo operation and release any resources */
  suspend fun undo()

  /**
   * Release resources, undo has not be called.
   */
  suspend fun release()

  companion object {
    val NullMemento = object : Memento {
      override val isReleased: Boolean get() = true
      override suspend fun undo() = Unit
      override suspend fun release() = Unit
      override fun toString(): String = "NullMemento"
    }
  }
}


abstract class BaseMemento : Memento {
  override var isReleased: Boolean = false
  private inline val isNotReleased: Boolean get() = !isReleased

  override suspend fun undo() {
    if (isNotReleased) {
      isReleased = true
      doUndo()
    } else LOG.e { it("Attempt to execute (undo) already released Memento %s", this) }
  }

  protected abstract suspend fun doUndo()

  override suspend fun release() {
    if (isNotReleased) {
      isReleased
      doRelease()
    } else LOG.e { it("Attempt to release already released Memento %s", this) }
  }

  protected open suspend fun doRelease() {}
}
