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
import com.ealva.toque.media.Media
import com.ealva.toque.media.MediaEvent
import com.ealva.toque.tag.ArtistParserFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

private val LOG by lazyLogger(VlcMedia::class)

class VlcMedia(
  private val media: IMedia,
  private val uri: Uri,
  private val artistParserFactory: ArtistParserFactory
) : Media {
  private val mutableEventFlow = MutableSharedFlow<MediaEvent>()
  private val mediaPlayer: MediaPlayer? = null

  override val eventFlow: Flow<MediaEvent>
    get() = mutableEventFlow

  override fun close() {
    mediaPlayer?.release()
    media.release()
  }
}
