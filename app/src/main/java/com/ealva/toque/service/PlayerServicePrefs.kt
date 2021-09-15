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

package com.ealva.toque.service

import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.prefs.BaseToquePreferenceStore
import com.ealva.toque.service.queue.QueueType

typealias PlayerServicePrefsSingleton = PreferenceStoreSingleton<PlayerServicePrefs>

interface PlayerServicePrefs : PreferenceStore<PlayerServicePrefs> {
  val currentQueueType: StorePref<Int, QueueType>

  companion object {
    fun make(storage: Storage): PlayerServicePrefs = PlayerServicePrefsImpl(storage)
  }
}

private class PlayerServicePrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<PlayerServicePrefs>(storage), PlayerServicePrefs {
  override val currentQueueType by enumPref(QueueType.Audio)
}
