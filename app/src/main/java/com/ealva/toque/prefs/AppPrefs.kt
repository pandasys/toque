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

package com.ealva.toque.prefs

import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.audio.AudioOutputModule
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.prefs.AppPrefs.Companion.DUCK_VOLUME_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.MEDIA_FADE_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.PLAY_PAUSE_FADE_RANGE

typealias AppPrefsSingleton = PreferenceStoreSingleton<AppPrefs>

interface AppPrefs : PreferenceStore<AppPrefs> {
  val firstRun: BoolPref
  val allowDuplicates: BoolPref
  val goToNowPlaying: BoolPref
  val ignoreSmallFiles: BoolPref
  val ignoreThreshold: MillisStorePref
  val lastScanTime: MillisStorePref
  val playPauseFade: BoolPref
  val playPauseFadeLength: MillisStorePref
  val autoAdvanceFade: BoolPref
  val autoAdvanceFadeLength: MillisStorePref
  val manualChangeFade: BoolPref
  val manualChangeFadeLength: MillisStorePref
  val scrobbler: StorePref<Int, ScrobblerPackage>
  val duckAction: StorePref<Int, DuckAction>
  val duckVolume: VolumeStorePref
  val playUpNextAction: StorePref<Int, PlayUpNextAction>
  val endOfQueueAction: StorePref<Int, EndOfQueueAction>
  val selectMediaAction: StorePref<Int, SelectMediaAction>
  val audioOutputModule: StorePref<Int, AudioOutputModule>

  companion object {
    val PLAY_PAUSE_FADE_RANGE = Millis(500)..Millis(2000)
    val MEDIA_FADE_RANGE = Millis(2000)..Millis(10000)
    val DUCK_VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED

    fun make(storage: Storage): AppPrefs = AppPrefsImpl(storage)
  }
}

private class AppPrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<AppPrefs>(storage), AppPrefs {
  override val firstRun by preference(true)
  override val allowDuplicates by preference(false)
  override val goToNowPlaying by preference(true)
  override val ignoreSmallFiles by preference(false)
  override val ignoreThreshold by millisPref(Millis.ZERO)
  override val lastScanTime by millisPref(Millis.ZERO)
  override val playPauseFade by preference(false)
  override val playPauseFadeLength by millisPref(Millis.ONE_SECOND) { millis ->
    millis.coerceIn(PLAY_PAUSE_FADE_RANGE)
  }
  override val autoAdvanceFade by preference(false)
  override val autoAdvanceFadeLength by millisPref(Millis.THREE_SECONDS) { millis ->
    millis.coerceIn(MEDIA_FADE_RANGE)
  }
  override val manualChangeFade by preference(false)
  override val manualChangeFadeLength by millisPref(Millis.THREE_SECONDS) { millis ->
    millis.coerceIn(MEDIA_FADE_RANGE)
  }
  override val scrobbler by enumPref(ScrobblerPackage.None)
  override val duckAction by enumPref(DuckAction.Duck)
  override val duckVolume by volumePref(DUCK_VOLUME_RANGE.endInclusive / 2) {
    it.coerceIn(DUCK_VOLUME_RANGE)
  }
  override val playUpNextAction by enumPref(PlayUpNextAction.Prompt)
  override val endOfQueueAction by enumPref(EndOfQueueAction.PlayNextList)
  override val selectMediaAction by enumPref(SelectMediaAction.Play)
  override val audioOutputModule by enumPref(AudioOutputModule.DEFAULT)
}
