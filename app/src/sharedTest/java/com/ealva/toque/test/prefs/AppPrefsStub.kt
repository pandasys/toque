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

package com.ealva.toque.test.prefs

import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.MutablePreferenceStore
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.StoreHolder
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.prefs.SelectMediaAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

typealias BoolPrefStub = PrefStub<Boolean, Boolean>
typealias MillisPrefStub = PrefStub<Long, Millis>

@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
class AppPrefsStub : AppPrefs {
  override val updateFlow: Flow<StoreHolder<AppPrefs>> = emptyFlow()

  override suspend fun clear(vararg prefs: PreferenceStore.Preference<*, *>) {
    TODO("Not yet implemented")
  }

  override suspend fun edit(block: suspend AppPrefs.(MutablePreferenceStore) -> Unit) {
    TODO("Not yet implemented")
  }

  override val firstRun = BoolPrefStub()
  override val allowDuplicates = PrefStub<Boolean, AllowDuplicates>()
  override val goToNowPlaying = BoolPrefStub()
  override val ignoreSmallFiles = BoolPrefStub()
  override val ignoreThreshold = MillisPrefStub()
  override val lastScanTime = MillisPrefStub()
  override val playPauseFade = BoolPrefStub()
  override val playPauseFadeLength = MillisPrefStub()
  override val autoAdvanceFade = BoolPrefStub()
  override val autoAdvanceFadeLength = MillisPrefStub()
  override val manualChangeFade = BoolPrefStub()
  override val manualChangeFadeLength = MillisPrefStub()
  override val scrobbler = PrefStub<Int, ScrobblerPackage>()
  override val duckAction = PrefStub<Int, DuckAction>()
  override val duckVolume = PrefStub<Int, Volume>()
  override val playUpNextAction = PrefStub<Int, PlayUpNextAction>()
  override val endOfQueueAction = PrefStub<Int, EndOfQueueAction>()
  override val selectMediaAction = PrefStub<Int, SelectMediaAction>()
  override val audioOutputModule = PrefStub<Int, AudioOutputModule>()
  override val markPlayedPercentage = PrefStub<Int, Int>()
  override val rewindThenPrevious: BoolPref = BoolPrefStub()
}
