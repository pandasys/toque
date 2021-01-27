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

package com.ealva.toque.persist

import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import org.junit.Test

class EnumsTest {

  @Test
  fun `test reify`() {
    expect(TestEnum::class.reify(TestEnum.Fourth.id)).toBe(
      TestEnum.Fourth
    )
    TestEnum.values().forEach { value ->
      expect(value.javaClass.reify(value.id)).toBe(value)
      expect(value.javaClass.kotlin.reify(value.id)).toBe(value)
    }
  }

  @Test
  fun `test reify sparse`() {
    GenSparse.First.ordinal
    expect(GenSparse::class.reify(GenSparse.Fourth.id)).toBe(
      GenSparse.Fourth
    )
    expect(GenSparse::class.reify(GenSparse.First.id)).toBe(
      GenSparse.First
    )
    expect(GenSparse::class.reify(GenSparse.Third.id)).toBe(
      GenSparse.Third
    )
    expect(GenSparse::class.reify(GenSparse.Second.id)).toBe(
      GenSparse.Second
    )
    GenSparse.values().forEach { value ->
      expect(value.javaClass.reify(value.id)).toBe(value)
      expect(value.javaClass.kotlin.reify(value.id)).toBe(value)
    }
  }

  @Test
  fun `test reify null`() {
    expect(TestEnum::class.java.reify(Int.MAX_VALUE)).toBeNull { "reify should have returned null" }
    expect(
      TestEnum::class.java.reify(
        Int.MAX_VALUE,
        TestEnum.Fourth
      )
    ).toBe(TestEnum.Fourth)
    expect(
      TestEnum::class.java.reify(
        Int.MAX_VALUE,
        TestEnum.First
      )
    ).toBe(TestEnum.First)
    expect(TestEnum::class.reify(Int.MAX_VALUE)).toBeNull { "reify should have returned null" }
    expect(
      TestEnum::class.reify(
        Int.MAX_VALUE,
        TestEnum.Fourth
      )
    ).toBe(TestEnum.Fourth)
    expect(
      TestEnum::class.reify(
        Int.MAX_VALUE,
        TestEnum.First
      )
    ).toBe(TestEnum.First)
  }

  @Test
  fun `test reify require`() {
    TestEnum.values().forEach { value ->
      expect(value.javaClass.reifyRequire(value.id)).toBe(value)
      expect(value.javaClass.kotlin.reifyRequire(value.id)).toBe(value)
    }
    GenSparse.values().forEach { value ->
      expect(value.javaClass.reifyRequire(value.id)).toBe(value)
      expect(value.javaClass.kotlin.reifyRequire(value.id)).toBe(value)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `test reify require fail`() {
    val enum = TestEnum::class.reifyRequire(Int.MAX_VALUE)
    fail("Should have thrown IllegalArgumentException but received $enum")
  }
}

enum class TestEnum(override val id: Int) : HasConstId {
  First(1),
  Second(2),
  Third(3),
  Fourth(4);

  override fun toString(): String = "$name($id)"
}

enum class GenSparse(override val id: Int) : HasConstId {
  First(Int.MIN_VALUE),
  Second(-1),
  Third(Int.MAX_VALUE),
  Fourth(0);

  override fun toString(): String = "$name($id)"
}
