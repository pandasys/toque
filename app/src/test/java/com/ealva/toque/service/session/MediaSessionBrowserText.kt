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

package com.ealva.toque.service.session

import com.ealva.toque.persist.toAlbumId
import com.ealva.toque.persist.toArtistId
import com.ealva.toque.persist.toComposerId
import com.ealva.toque.persist.toGenreId
import com.ealva.toque.persist.toMediaId
import com.ealva.toque.persist.toPlaylistId
import com.nhaarman.expect.expect
import org.junit.Test

class MediaSessionBrowserText {
  @Test
  fun `test make ID`() {
    listOf(
      Pair(1000.toMediaId(), ""),
      Pair(100.toArtistId(), MediaSessionBrowser.ARTIST_PREFIX),
      Pair(200.toAlbumId(), MediaSessionBrowser.ALBUM_PREFIX),
      Pair(300.toGenreId(), MediaSessionBrowser.GENRE_PREFIX),
      Pair(400.toComposerId(), MediaSessionBrowser.COMPOSER_PREFIX),
      Pair(500.toPlaylistId(), MediaSessionBrowser.PLAYLIST_PREFIX)
    ).forEach { pair ->
      val id = pair.first
      val prefix = pair.second
      val mediaId = MediaSessionBrowser.makeMediaId(id)
      mediaId.split('_').let { components ->
        if (components.size == 1) {
          expect(components[0].toLong()).toBe(id.value)
        } else {
          expect(components.size).toBe(2)
          expect(components[0]).toBe(prefix)
          expect(components[1].toLong()).toBe(id.value)
        }
      }
    }
  }
}
