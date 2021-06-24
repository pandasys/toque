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

package com.ealva.toque.service.player

import android.os.PowerManager
import com.ealva.toque.common.Millis

interface WakeLock {
  fun acquire()
  fun release()

  companion object {
    operator fun invoke(
      lock: PowerManager.WakeLock,
      timeout: Millis
    ): WakeLock = WakeLockImpl(lock, timeout)
  }
}

private class WakeLockImpl(val lock: PowerManager.WakeLock, val timeout: Millis) : WakeLock {
  override fun acquire() = lock.acquire(timeout.value)
  override fun release() = lock.release()
}
