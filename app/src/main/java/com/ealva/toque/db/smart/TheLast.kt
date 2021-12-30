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

import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.persist.HasConstId

enum class TheLast(
  override val id: Int,
  private val stringRes: Int,
  val unitsName: String
) : HasConstId {
  Days(1, R.string.days, "days") {
    override fun calc(units: Long): Long {
      return -units
    }
  },
  Weeks(2, R.string.weeks, "days") {
    override fun calc(units: Long): Long {
      return -units * 7
    }
  },
  Months(3, R.string.months, "months") {
    override fun calc(units: Long): Long {
      return -units
    }
  };

  data class Units(val value: Int)

  override fun toString(): String {
    return fetch(stringRes)
  }

  /**
   * [calc] is the negative amount strftime will use to calculate time in the past from now.
   * This is used in the DateMatchers InTheLast and NotInTheLast. [Days] and [Weeks]
   * both use days via strftime and [Months] uses months.
   */
  abstract fun calc(units: Long): Long

  companion object {
    val ALL_VALUES = values().asList()

    /**
     * @throws IllegalArgumentException if [id] is not found and [defaultValue] is null
     */
    fun fromId(id: Int, defaultValue: TheLast? = null): TheLast {
      return when (id) {
        Days.id -> Days
        Weeks.id -> Weeks
        Months.id -> Months
        else -> defaultValue ?: throw IllegalArgumentException("Invalid ID $id")
      }
    }

    fun isValidId(id: Long): Boolean {
      return ALL_VALUES.any { it.id.toLong() == id }
    }
  }
}
