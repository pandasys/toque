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

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ealva.toque.common.Millis
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.service.player.PlayerTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface VlcPlayerFactory {
  suspend fun make(
    vlcMedia: VlcMedia,
    listener: VlcPlayerListener,
    vlcEqPreset: VlcEqPreset,
    onPreparedTransition: PlayerTransition,
    prefs: AppPreferences,
    duration: Millis,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
  ): VlcPlayer

  companion object {
    operator fun invoke(
      context: Context,
      libVlcSingleton: LibVlcSingleton
    ): VlcPlayerFactory = VlcPlayerFactoryImpl(context, libVlcSingleton)
  }
}

class VlcPlayerFactoryImpl(
  context: Context,
  private val libVlcSingleton: LibVlcSingleton
) : VlcPlayerFactory {
  private val powerManager: PowerManager = requireNotNull(context.getSystemService())

  override suspend fun make(
    vlcMedia: VlcMedia,
    listener: VlcPlayerListener,
    vlcEqPreset: VlcEqPreset,
    onPreparedTransition: PlayerTransition,
    prefs: AppPreferences,
    duration: Millis,
    dispatcher: CoroutineDispatcher
  ): VlcPlayer {
    return VlcPlayer.make(
      libVlcSingleton.instance(),
      vlcMedia,
      duration,
      listener,
      vlcEqPreset,
      onPreparedTransition,
      prefs,
      powerManager,
      dispatcher
    )
  }
}

object NullVlcPlayerFactory : VlcPlayerFactory {
  override suspend fun make(
    vlcMedia: VlcMedia,
    listener: VlcPlayerListener,
    vlcEqPreset: VlcEqPreset,
    onPreparedTransition: PlayerTransition,
    prefs: AppPreferences,
    duration: Millis,
    dispatcher: CoroutineDispatcher
  ): VlcPlayer = NullVlcPlayer
}
