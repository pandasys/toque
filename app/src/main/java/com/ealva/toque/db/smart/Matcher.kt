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

package com.ealva.toque.db.smart

import android.os.Parcelable
import com.ealva.toque.persist.HasConstId
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.Column

interface Matcher<T> : HasConstId, Parcelable {
  /**
   * Matcher should make the where part of the select statement. An empty return string indicates
   * there is no where part to contribute
   */
  fun makeWhereClause(column: Column<T>, data: MatcherData): Op<Boolean>

  /**
   * Indicates if the [data] is valid for this matcher
   */
  fun willAccept(data: MatcherData): Boolean

  /**
   * If [data] is unacceptable for the matcher, try to modify it to be acceptable or return default
   * values. By default if [data] is not acceptable, ie. ![willAccept], [MatcherData.EMPTY] is
   * returned.
   */
  fun acceptableData(data: MatcherData): MatcherData =
    if (willAccept(data)) data else sanitize(data)

  fun sanitize(data: MatcherData): MatcherData = defaultData

  val defaultData: MatcherData
    get() = MatcherData.EMPTY

  companion object {
    const val SQL_LIKE_WILDCARD = "%"
    const val ESCAPED_SQL_LIKE_WILDCARD = "%%"
  }
}

fun String.escapeWildcard(): String {
  return replace(Matcher.SQL_LIKE_WILDCARD, Matcher.ESCAPED_SQL_LIKE_WILDCARD)
}
