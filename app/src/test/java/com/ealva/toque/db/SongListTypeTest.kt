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

package com.ealva.toque.db

import com.ealva.toque.db.SongListType.Album
import com.ealva.toque.db.SongListType.All
import com.ealva.toque.db.SongListType.Artist
import com.ealva.toque.db.SongListType.Composer
import com.ealva.toque.db.SongListType.External
import com.ealva.toque.db.SongListType.Genre
import com.ealva.toque.db.SongListType.PlayList
import com.nhaarman.expect.expect
import org.junit.Test

class SongListTypeTest {

  @Test
  fun nextType() {
    SongListType.values().forEach { type ->
      with(type) {
        expectNext(
          when (this) {
            All -> Album
            Album -> Artist
            Artist -> Composer
            Composer -> Genre
            Genre -> PlayList
            PlayList -> Album
            External -> Album
          }
        )
      }
    }
  }

  @Test
  fun previousType() {
    SongListType.values().forEach { type ->
      with(type) {
        expectPrevious(
          when (this) {
            All -> PlayList
            Album -> PlayList
            Artist -> Album
            Composer -> Artist
            Genre -> Composer
            PlayList -> Genre
            External -> PlayList
          }
        )
      }
    }
  }
}

fun SongListType.expectNext(next: SongListType) = expect(nextType()).toBe(next)
fun SongListType.expectPrevious(previous: SongListType) = expect(previousType()).toBe(previous)
