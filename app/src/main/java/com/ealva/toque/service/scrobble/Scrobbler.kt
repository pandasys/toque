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

package com.ealva.toque.service.scrobble

import com.ealva.toque.service.audio.AudioQueueItem

/**
 * Interface to all scrobblers. To add a Scrobbler, add the package/title to
 * [com.ealva.toque.prefs.ScrobblerPackage], implement this interface, then call the constructor
 * in the [ScrobblerFactory.make] implementation.
 */
interface Scrobbler {
  fun start(item: AudioQueueItem)

  fun resume(item: AudioQueueItem)

  fun pause(item: AudioQueueItem)

  fun complete(item: AudioQueueItem)

  fun shutdown()
}

object NullScrobbler : Scrobbler {
  override fun start(item: AudioQueueItem) {}
  override fun resume(item: AudioQueueItem) {}
  override fun pause(item: AudioQueueItem) {}
  override fun complete(item: AudioQueueItem) {}
  override fun shutdown() {}
}
