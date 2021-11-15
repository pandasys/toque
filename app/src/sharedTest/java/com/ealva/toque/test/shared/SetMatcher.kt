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

@file:Suppress("unused")

package com.ealva.toque.test.shared

import com.nhaarman.expect.Matcher
import com.nhaarman.expect.fail

fun <T> expect(actual: Set<T>?): SetMatcher<T> {
  return SetMatcher(actual)
}

class SetMatcher<T>(override val actual: Set<T>?) : Matcher<Set<T>>(actual) {
  private val name = actual?.javaClass?.name ?: "null"
  fun toBeEmpty(message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to be empty, but the actual value was null.", message)
    }

    if (actual.isNotEmpty()) {
      fail("Expected $name to be empty.", message)
    }
  }

  fun toHaveSize(size: Int, message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to have size $size, but the actual value was null.", message)
    }

    if (actual.size != size) {
      fail("Expected $name to have size $size, but the actual size was ${actual.size}.", message)
    }
  }

  fun toContain(expected: T, message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to contain $expected, but the actual value was null.", message)
    }

    if (!actual.contains(expected)) {
      fail("Expected $name to contain $expected", message)
    }
  }
}
