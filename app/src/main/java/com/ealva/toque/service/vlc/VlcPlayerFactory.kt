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
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ealva.toque.common.Millis
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayerFactory
import com.ealva.toque.service.player.PlayerTransition
import com.ealva.toque.service.player.WakeLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

private const val WAKE_LOCK_TIMEOUT_MINUTES = 25L

private val WAKE_LOCK_TIMEOUT = Millis(TimeUnit.MINUTES.toMillis(WAKE_LOCK_TIMEOUT_MINUTES))

class VlcPlayerFactory(
  context: Context,
  private val libVlcSingleton: LibVlcSingleton,
  private val libVlcPrefsSingleton: LibVlcPrefsSingleton,
  private val appPrefs: AppPrefs,
  private val presetSelector: EqPresetSelector,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : AvPlayerFactory {
  private val powerManager: PowerManager = requireNotNull(context.getSystemService())

  override suspend fun makeAudioPlayer(
    media: Uri,
    mediaId: MediaId,
    albumId: AlbumId,
    duration: Millis,
    preparedTransition: PlayerTransition,
    startPaused: Boolean
  ): AvPlayer = VlcPlayer(
    libVlcSingleton.instance()
      .makeAudioMedia(media, Millis.ZERO, startPaused, libVlcPrefsSingleton.instance()),
    duration,
    presetSelector.getPreferredEqPreset(mediaId, albumId),
    preparedTransition,
    appPrefs,
    WakeLock(powerManager.makeWakeLock(), WAKE_LOCK_TIMEOUT),
    dispatcher
  )
}

private fun PowerManager.makeWakeLock() =
  newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name).apply {
    setReferenceCounted(false)
  }
