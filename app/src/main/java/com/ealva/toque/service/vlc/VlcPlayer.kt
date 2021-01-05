/*
 * Copyright 2020 eAlva.com
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

import com.ealva.toque.service.player.Player
import org.videolan.libvlc.MediaPlayer

class VlcPlayer private constructor(private val player: MediaPlayer) : Player {
  fun release() {
    player.release()
  }

  override fun pause(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun play(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  private fun setPreset(vlcEqPreset: VlcEqPreset) {
    vlcEqPreset.applyToPlayer(player)
  }

  companion object {
    fun make(
      libVlc: LibVlc,
      vlcMedia: VlcMedia,
      vlcEqPreset: VlcEqPreset = VlcEqPreset.NONE
    ): VlcPlayer {
      return VlcPlayer(libVlc.makeMediaPlayer(vlcMedia.media)).apply {
        setPreset(vlcEqPreset)
      }
      /*
        override var duration: Long,
  private val updateListener: AvPlayerListener,
  private var eqPreset: Equalizer?,
  private val initialSeek: Long,
  private val onPreparedTransition: PlayerTransition,
  startPaused: Boolean,
  private val powerManager: PowerManager,
  private val prefs: AppPreferences

       */
    }
  }
}
