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

package com.ealva.toque.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.milliseconds

@Parcelize
@JvmInline
value class Filter(val value: String) : Parcelable {
  inline val isBlank: Boolean get() = value.isBlank()
  inline val isNotBlank: Boolean get() = value.isNotBlank()

  companion object {
    val NoFilter = Filter("")
    val debounceDuration = 300.milliseconds
  }
}

inline val String.asFilter: Filter get() = Filter(this.trim())
