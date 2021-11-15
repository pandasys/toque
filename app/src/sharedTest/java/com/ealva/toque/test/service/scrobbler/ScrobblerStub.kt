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

@file:Suppress("PropertyName")

package com.ealva.toque.test.service.scrobbler

import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.scrobble.Scrobbler
import com.nhaarman.expect.fail

class ScrobblerStub : Scrobbler {
  var _startCalled = 0
  val _startItems = mutableListOf<PlayableAudioItem>()
  override fun start(item: PlayableAudioItem) {
    _startCalled++
    _startItems.add(item)
  }

  var _resumeCalled = 0
  val _resumeItems = mutableListOf<PlayableAudioItem>()
  override fun resume(item: PlayableAudioItem) {
    _resumeCalled++
    _resumeItems.add(item)
  }

  var _pauseCalled = 0
  val _pauseItems = mutableListOf<PlayableAudioItem>()
  override fun pause(item: PlayableAudioItem) {
    _pauseCalled++
    _pauseItems.add(item)
  }

  var _completeCalled = 0
  val _completeItems = mutableListOf<PlayableAudioItem>()
  override fun complete(item: PlayableAudioItem) {
    _completeCalled++
    _completeItems.add(item)
  }

  var _shutdownCalled = 0
  override fun shutdown() {
    _shutdownCalled++
  }

  fun verifyZeroInteractions() {
    when {
      _startCalled > 0 -> fail("start called")
      _resumeCalled > 0 -> fail("resume called")
      _pauseCalled > 0 -> fail("pause called")
      _completeCalled > 0 -> fail("complete called")
      _shutdownCalled > 0 -> fail("shutdown called")
    }
  }
}
