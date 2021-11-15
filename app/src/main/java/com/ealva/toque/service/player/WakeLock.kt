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

package com.ealva.toque.service.player

import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis

private val LOG by lazyLogger(WakeLock::class)

interface WakeLock {
  fun acquire()
  fun release()
}

interface WakeLockFactory {
  fun makeWakeLock(timeout: Millis, tag: String): WakeLock

  companion object {
    operator fun invoke(powerManager: PowerManager): WakeLockFactory =
      WakeLockFactoryImpl(powerManager)
  }
}

private class WakeLockFactoryImpl(private val powerManager: PowerManager) : WakeLockFactory {
  override fun makeWakeLock(timeout: Millis, tag: String): WakeLock =
    WakeLockImpl(powerManager, timeout, tag)
}

private class WakeLockImpl(
  powerManager: PowerManager,
  private val timeout: Millis,
  tag: String
) : WakeLock {
  private val lock: PowerManager.WakeLock = powerManager.makeWakeLock(tag)
  override fun acquire() = lock.acquire(timeout())
  override fun release() = lock.release()
}

private fun PowerManager.makeWakeLock(tag: String) = newWakeLock(PARTIAL_WAKE_LOCK, tag).apply {
  setReferenceCounted(false)
}
