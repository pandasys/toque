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

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.github.michaelbull.result.Result
import java.io.PrintWriter
import java.io.StringWriter

private fun Throwable.stackTraceToString(): String {
  return StringWriter().apply {
    PrintWriter(this).also { pw ->
      printStackTrace(pw)
    }
  }.toString()
}

typealias DaoResult<T> = Result<T, DaoMessage>
typealias BoolResult = DaoResult<Boolean>
typealias LongResult = DaoResult<Long>

/**
 * A Dao persistent operation failed. See [message] for specifics
 */
class DaoException(msg: String) : RuntimeException(msg)

sealed class DaoMessage

class DaoExceptionMessage(val ex: Throwable) : DaoMessage() {
  override fun toString(): String = """$ex\n${ex.stackTraceToString()}"""
}

abstract class DaoStringMessage(
  @StringRes private val res: Int,
  private vararg val args: Any
) : DaoMessage() {
  override fun toString(): String = fetch(res, args)
}

class DaoNotFound(itemNotFound: Any) : DaoStringMessage(R.string.NotFoundItem, itemNotFound)
class DaoFailedToInsert(item: Any) : DaoStringMessage(R.string.FailedToInsertItem, item)
class DaoFailedToUpdate(item: Any) : DaoStringMessage(R.string.FailedToUpdateItem, item)
class DaoFailedToDelete(item: Any) : DaoStringMessage(R.string.FailedToDeleteItem, item)

object DaoNotImplemented : DaoMessage() {
  override fun toString(): String = fetch(R.string.NotImplemented)
}
