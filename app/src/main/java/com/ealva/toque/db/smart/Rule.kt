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
import androidx.compose.runtime.Immutable
import com.ealva.welite.db.expr.Op
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class Rule(
  val id: Long,
  val ruleField: RuleField,
  val matcher: Matcher<*>,
  val data: MatcherData
) : Parcelable {
  init {
    require(ruleField.contains(matcher)) {
      "Rule:${this.javaClass} does not contain matcher ${matcher.javaClass}"
    }
  }

  val ruleType: Int
    get() = ruleField.id shl 16 and matcher.id

  fun makeWhereClause(): Op<Boolean> = ruleField.makeWhereClause(matcher, data)

  fun makeJoinTemplate(): JoinTemplate? = ruleField.makeJoinClause(matcher, data)

  fun hasSameFieldAndMatcher(newRule: Rule): Boolean =
    ruleField === newRule.ruleField && matcher === newRule.matcher

  /**
   * Is the Rule consistent and [data] is valid for the [matcher]
   */
  val isValid: Boolean get() = ruleField.contains(matcher) && matcher.willAccept(data)
  inline val isNotValid: Boolean get() = !isValid

  override fun toString(): String {
    return "Rule[id=$id, ruleField=$ruleField, matcher=$matcher, data=$data]"
  }

  companion object {

    operator fun invoke(
      id: Long,
      ruleField: RuleField,
      matcherId: Int,
      data: MatcherData
    ): Rule = Rule(
      id = id,
      ruleField = ruleField,
      matcher = ruleField.reifyMatcher(matcherId),
      data = data
    )

  }
}
