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
import com.ealva.toque.common.Millis
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.media.Media
import com.ealva.toque.service.media.MediaFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.interfaces.IMedia.Parse

class VlcMediaFactory(
  private val libVlcSingleton: LibVlcSingleton,
  private val appPrefsSingleton: AppPrefsSingleton,
  private val libVlcPrefsSingleton: LibVlcPrefsSingleton,
  private val presetFactory: VlcPresetFactory,
  private val vlcPlayerFactory: VlcPlayerFactory,
  private val dispatcher: CoroutineDispatcher? = null
) : MediaFactory {
  override suspend fun makeAudio(
    location: Uri,
    mediaId: MediaId,
    albumId: AlbumId,
    initialSeek: Millis,
    startPaused: Boolean
  ): Media = withContext(dispatcher ?: Dispatchers.IO) {
    val libVlc = libVlcSingleton.instance()
    VlcMedia(
      libVlc.makeAudioMedia(
        location,
        initialSeek,
        startPaused,
        libVlcPrefsSingleton.instance()
      ).apply { parse(Parse.ParseLocal) },
      location,
      mediaId,
      albumId,
      presetFactory,
      vlcPlayerFactory,
      appPrefsSingleton.instance(),
      dispatcher ?: Dispatchers.Main
    )
  }
}
