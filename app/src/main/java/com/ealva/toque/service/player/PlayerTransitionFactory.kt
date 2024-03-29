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
/*
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.audioout.AudioOutputModule.AudioTrack
import com.ealva.toque.common.Millis
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.player.PlayerTransitionFactory.MediaTransitionType

interface PlayerTransitionFactory {
  fun getPauseTransition(): PlayerTransition
  fun getPlayTransition(): PlayerTransition

  enum class MediaTransitionType {
    AutoAdvance {
      override fun make(prefs: AppPrefs): PlayerTransitionPair =
        make(prefs.autoAdvanceFade(), prefs.autoAdvanceFadeLength(), prefs.audioOutputModule())
    },
    ManualAdvance {
      override fun make(prefs: AppPrefs): PlayerTransitionPair =
        make(prefs.manualChangeFade(), prefs.manualChangeFadeLength(), prefs.audioOutputModule())
    };

    abstract fun make(prefs: AppPrefs): PlayerTransitionPair

    protected fun make(
      shouldFade: Boolean,
      length: Millis,
      outputModule: AudioOutputModule
    ): PlayerTransitionPair = if (shouldFade) {
      CrossFadeTransition(length, outputModule == AudioTrack)
    } else DirectTransition()
  }

  fun getMediaTransition(type: MediaTransitionType): PlayerTransitionPair

  companion object {
    operator fun invoke(appPrefs: AppPrefs): PlayerTransitionFactory =
      PlayerTransitionFactoryImpl(appPrefs)
  }
}

private class PlayerTransitionFactoryImpl(val prefs: AppPrefs) : PlayerTransitionFactory {
  override fun getPauseTransition(): PlayerTransition = if (prefs.playPauseFade()) {
    PauseFadeOutTransition(prefs.playPauseFadeLength())
  } else PauseImmediateTransition()

  override fun getPlayTransition(): PlayerTransition = if (prefs.playPauseFade()) {
    FadeInTransition(prefs.playPauseFadeLength())
  } else PlayImmediateTransition()

  override fun getMediaTransition(type: MediaTransitionType): PlayerTransitionPair =
    type.make(prefs)
}
*/
