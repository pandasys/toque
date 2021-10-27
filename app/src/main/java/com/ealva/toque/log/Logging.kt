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

@file:Suppress("unused", "FunctionName")

package com.ealva.toque.log

import com.ealva.ealvalog.LogEntry
import com.ealva.ealvalog.Logger
import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.d
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.w
import com.ealva.ealvalog.wtf
import com.ealva.toque.BuildConfig

inline fun Logger._d(
  throwable: Throwable? = null,
  marker: Marker? = null,
  block: (LogEntry) -> Unit
) {
  if (BuildConfig.DEBUG) d(throwable, marker, null, block)
}

inline fun Logger._i(
  throwable: Throwable? = null,
  marker: Marker? = null,
  block: (LogEntry) -> Unit
) {
  if (BuildConfig.DEBUG) i(throwable, marker, null, block)
}

inline fun Logger._w(
  throwable: Throwable? = null,
  marker: Marker? = null,
  block: (LogEntry) -> Unit
) {
  if (BuildConfig.DEBUG) w(throwable, marker, null, block)
}

inline fun Logger._e(
  throwable: Throwable? = null,
  marker: Marker? = null,
  block: (LogEntry) -> Unit
) {
  if (BuildConfig.DEBUG) e(throwable, marker, null, block)
}

inline fun Logger._wtf(
  throwable: Throwable? = null,
  marker: Marker? = null,
  block: (LogEntry) -> Unit
) {
  if (BuildConfig.DEBUG) wtf(throwable, marker, null, block)
}

inline fun Logger.runLogging(msg: () -> String, block: () -> Unit) = try {
  block()
} catch (e: Throwable) {
  e(e) { it(msg()) }
}
