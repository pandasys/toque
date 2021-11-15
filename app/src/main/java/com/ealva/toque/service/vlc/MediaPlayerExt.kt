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

package com.ealva.toque.service.vlc

import com.ealva.toque.common.Volume
import org.videolan.libvlc.MediaPlayer

const val BUFFERING_PERCENT_TRIGGER_PREPARED = 90.0
@Suppress("NOTHING_TO_INLINE")
inline fun MediaPlayer.Event.ampleBufferedForPrepare(): Boolean =
  buffering > BUFFERING_PERCENT_TRIGGER_PREPARED

val MediaPlayer.Event.isBuffering: Boolean
  inline get() = type == MediaPlayer.Event.Buffering

/**
 * Map the volume value used by the MediaPlayer to a value which better matches human hearing. As of
 * now the user does not directly affect this volume. The user only affects the stream volume
 * (Media volume presented to the user) and this volume is only affected via transitions.
 */
fun MediaPlayer.setMappedVolume(linearVolume: Volume) {
  volume = linearToLogVolumeMap[linearVolume().coerceIn(linearToLogVolumeMap.indices)]
}

/**
 * Linear volume controls are poor because that's not how our hearing works. This map converts the
 * linear volume scale the media player uses to a logarithmic scale our ears use.
 * [Decibels](https://en.wikipedia.org/wiki/Decibel)
 *
 * To hear the problem change the [MediaPlayer.setMappedVolume] to directly use the linear volume
 * without mapping, turn the device volume up (doesn't need to be very loud), and use transition
 * fades: PauseFadeOutTransition, ShutdownFadeOutTransition, FadeInTransition, etc... On fade out
 * you should hear the volume decrease much too quickly and be silent for too long. Fade in sounds
 * very sudden too.
 *
 * Currently no reason to calculate this at runtime as there are only 101 distinct values, so this
 * function was used to calculate these values:
 * ```
 * fun linearToLog(z: Int): Int {
 *   val x = 0.0
 *   val y = 100.00
 *   val b = (if (x > 0) ln(y / x) else ln(y)) / (y - x)
 *   val a = 100 / exp(b * 100)
 *   return (a * exp(b * z)).roundToInt()
 * }```
 */
private val linearToLogVolumeMap = intArrayOf(
  0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4,
  5, 5, 5, 5, 5, 6, 6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 10, 10, 10, 11, 11, 12, 13, 13, 14, 14, 15, 16,
  17, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 32, 33, 35, 36, 38, 40, 42, 44, 46, 48,
  50, 52, 55, 58, 60, 63, 66, 69, 72, 76, 79, 83, 87, 91, 95, 100
)

@Suppress("unused")
private fun MediaPlayer.Event.asString(): String {
  return when (type) {
    MediaPlayer.Event.MediaChanged -> "Event.MediaChanged"
    MediaPlayer.Event.Opening -> "Event.Opening"
    MediaPlayer.Event.Buffering -> "Event.Buffering $buffering"
    MediaPlayer.Event.Playing -> "Event.Playing"
    MediaPlayer.Event.Paused -> "Event.Paused"
    MediaPlayer.Event.Stopped -> "Event.Stopped"
    MediaPlayer.Event.EndReached -> "Event.EndReached"
    MediaPlayer.Event.EncounteredError -> "Event.EncounteredError"
    MediaPlayer.Event.TimeChanged -> "Event.TimeChanged $timeChanged"
    MediaPlayer.Event.PositionChanged -> "Event.PositionChanged $positionChanged"
    MediaPlayer.Event.SeekableChanged -> "Event.SeekableChanged $seekable"
    MediaPlayer.Event.PausableChanged -> "Event.PausableChanged $pausable"
    MediaPlayer.Event.LengthChanged -> "Event.LengthChanged $lengthChanged"
    MediaPlayer.Event.Vout -> "Event.Vout $voutCount"
    MediaPlayer.Event.ESAdded -> "Event.ESAdded"
    MediaPlayer.Event.ESDeleted -> "Event.ESDeleted"
    MediaPlayer.Event.ESSelected -> "Event.ESSelected"
    MediaPlayer.Event.RecordChanged -> "Event.RecordChanged"
    else -> "Event.UNKNOWN"
  }
}
