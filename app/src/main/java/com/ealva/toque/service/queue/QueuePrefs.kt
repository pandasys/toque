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

package com.ealva.toque.service.queue

import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.SongListType
import com.ealva.toque.prefs.BaseToquePreferenceStore
import com.ealva.toque.service.media.EqMode

typealias QueuePrefsSingleton = PreferenceStoreSingleton<QueuePrefs>

interface QueuePrefs : PreferenceStore<QueuePrefs> {
  val firstRun: BoolPref
  val repeatMode: PreferenceStore.Preference<Int, RepeatMode>
  val shuffleMode: PreferenceStore.Preference<Int, ShuffleMode>
  val eqMode: PreferenceStore.Preference<Int, EqMode>
  val lastListType: PreferenceStore.Preference<Int, SongListType>
  val lastListName: PreferenceStore.Preference<String, String>
  val playbackRate: PreferenceStore.Preference<Float, PlaybackRate>
  suspend fun setLastList(idList: AudioIdList)

  companion object {
    fun make(storage: Storage): QueuePrefs = QueuePrefsImpl(storage)
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
  override val playbackRate by asTypePref(
    default = PlaybackRate.NORMAL,
    maker = { PlaybackRate(it) },
    serialize = { it.value },
    sanitize = { playbackRate -> playbackRate.coerceIn(PlaybackRate.RANGE) }
  )

  override suspend fun setLastList(idList: AudioIdList) = edit {
    it[lastListType] = idList.listType
    it[lastListName] = idList.listName
  }
}
