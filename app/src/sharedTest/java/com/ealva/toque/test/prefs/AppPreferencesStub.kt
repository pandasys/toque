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

@file:Suppress("PropertyName")

package com.ealva.toque.test.prefs

import androidx.datastore.preferences.Preferences
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.prefs.SelectMediaAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AppPreferencesStub : AppPreferences {
  override fun allowDuplicates(): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun allowDuplicates(value: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun goToNowPlaying(): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun goToNowPlaying(value: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun ignoreSmallFiles(): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun ignoreSmallFiles(value: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun ignoreThreshold(): Millis {
    TODO("Not yet implemented")
  }

  override suspend fun ignoreThreshold(time: Millis): Boolean {
    TODO("Not yet implemented")
  }

  override fun lastScanTime(): Millis {
    TODO("Not yet implemented")
  }

  override suspend fun lastScanTime(time: Millis): Boolean {
    TODO("Not yet implemented")
  }

  override fun fadeOnPlayPause(): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun fadeOnPlayPause(fade: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun playPauseFadeLength(): Millis {
    TODO("Not yet implemented")
  }

  override suspend fun playPauseFadeLength(millis: Millis): Boolean {
    TODO("Not yet implemented")
  }

  var _scrobblerItems = mutableListOf<ScrobblerPackage>()
  override fun scrobbler(): ScrobblerPackage = _scrobblerItems.removeAt(0)

  override suspend fun scrobbler(scrobblerPackage: ScrobblerPackage): Boolean {
    TODO("Not yet implemented")
  }

  var _scrobblerFlow: Flow<ScrobblerPackage> = flow { emit(ScrobblerPackage.None) }
  override fun scrobblerFlow(): Flow<ScrobblerPackage> = _scrobblerFlow

  override fun duckAction(): DuckAction {
    TODO("Not yet implemented")
  }

  override suspend fun duckAction(action: DuckAction): Boolean {
    TODO("Not yet implemented")
  }

  override fun duckVolume(): Volume {
    TODO("Not yet implemented")
  }

  override suspend fun duckVolume(volume: Volume): Boolean {
    TODO("Not yet implemented")
  }

  var _playUpNextAction: PlayUpNextAction = PlayUpNextAction.Prompt
  var _getPlayUpNextActionCalled = 0
  override fun playUpNextAction(): PlayUpNextAction =
    _playUpNextAction.also { _getPlayUpNextActionCalled++ }

  var _setPlayUpNextActionCalled = 0
  override suspend fun playUpNextAction(action: PlayUpNextAction): Boolean {
    _playUpNextAction = action
    _setPlayUpNextActionCalled++
    return true
  }

  var _endOfQueueAction: EndOfQueueAction = EndOfQueueAction.Stop
  var _getEndOfQueueActionCalled = 1
  override fun endOfQueueAction(): EndOfQueueAction =
    _endOfQueueAction.also { _getEndOfQueueActionCalled++ }

  var _setEndOfQueueActionCalled = false
  override suspend fun endOfQueueAction(action: EndOfQueueAction): Boolean {
    _endOfQueueAction = action
    _setEndOfQueueActionCalled = true
    return true
  }

  override fun selectMediaAction(): SelectMediaAction {
    TODO("Not yet implemented")
  }

  override suspend fun selectMediaAction(action: SelectMediaAction): Boolean {
    TODO("Not yet implemented")
  }

  override suspend fun resetAllToDefault() {
    TODO("Not yet implemented")
  }

  var _asMap: Map<Preferences.Key<*>, Any> = emptyMap()
  var _asMapCalled = 0
  override fun asMap(): Map<Preferences.Key<*>, Any> = _asMap.also { _asMapCalled++ }
}
