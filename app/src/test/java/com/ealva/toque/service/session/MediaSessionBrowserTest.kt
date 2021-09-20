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

import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PlaylistId
import com.nhaarman.expect.expect
import org.junit.Test

class MediaSessionBrowserTest {
  @Test
  fun `test make ID`() {
    listOf(
      Pair(MediaId(1000), MediaSessionBrowser.MEDIA_PREFIX),
      Pair(ArtistId(100), MediaSessionBrowser.ARTIST_PREFIX),
      Pair(AlbumId(200), MediaSessionBrowser.ALBUM_PREFIX),
      Pair(GenreId(300), MediaSessionBrowser.GENRE_PREFIX),
      Pair(ComposerId(400), MediaSessionBrowser.COMPOSER_PREFIX),
      Pair(PlaylistId(500), MediaSessionBrowser.PLAYLIST_PREFIX)
    ).forEach { pair ->
      val id = pair.first
      val prefix = pair.second
      val mediaId = id.toCompatMediaId()
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

  @Test
  fun `test string to PersistentId`() {
    expect("${MediaSessionBrowser.MEDIA_PREFIX}_1000".toPersistentId()).toBe(MediaId(1000))
    expect("1000".toPersistentId()).toBe(MediaId(1000))
    expect("${MediaSessionBrowser.ARTIST_PREFIX}_1".toPersistentId()).toBe(ArtistId(1))
    expect("${MediaSessionBrowser.ALBUM_PREFIX}_20000".toPersistentId()).toBe(AlbumId(20000))
    expect("${MediaSessionBrowser.GENRE_PREFIX}_2".toPersistentId()).toBe(GenreId(2))
    expect("${MediaSessionBrowser.COMPOSER_PREFIX}_3".toPersistentId()).toBe(ComposerId(3))
    expect("${MediaSessionBrowser.PLAYLIST_PREFIX}_4".toPersistentId()).toBe(PlaylistId(4))
  }
}
