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

/**
 * Throws an [IllegalArgumentException] with the result of calling [lazyMessage] if the [value] is
 * false, else executes [block] and returns the result.
 */
inline fun <T> requireThen(value: Boolean, lazyMessage: () -> Any, block: () -> T): T {
  require(value, lazyMessage)
  return block()
}

/**
 * Throws an [IllegalArgumentException] if the [value] is false, else executes [block]
 */
inline fun <T> requireThen(value: Boolean, block: () -> T): T =
  requireThen(value, { "Failed requirement." }, block)

inline fun <T> checkThen(value: Boolean, lazyMessage: () -> Any, block: () -> T): T {
  check(value, lazyMessage)
  return block()
}

inline fun <T> checkThen(value: Boolean, block: () -> T): T =
  checkThen(value, { "Check failed." }, block)

/**
 * Returns `this` value if it satisfies the given [requisite] or throws IllegalArgumentException
 * if it doesn't.
 */
inline fun <T> T.takeRequire(requisite: T.() -> Boolean): T =
  takeRequire(requisite) { "Failed requirement." }

/**
 * Returns `this` value if it satisfies the given [requisite] or throws IllegalArgumentException
 * if it doesn't.
 */
inline fun <T> T.takeRequire(
  requisite: T.() -> Boolean,
  lazyMessage: () -> Any
): T = apply { require(requisite(), lazyMessage) }

/**
 * Returns `this` value if it satisfies the given [requisite] or throws IllegalArgumentException
 * if it doesn't.
 */
inline fun <T> T.takeCheck(requisite: T.() -> Boolean): T =
  takeCheck(requisite) { "Failed requirement." }

/**
 * Returns `this` value if it satisfies the given [requisite] or throws IllegalStateException
 * if it doesn't.
 */
inline fun <T> T.takeCheck(
  requisite: T.() -> Boolean,
  lazyMessage: () -> Any
): T = apply { check(requisite(), lazyMessage) }
