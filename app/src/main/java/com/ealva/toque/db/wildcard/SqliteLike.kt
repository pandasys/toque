/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.db.wildcard

import com.ealva.toque.common.Filter
import com.ealva.toque.common.asFilter
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.notLike

@Suppress("MemberVisibilityCanBePrivate")
object SqliteLike {
  const val ESC_CHAR = '\\'
  const val GREEDY_WILDCARD_CHAR = '%'
  const val SINGLE_WILDCARD_CHAR = '_'
  const val GREEDY_WILDCARD = "%"

  /**
   * If this is null or blank returns an empty string, else trims this string, wraps with [prefix]
   * and [postfix], and escapes wildcards or the [ESC_CHAR]
   */
  fun String?.wrapForLike(
    prefix: CharSequence = GREEDY_WILDCARD,
    postfix: CharSequence = GREEDY_WILDCARD
  ): String = if (isNullOrBlank()) "" else trim().let { trimmed ->
    buildString(length * 2) {
      append(prefix)
      trimmed.forEach { ch ->
        when (ch) {
          GREEDY_WILDCARD_CHAR, SINGLE_WILDCARD_CHAR, ESC_CHAR -> append(ESC_CHAR)
        }
        append(ch)
      }
      append(postfix)
    }
  }

  /**
   * Wrap with LIKE operator wildcard '%' and escape any necessary characters within the string,
   * '%', '_', and [ESC_CHAR]. If we want to support wildcard searches later we could add our
   * own wildcards and map them.
   */
  fun String?.wrapAsFilter(): Filter = wrapForLike(GREEDY_WILDCARD, GREEDY_WILDCARD).asFilter

  /** Create a where clause to search for this using SQLite "like" */
  fun <T : String?> SqlTypeExpression<T>.likeEscaped(
    search: T,
    prefix: CharSequence = GREEDY_WILDCARD,
    postfix: CharSequence = GREEDY_WILDCARD
  ): Op<Boolean> = like(search.wrapForLike(prefix, postfix)).escape(ESC_CHAR)

  /** Create a where clause to search for this using SQLite "not like" */
  fun <T : String?> SqlTypeExpression<T>.notLikeEscaped(
    search: T,
    prefix: CharSequence = GREEDY_WILDCARD,
    postfix: CharSequence = GREEDY_WILDCARD
  ): Op<Boolean> = notLike(search.wrapForLike(prefix, postfix)).escape(ESC_CHAR)
}
