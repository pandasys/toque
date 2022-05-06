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

import com.nhaarman.expect.expect
import org.junit.Test

typealias PrevUpdateResult = Pair<MatcherData, MatcherData>

class RatingMatcherTest {
  @Test
  fun sanitize() {
    val matcher = RatingMatcher.IsInTheRange
    testData.forEach { (one, two) ->
      expect(matcher.sanitize(one)).toBe(two) {
        "(${one.first},${one.second}) (${two.first},${two.second})"
      }
    }
  }
}

private fun makeData(
  low: Long,
  high: Long,
  expectedLow: Long,
  expectedHigh: Long
): PrevUpdateResult = PrevUpdateResult(
  makeRatingData(low, high),
  makeRatingData(expectedLow, expectedHigh)
)

private fun makeRatingData(low: Long, high: Long) = MatcherData("", low, high)

private val testData = listOf(
  makeData(-1, 0, 0, 10),
  makeData(-1, 101, 0, 100),

  makeData(0, 0, 0, 10),
  makeData(0, 10, 0, 10),
  makeData(10, 0, 10, 20),
  makeData( 10, 10, 10, 20),

  makeData(100, 0, 90, 100),
  makeData(100, 100, 90, 100),
  makeData(90, 90, 90, 100),
  makeData(90, 80, 90, 100),
)
