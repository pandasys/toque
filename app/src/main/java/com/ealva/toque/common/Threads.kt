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

import android.os.Looper

val mainLooper: Looper = Looper.getMainLooper()
val uiThread: Thread = mainLooper.thread
val isUiThread: Boolean
  inline get() = uiThread === Thread.currentThread()

/**
 * Throws an [IllegalStateException] if not on the UI thread
 */
fun ensureUiThread() = check(isUiThread) {
  "Required execution on UI thread, current=${Thread.currentThread()}"
}
