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

@file:Suppress("MemberVisibilityCanBePrivate", "PropertyName")

package com.ealva.toque.test.service.player

import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.service.player.TransitionPlayer
import com.nhaarman.expect.fail

class TransitionPlayerStub : TransitionPlayer {
  var _isPlaying = false
  var _isPlayingCalled = 0
  override val isPlaying: Boolean
    get() {
      _isPlayingCalled++
      return _isPlaying
    }

  var _isPaused = false
  var _isPausedCalled = 0
  override val isPaused: Boolean
    get() {
      _isPausedCalled++
      return _isPaused
    }

  var _isShutdown = false
  var _isShutdownCalled = 0
  override val playerIsShutdown: Boolean
    get() {
      _isShutdownCalled++
      return _isShutdown
    }

  var _volume = Volume.NONE
  var _volumeGetCalled = 0
  var _volumeSetCalled = 0
  override var playerVolume: Volume
    get() {
      _volumeGetCalled++
      return _volume
    }
    set(value) {
      _volumeSetCalled++
      _volume = value
    }

  var _volumeRange = Volume.NONE..Volume.MAX
  var _volumeRangeCalled = 0
  override val volumeRange: VolumeRange
    get() {
      _volumeRangeCalled++
      return _volumeRange
    }


  var _allowVolumeChange = true
  var _allowVolumeChangeGetCalled = 0
  var _allowVolumeChangeSetCalled = 0
  override var allowVolumeChange: Boolean
    get() {
      ++_allowVolumeChangeGetCalled
      return _allowVolumeChange
    }
    set(value) {
      ++_allowVolumeChangeSetCalled
      _allowVolumeChange = value
    }

  var _remainingTime = Millis(0)
  var _remainingTimeCalled = 0
  override val remainingTime: Millis
    get() {
      _remainingTimeCalled++
      return _remainingTime
    }

  var _notifyPausedCalled = 0
  override fun notifyPaused() {
    _notifyPausedCalled++
  }

  var _notifyPlayingCalled = 0
  override fun notifyPlaying() {
    _notifyPlayingCalled++
  }

  var _pauseCalled = 0
  override fun pause() {
    _pauseCalled++
  }

  var _playCalled = 0
  override fun play() {
    _playCalled++
  }

  var _shutdownCalled = 0
  override fun shutdownPlayer() {
    _shutdownCalled++
  }

  var _shouldContinue = false
  var _shouldContinueCalled = 0
  override fun shouldContinue(): Boolean {
    _shouldContinueCalled++
    return _shouldContinue
  }

  fun verifyZeroInteractions() {
    when {
      _isPlayingCalled > 0 -> fail("isPlaying called")
      _isPausedCalled > 0 -> fail("isPaused called")
      _isShutdownCalled > 0 -> fail("isShutdown called")
      _volumeGetCalled > 0 -> fail("volume.get called")
      _volumeSetCalled > 0 -> fail("volume.set called")
      _volumeRangeCalled > 0 -> fail("volumeRange called")
      _remainingTimeCalled > 0 -> fail("remainingTime called")
      _notifyPausedCalled > 0 -> fail("notifyPaused called")
      _notifyPlayingCalled > 0 -> fail("notifyPlaying called")
      _pauseCalled > 0 -> fail("pause called")
      _playCalled > 0 -> fail("play called")
      _shutdownCalled > 0 -> fail("shutdown called")
      _shouldContinueCalled > 0 -> fail("shouldContinue called")
    }
  }
}
