/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.common

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was
 * successful, catching and encapsulating any thrown exception, except [CancellationException], as a
 * failure.
 *
 * ```runCatching``` breaks coroutine structured concurrency. This function will catch everything
 * and make it an [Err] except [CancellationException]
 */
@OptIn(ExperimentalContracts::class)
inline fun <V> runSuspendCatching(block: () -> V): Result<V, Throwable> {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  return try {
    Ok(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    Err(e)
  }
}

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was
 * successful, catching and encapsulating any thrown exception, except [CancellationException], as a
 * failure.
 *
 * ```runCatching``` breaks coroutine structured concurrency. This function will catch everything
 * and make it an [Err] except [CancellationException]
 */
@OptIn(ExperimentalContracts::class)
inline infix fun <T, V> T.runSuspendCatching(block: T.() -> V): Result<V, Throwable> {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  return try {
    Ok(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    Err(e)
  }
}
