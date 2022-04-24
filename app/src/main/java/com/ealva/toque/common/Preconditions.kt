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

package com.ealva.toque.common

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T> requireThen(value: Boolean, lazyMessage: () -> Any, block: () -> T): T {
  contract {
    returns() implies value
  }
  require(value, lazyMessage)
  return block()
}

@OptIn(ExperimentalContracts::class)
inline fun <T> requireThen(value: Boolean, block: () -> T): T {
  contract {
    returns() implies value
  }
  return requireThen(value, { "Failed requirement." }, block)
}

@OptIn(ExperimentalContracts::class)
inline fun <T> checkThen(value: Boolean, lazyMessage: () -> Any, block: () -> T): T {
  contract {
    returns() implies value
  }
  check(value, lazyMessage)
  return block()
}

@OptIn(ExperimentalContracts::class)
inline fun <T> checkThen(value: Boolean, block: () -> T): T {
  contract {
    returns() implies value
  }
  return checkThen(value, { "Check failed." }, block)
}

/**
 * Returns `this` value if it satisfies the given [predicate] or throws IllegalArgumentException
 * if it doesn't.
 */
inline fun <T> T.takeRequire(predicate: T.() -> Boolean): T =
  takeRequire(predicate) { "Failed requirement." }

/**
 * Returns `this` value if it satisfies the given [predicate] or throws IllegalArgumentException
 * if it doesn't.
 */
inline fun <T> T.takeRequire(
  predicate: T.() -> Boolean,
  lazyMessage: () -> Any
): T  = apply { require(predicate(), lazyMessage) }
