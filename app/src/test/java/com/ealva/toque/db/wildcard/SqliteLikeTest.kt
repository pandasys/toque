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

import com.ealva.toque.common.asFilter
import com.ealva.toque.db.wildcard.SqliteLike.wrapForLike
import com.ealva.toque.db.wildcard.SqliteLike.likeEscaped
import com.ealva.toque.db.wildcard.SqliteLike.notLikeEscaped
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.welite.db.table.Table
import com.nhaarman.expect.expect
import org.junit.Test

class SqliteLikeTest {
  @Test
  fun testStringLikeWrapped() {
    listOf(
      "" to "",
      "ab" to "%ab%",
      "%a%b_c_" to """%\%a\%b\_c\_%""",
      """\""" to """%\\%""",
      """\%\_\\""" to """%\\\%\\\_\\\\%"""
    ).forEach { (actual, expected) ->
      expect(actual.wrapForLike()).toBe(expected)
    }
  }

  @Test
  fun testStringLikeBeginsWith() {
    listOf(
      "" to "",
      "ab" to "ab%",
      "%a%b_c_" to """\%a\%b\_c\_%""",
      """\""" to """\\%""",
      """\%\_\\""" to """\\\%\\\_\\\\%"""

    ).forEach { (actual, expected) ->
      expect(actual.wrapForLike(prefix = "")).toBe(expected)
    }
  }

  @Test
  fun testStringLikeEndsWith() {
    listOf(
      "" to "",
      "ab" to "%ab",
      "%a%b_c_" to """%\%a\%b\_c\_""",
      """\""" to """%\\""",
      """\%\_\\""" to """%\\\%\\\_\\\\"""

    ).forEach { (actual, expected) ->
      expect(actual.wrapForLike(postfix = "")).toBe(expected)
    }
  }

  @Test
  fun testLikeEscaped() {
    val theTable = object : Table("TheTable") {
      val theColumn = text("theColumn")
    }
    val op = theTable.theColumn.likeEscaped("find")
    expect(op.toString()).toBe("""TheTable.theColumn LIKE '%find%' ESCAPE '\'""")
  }


  @Test
  fun testNotLikeEscaped() {
    val theTable = object : Table("TheTable") {
      val theColumn = text("theColumn")
    }
    val op = theTable.theColumn.notLikeEscaped("find")
    expect(op.toString()).toBe("""TheTable.theColumn NOT LIKE '%find%' ESCAPE '\'""")
  }

  @Test
  fun testWrapAsFilter() {
    listOf(
      "" to "",
      "ab" to "%ab%",
      "%a%b_c_" to """%\%a\%b\_c\_%""",
      """\""" to """%\\%""",
      """\%\_\\""" to """%\\\%\\\_\\\\%"""
    ).forEach { (actual, expected) ->
      expect(actual.wrapAsFilter()).toBe(expected.asFilter)
    }
  }
}
