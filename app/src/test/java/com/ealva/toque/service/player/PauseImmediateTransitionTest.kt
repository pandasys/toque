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

package com.ealva.toque.service.player

import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.test.service.player.TransitionPlayerStub
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PauseImmediateTransitionTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var player: TransitionPlayerStub
  private lateinit var transition: PlayerTransition

  @Before
  fun init() {
    player = TransitionPlayerStub()
    transition = PauseImmediateTransition()
    transition.setPlayer(player)
  }

  @Test
  fun isPlaying() {
    player._isPlaying = false
    player._isPaused = true
    expect(transition.isPlaying).toBe(false)
    expect(transition.isPaused).toBe(true)
  }

  @Test
  fun isPaused() {
    player._isPlaying = true
    player._isPaused = false
    expect(transition.isPlaying).toBe(true)
    expect(transition.isPaused).toBe(false)
  }

  @Test
  fun accept() {
    expect(transition.accept(PlayImmediateTransition())).toBe(true)
    expect(transition.accept(FadeInTransition(Millis.ZERO))).toBe(true)
    expect(transition.accept(ShutdownFadeOutTransition(Millis.ZERO))).toBe(false)
    expect(transition.accept(ShutdownImmediateTransition())).toBe(true)
    expect(transition.accept(PauseImmediateTransition())).toBe(false)
    expect(transition.accept(PauseFadeOutTransition(Millis.ZERO))).toBe(false)
  }

  @Test
  fun execute() = coroutineRule.runBlockingTest {
    transition.execute()
    expect(player._pauseCalled).toBe(1)
    expect(player._notifyPausedCalled).toBe(1)
    expect(player._volumeSetCalled).toBe(1)
    expect(player._volume).toBe(Volume.ZERO)
    expect(transition.isCancelled).toBe(false)
    expect(transition.isFinished).toBe(true)
  }

  @Test
  fun cancel() = coroutineRule.runBlockingTest {
    transition.setCancelled()
    transition.execute()
    player.verifyZeroInteractions()
    expect(transition.isCancelled).toBe(true)
    expect(transition.isFinished).toBe(true)
  }
}
