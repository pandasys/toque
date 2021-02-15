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

import android.net.Uri
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.toMillis
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.file.isNetworkScheme
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.service.media.Media
import com.ealva.toque.service.media.MediaEvent
import com.ealva.toque.service.media.MediaPlayerEvent
import com.ealva.toque.service.player.PlayerTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.interfaces.IMedia

private val LOG by lazyLogger(VlcMedia::class)

class VlcMedia(
  val media: IMedia,
  val uri: Uri,
  private val mediaId: MediaId,
  private val albumId: AlbumId,
  private val presetSelector: EqPresetSelector,
  private val vlcPlayerFactory: VlcPlayerFactory,
  private val prefs: AppPreferences,
  private val dispatcher: CoroutineDispatcher
) : Media, VlcPlayerListener {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val duration = media.duration.toMillis()
  private var player: VlcPlayer = NullVlcPlayer
  override val mediaEventFlow = MutableSharedFlow<MediaEvent>()
  override val playerEventFlow = MutableSharedFlow<MediaPlayerEvent>()

  fun release() {
    scope.cancel()
    player.shutdown()
    media.release()
  }

  override suspend fun prepareAndPlay(onPreparedTransition: PlayerTransition) {
    player = makePlayer(onPreparedTransition)
  }

  private suspend fun makePlayer(onPreparedTransition: PlayerTransition): VlcPlayer =
    vlcPlayerFactory.make(
      this,
      this,
      presetSelector.getPreferredEqPreset(mediaId, albumId),
      onPreparedTransition,
      prefs,
      duration,
      dispatcher
    )

  companion object {
    fun parseFlagFromUri(uri: Uri): Int =
      if (uri.isNetworkScheme()) PARSE_NETWORK else PARSE_LOCAL

    /** Parse metadata if the file is local. Doesn't bother with artwork */
    const val PARSE_LOCAL = IMedia.Parse.ParseLocal

    /** Parse metadata even if over a network connection. Doesn't bother with artwork */
    const val PARSE_NETWORK = IMedia.Parse.ParseNetwork

    /** Parse metadata and fetch artwork if the file is local */
    const val PARSE_WITH_ART_LOCAL = IMedia.Parse.FetchLocal

    /** Parse metadata and fetch artwork even if over a network connection */
    const val PARSE_WITH_ART_NETWORK = IMedia.Parse.FetchNetwork
  }

  override fun onPositionUpdate(position: Millis, duration: Millis) {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.PositionUpdate(position, duration)) }
  }

  override fun onPrepared(position: Millis, duration: Millis) {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.Prepared(position, duration)) }
  }

  override fun onStart(firstStart: Boolean) {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.Start(firstStart)) }
  }

  override fun onPaused(position: Millis) {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.Paused(position)) }
  }

  override fun onStopped() {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.Stopped) }
  }

  override fun onPlaybackComplete() {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.PlaybackComplete) }
  }

  override fun onError() {
    scope.launch { playerEventFlow.emit(MediaPlayerEvent.Error) }
  }
}
