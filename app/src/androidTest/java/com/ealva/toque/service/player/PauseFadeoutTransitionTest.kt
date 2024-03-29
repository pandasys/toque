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

package com.ealva.toque.service.player

import com.ealva.toque.common.Volume
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.sharedtest.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class PauseFadeoutTransitionTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var player: TransitionPlayerSpy
  private lateinit var transition: PlayerTransition
  private lateinit var clockStub: ClockStub

  @Before
  fun init() {
    clockStub = ClockStub(Clock.System.now(), FadeInTransition.MIN_FADE_LENGTH, 20)
    player = TransitionPlayerSpy()
    transition = PauseFadeOutTransition(
      fadeLength = 2000.toDuration(DurationUnit.MILLISECONDS),
      clock = clockStub,
      dispatcher = coroutineRule.testDispatcher
    )
    transition.setPlayer(player)
  }

  @Test
  fun isPlaying() {
    player._isPlaying = true
    expect(transition.isPlaying).toBe(false) // shouldn't ask the player, always true
  }

  @Test
  fun isPaused() {
    player._isPaused = false
    expect(transition.isPaused).toBe(true) // shouldn't ask the player, always false
  }

  @Test
  fun accept() {
    expect(transition.accept(PlayImmediateTransition())).toBe(true)
    expect(transition.accept(FadeInTransition(Duration.ZERO))).toBe(true)
    expect(transition.accept(ShutdownFadeOutTransition(Duration.ZERO))).toBe(false)
    expect(transition.accept(ShutdownImmediateTransition())).toBe(true)
    expect(transition.accept(PauseImmediateTransition())).toBe(false)
    expect(transition.accept(PauseFadeOutTransition(Duration.ZERO))).toBe(false)
  }

  @Test
  fun execute() = runTest {
    // given
    player._volume = Volume.MAX
    player._isPlaying = true
    player._remainingTime = 2000.toDuration(DurationUnit.MILLISECONDS)

    // when
    transition.execute()
    advanceUntilIdle()

    // then
    expect(player._shutdownCalled).toBe(0)
    expect(player._pauseCalled).toBe(1)
    expect(player._notifyPausedCalled).toBe(1)
    expect(player._volumeGetCalled).toBe(1)
    expect(player._remainingTimeCalled).toBe(1)
    expect(player._volumeSetCalled).toBe(20)
    expect(player._volume).toBe(Volume.NONE)
    expect(transition.isCancelled).toBe(false)
    expect(transition.isFinished).toBe(true)
  }

  @Test
  fun cancel() = runTest {
    transition.setCancelled()
    transition.execute()
    player.verifyZeroInteractions()
    expect(transition.isCancelled).toBe(true)
    expect(transition.isFinished).toBe(true)
  }
}
