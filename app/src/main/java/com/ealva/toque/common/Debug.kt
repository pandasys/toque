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

package com.ealva.toque.common

import com.ealva.toque.BuildConfig

inline fun debug(block: () -> Unit) {
  if (BuildConfig.DEBUG) block()
}

inline fun debugCheck(value: Boolean, lazyMessage: () -> Any) {
  debug { check(value, lazyMessage) }
}

inline fun debugRequire(value: Boolean, lazyMessage: () -> Any): Unit = debug {
  if (!value) {
    val message = lazyMessage()
    throw IllegalArgumentException(message.toString())
  }
}
