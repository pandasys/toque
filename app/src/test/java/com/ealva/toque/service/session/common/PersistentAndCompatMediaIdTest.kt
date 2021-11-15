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

package com.ealva.toque.service.session.common

import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.session.common.IdPrefixes.ALBUM_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.ARTIST_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.COMPOSER_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.GENRE_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.MEDIA_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.PLAYLIST_PREFIX
import com.nhaarman.expect.expect
import org.junit.Test

class PersistentAndCompatMediaIdTest {
  @Test
  fun `test make ID`() {
    listOf(
      Pair(MediaId(1000), MEDIA_PREFIX),
      Pair(ArtistId(100), ARTIST_PREFIX),
      Pair(AlbumId(200), ALBUM_PREFIX),
      Pair(GenreId(300), GENRE_PREFIX),
      Pair(ComposerId(400), COMPOSER_PREFIX),
      Pair(PlaylistId(500), PLAYLIST_PREFIX)
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
    expect("${MEDIA_PREFIX}_1000".toPersistentId()).toBe(MediaId(1000))
    expect("1000".toPersistentId()).toBe(MediaId(1000))
    expect("${ARTIST_PREFIX}_1".toPersistentId()).toBe(ArtistId(1))
    expect("${ALBUM_PREFIX}_20000".toPersistentId()).toBe(AlbumId(20000))
    expect("${GENRE_PREFIX}_2".toPersistentId()).toBe(GenreId(2))
    expect("${COMPOSER_PREFIX}_3".toPersistentId()).toBe(ComposerId(3))
    expect("${PLAYLIST_PREFIX}_4".toPersistentId()).toBe(PlaylistId(4))
  }
}
