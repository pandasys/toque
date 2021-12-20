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

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.persist.HasConstId
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.or

enum class AnyOrAll(
  override val id: Int,
  @StringRes private val stringRes: Int,
  val op: Expression<Boolean>.(op: Expression<Boolean>) -> Op<Boolean>,
) : HasConstId {
  All(1, R.string.all, { rhs -> and(rhs) }),
  Any(2, R.string.any, { rhs -> or(rhs) });

  fun apply(lhs: Op<Boolean>, rhs: Op<Boolean>): Op<Boolean> = lhs.op(rhs)

  override fun toString(): String {
    return fetch(stringRes)
  }

  companion object {
    val allValues = values().asList()
  }
}
