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
import com.ealva.toque.common.toMillis
import com.ealva.toque.db.AlbumId
import com.ealva.toque.db.MediaId
import com.ealva.toque.file.isNetworkScheme
import com.ealva.toque.service.media.Media
import com.ealva.toque.service.media.MediaEvent
import com.ealva.toque.service.player.PlayerTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.videolan.libvlc.interfaces.IMedia

private val LOG by lazyLogger(VlcMedia::class)

class VlcMedia(
  val media: IMedia,
  val uri: Uri,
  private val mediaId: MediaId,
  private val albumId: AlbumId,
  private val presetSelector: EqPresetSelector,
  private val vlcPlayerFactory: VlcPlayerFactory,
  private val dispatcher: CoroutineDispatcher
) : Media {
  private val duration = media.duration.toMillis()
  private val mutableEventFlow = MutableSharedFlow<MediaEvent>()
  private var player: VlcPlayer = NullVlcPlayer

  override val eventFlow: Flow<MediaEvent>
    get() = mutableEventFlow

  fun release() {
    player.shutdown()
    media.release()
  }

  override suspend fun prepareAndPlay(onPreparedTransition: PlayerTransition) {
    player = makePlayer(onPreparedTransition)
  }

  private suspend fun makePlayer(onPreparedTransition: PlayerTransition): VlcPlayer =
    vlcPlayerFactory.make(
      this,
      presetSelector.getPreferredEqPreset(mediaId, albumId),
      onPreparedTransition,
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
}
