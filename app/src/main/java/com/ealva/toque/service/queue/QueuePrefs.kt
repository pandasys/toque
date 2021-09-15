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

package com.ealva.toque.service.queue

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.SongListType
import com.ealva.toque.prefs.BaseToquePreferenceStore
import com.ealva.toque.service.media.EqMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

typealias QueuePrefsSingleton = PreferenceStoreSingleton<QueuePrefs>

interface QueuePrefs : PreferenceStore<QueuePrefs> {
  val firstRun: BoolPref
  val repeatMode: PreferenceStore.Preference<Int, RepeatMode>
  val shuffleMode: PreferenceStore.Preference<Int, ShuffleMode>
  val eqMode: PreferenceStore.Preference<Int, EqMode>
  val lastListType: PreferenceStore.Preference<Int, SongListType>
  val lastListName: PreferenceStore.Preference<String, String>

  suspend fun setLastList(idList: AudioIdList)

  /*
  fun nextRepeatMode(): Repeat
  fun shuffleSongs(): Boolean
   */
  companion object {
    fun make(storage: Storage): QueuePrefs = QueuePrefsImpl(storage)

    val NULL: QueuePrefs by lazy {
      object : BaseToquePreferenceStore<QueuePrefs>(NullStorage), QueuePrefs {
        override val firstRun by preference(false)
        override val repeatMode by enumPref(RepeatMode.None)
        override val shuffleMode by enumPref(ShuffleMode.None)
        override val eqMode by enumPref(EqMode.Off)
        override val lastListType by enumPref(SongListType.All)
        override val lastListName by preference("")
        override suspend fun setLastList(idList: AudioIdList) {}
      }
    }
  }
}

private class QueuePrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<QueuePrefs>(storage), QueuePrefs {
  override val firstRun by preference(true)
  override val repeatMode by enumPref(RepeatMode.None)
  override val shuffleMode by enumPref(ShuffleMode.None)
  override val eqMode by enumPref(EqMode.Off)
  override val lastListType by enumPref(SongListType.All)
  override val lastListName by preference("")
  override suspend fun setLastList(idList: AudioIdList) = edit {
    it[lastListType] = idList.listType
    it[lastListName] = idList.listName
  }
}

private object NullStorage : Storage {
  override val data: Flow<Preferences> = emptyFlow()
  override suspend fun clear() {}
  override suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {}
  override fun <T> get(key: Preferences.Key<T>): T? = null
}
