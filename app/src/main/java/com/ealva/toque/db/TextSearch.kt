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

import com.ealva.toque.db.wildcard.SqliteLike.likeEscaped
import com.ealva.toque.db.wildcard.SqliteLike.notLikeEscaped
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.neq

enum class TextSearch {
  Is {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column eq match
  },
  IsNot {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column neq match
  },
  Contains {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column.likeEscaped(match)
  },
  DoesNotContain {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column.notLikeEscaped(match)
  },
  BeginsWith {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column.likeEscaped(search = match, prefix = "")
  },
  EndsWith {
    override fun <T : String?> makeWhereOp(
      column: SqlTypeExpression<T>,
      match: T
    ): Op<Boolean> = column.likeEscaped(search = match, postfix = "")
  };

  abstract fun <T : String?> makeWhereOp(
    column: SqlTypeExpression<T>,
    match: T
  ): Op<Boolean>
}

interface HasTextSearch {
  val textSearch: TextSearch
}
