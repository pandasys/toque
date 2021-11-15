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

package com.ealva.toque.service.session.server

import com.ealva.prefstore.store.BasePreferenceStore
import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage

typealias NotificationPrefsSingleton = PreferenceStoreSingleton<NotificationPrefs>

interface NotificationPrefs : PreferenceStore<NotificationPrefs> {
  val showPrevInNotification: BoolPref
  val showNextInNotification: BoolPref
  val showCloseInNotification: BoolPref

  companion object {
    fun make(storage: Storage): NotificationPrefs = NotificationPrefsImpl(storage)
  }
}

private class NotificationPrefsImpl(
  storage: Storage
) : BasePreferenceStore<NotificationPrefs>(storage), NotificationPrefs {
  override val showPrevInNotification: BoolPref by preference(false)
  override val showNextInNotification: BoolPref by preference(false)
  override val showCloseInNotification: BoolPref by preference(true)
}
