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

package com.ealva.toque.service.queue

import android.media.AudioManager
import android.os.Build
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange

class MusicStreamVolume(private val audioManager: AudioManager) : StreamVolume {
  override val volumeIsFixed: Boolean = audioManager.isVolumeFixed

  override val volumeRange: VolumeRange =
    audioManager.musicStreamMinVolume..audioManager.musicStreamMaxVolume

  override var streamVolume: Volume
    get() = Volume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    set(value) = value.coerceIn(volumeRange).let { newVolume ->
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume(), 0)
      if (newVolume() != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
        audioManager.setStreamVolume(
          AudioManager.STREAM_MUSIC,
          newVolume(),
          AudioManager.FLAG_SHOW_UI
        )
      }
    }
}

private val AudioManager.musicStreamMinVolume: Volume
  get() {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Volume(getStreamMinVolume(AudioManager.STREAM_MUSIC))
    } else {
      Volume.NONE
    }
  }

private val AudioManager.musicStreamMaxVolume: Volume
  get() = Volume(getStreamMaxVolume(AudioManager.STREAM_MUSIC))
