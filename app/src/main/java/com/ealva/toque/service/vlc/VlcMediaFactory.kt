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
import com.ealva.toque.common.toMillis
import com.ealva.toque.db.AlbumId
import com.ealva.toque.db.MediaId
import com.ealva.toque.media.Media
import com.ealva.toque.media.MediaFactory

class VlcMediaFactory(
  private val libVlcSingleton: LibVlcSingleton,
  private val preferencesSingleton: LibVlcPreferencesSingleton,
  private val presetFactory: VlcPresetFactory
) : MediaFactory {
  override suspend fun makeAudio(
    location: Uri,
    mediaId: MediaId,
    albumId: AlbumId
  ): Media {
    val libVlc = libVlcSingleton.instance()
    return VlcMedia(
      libVlc,
      libVlc.makeAudioMedia(location, 0.toMillis(), true, preferencesSingleton.instance()),
      location,
      mediaId,
      albumId,
      presetFactory
    )
  }
}
