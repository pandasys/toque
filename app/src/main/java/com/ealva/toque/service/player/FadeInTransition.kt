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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.service.player

import com.ealva.toque.common.Millis
import com.ealva.toque.common.SuspendingThrottle
import com.ealva.toque.common.Volume
import com.ealva.toque.service.audio.PlayerTransition.Type
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val VOLUME_HACK_DELAY = 10L
private const val PERMITS_PER_SECOND = 10.0 // 100 millis per permit

private val ADJUST_FROM_END = Millis(200)
private val MIN_FADE_LENGTH = Millis(100)
private val MIN_VOLUME_STEP = Volume.ONE

/**
 * A play transition that fades in volume
 */
class FadeInTransition(
  /** Requested length of fade in millis may be adjusted depending on remaining duration */
  private val requestedFadeLength: Millis,
  /** The player's current volume is used as starting volume unless this is true */
  private val forceStartVolumeZero: Boolean = false,
  /** The throttle only need be changed for test */
  private val throttle: SuspendingThrottle = SuspendingThrottle(PERMITS_PER_SECOND),
  /** The dispatcher only need be changed for test */
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BasePlayerTransition(Type.Play) {

  override val isPlaying: Boolean = true
  override val isPaused: Boolean = false

  override suspend fun doExecute(player: TransitionPlayer) {
    val cancelVolume = player.volumeRange.endInclusive + Volume.ONE

    supervisorScope {
      launch(dispatcher) {
        player.notifyPlaying()
        val startVolume: Volume = player.getStartVolume()
        val endVolume: Volume = player.volumeRange.endInclusive
        if (shouldContinueTransition(player)) {
          player.playerVolume = startVolume
          if (!player.isPlaying) {
            player.play()
            // VLC HACK START
            // YES delay - Vlc not setting volume to zero properly - causing audio squawk at song
            // start and this nasty hack seems to fix it on my device. Who knows about other
            // devices?? This is necessary, and works most of the time, to properly set the volume.
            // Also, unsure if this is dependent on OpenSl ES vs Audio Track
            player.playerVolume = startVolume
            delay(VOLUME_HACK_DELAY)
          }
          player.playerVolume = startVolume
          // VLC HACK END

          val remaining: Millis =
            requestedFadeLength.coerceAtMost(player.remainingTime - ADJUST_FROM_END)

          if (remaining > MIN_FADE_LENGTH) {
            val stepLength: Millis = remaining / MIN_FADE_LENGTH
            val volumeChange =
              ((endVolume - startVolume) / stepLength).coerceAtLeast(MIN_VOLUME_STEP)

            var currentVolume = startVolume
            while (currentVolume <= endVolume) {
              currentVolume = (currentVolume + volumeChange).coerceAtMost(endVolume)
              player.playerVolume = currentVolume
              if (shouldContinueTransition(player)) {
                if (currentVolume >= endVolume) {
                  currentVolume = cancelVolume
                  setComplete()
                } else {
                  // If using default throttle, sleep until at least 100 millis have passed
                  throttle.acquire()
                }
              } else {
                currentVolume = cancelVolume
                setCancelled()
              }
            }
          } else {
            player.playerVolume = endVolume
            setComplete()
          }
        }
      }
    }
  }

  private fun TransitionPlayer.getStartVolume(): Volume {
    return if (forceStartVolumeZero) Volume.NONE else playerVolume
  }

  override fun toString(): String = buildString {
    append("FadeInTransition(requestedFadeLength=")
    append(requestedFadeLength)
    append(" forceStartVolumeZero=")
    append(forceStartVolumeZero)
    append(')')
  }
}
