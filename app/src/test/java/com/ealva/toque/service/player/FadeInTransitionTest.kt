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
import com.ealva.toque.common.NullSuspendingThrottle
import com.ealva.toque.common.Volume
import com.ealva.toque.common.toMillis
import com.ealva.toque.test.service.player.TransitionPlayerStub
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FadeInTransitionTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var player: TransitionPlayerStub
  private lateinit var transition: PlayerTransition

  @Before
  fun init() {
    player = TransitionPlayerStub()
    transition = FadeInTransition(
      2000.toMillis(),
      false,
      throttle = NullSuspendingThrottle,
      dispatcher = coroutineRule.testDispatcher
    )
    transition.setPlayer(player)
  }

  @Test
  fun isPlaying() = run {
    player._isPlaying = false
    expect(transition.isPlaying).toBe(true) // shouldn't ask the player, always true
  }

  @Test
  fun isPaused() {
    player._isPaused = true
    expect(transition.isPaused).toBe(false) // shouldn't ask the player, always false
  }

  @Test
  fun accept() {
    expect(transition.accept(PlayImmediateTransition())).toBe(false)
    expect(transition.accept(FadeInTransition(Millis.ZERO, false))).toBe(false)
    expect(transition.accept(ShutdownFadeOutTransition(Millis.ZERO))).toBe(true)
    expect(transition.accept(ShutdownImmediateTransition())).toBe(true)
    expect(transition.accept(PauseImmediateTransition())).toBe(true)
    expect(transition.accept(PauseFadeOutTransition(Millis.ZERO))).toBe(true)
  }

  @Test
  fun execute() = coroutineRule.runBlockingTest {
    // given
    player._volume = Volume.ZERO
    player._isPlaying = false
    player._remainingTime = 2000.toMillis()
    player._shouldContinue = true

    // when
    transition.execute()

    // then
    expect(player._playCalled).toBe(1)
    expect(player._notifyPlayingCalled).toBe(1)
    expect(player._volumeGetCalled).toBe(1)
    expect(player._shouldContinueCalled).toBeGreaterThan(0)
    expect(player._remainingTimeCalled).toBe(1)
    expect(player._volumeSetCalled).toBeGreaterThan(20)
    expect(player._volume).toBe(Volume.ONE_HUNDRED)
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
