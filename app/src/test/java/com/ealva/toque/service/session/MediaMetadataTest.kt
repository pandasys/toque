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

import android.net.Uri
import android.os.Build
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.service.media.StarRating
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

const val ID: String = "Id"
val TITLE = Title("title")
val ARTIST = ArtistName("Artist")
val DURATION = Millis(1000)
val ALBUM = AlbumTitle("Album")
const val AUTHOR: String = "Author"
const val WRITER: String = "writer"
const val COMPOSER: String = "composer"
const val COMPILATION: String = "Compilation"
const val DATE: String = "date"
const val YEAR: Long = 2020
const val GENRE: String = "genre"
const val TRACK_NUMBER: Long = 1
const val TRACK_COUNT: Long = 2
const val DISC_NUMBER: Long = 3
val ALBUM_ARTIST = ArtistName("album artist")
val ART_URI: Uri = Uri.parse("file:\\blah")
val ALBUM_ART_URI: Uri = Uri.parse("file:\\slog")
val USER_RATING: StarRating = StarRating.STAR_5
val RATING: StarRating = StarRating.STAR_4
const val DISPLAY_TITLE: String = "disp title"
const val DISPLAY_SUBTITLE: String = "subtitle"
const val DISPLAY_DESCRIPTION: String = "description"
val DISPLAY_ICON_URI: Uri = Uri.parse("file:\\freude")
val MEDIA_URI: Uri = Uri.parse("file:\\karen")
const val DOWNLOAD_STATUS: Long = 0

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class MediaMetadataTest {
  @Test
  fun testMetadataKeys() {
    val metadata = MediaMetadata().apply {
      edit {
        id = ID
        title = TITLE
        artist = ARTIST
        duration = DURATION
        album = ALBUM
        author = AUTHOR
        writer = WRITER
        composer = COMPOSER
        compilation = COMPILATION
        date = DATE
        year = YEAR
        genre = GENRE
        trackNumber = TRACK_NUMBER
        trackCount = TRACK_COUNT
        discNumber = DISC_NUMBER
        albumArtist = ALBUM_ARTIST
        artUri = ART_URI
        albumArtUri = ALBUM_ART_URI
        userRating = USER_RATING
        rating = RATING
        displayTitle = DISPLAY_TITLE
        displaySubtitle = DISPLAY_SUBTITLE
        displayDescription = DISPLAY_DESCRIPTION
        displayIconUri = DISPLAY_ICON_URI
        mediaUri = MEDIA_URI
        downloadStatus = DOWNLOAD_STATUS
      }
    }
    with(metadata) {
      expect(id).toBe(ID)
      expect(title).toBe(TITLE)
      expect(artist).toBe(ARTIST)
      expect(duration).toBe(DURATION)
      expect(album).toBe(ALBUM)
      expect(author).toBe(AUTHOR)
      expect(writer).toBe(WRITER)
      expect(composer).toBe(COMPOSER)
      expect(compilation).toBe(COMPILATION)
      expect(date).toBe(DATE)
      expect(year).toBe(YEAR)
      expect(genre).toBe(GENRE)
      expect(trackNumber).toBe(TRACK_NUMBER)
      expect(trackCount).toBe(TRACK_COUNT)
      expect(discNumber).toBe(DISC_NUMBER)
      expect(albumArtist).toBe(ALBUM_ARTIST)
      expect(artUri).toBe(ART_URI)
      expect(albumArtUri).toBe(ALBUM_ART_URI)
      expect(userRating).toBe(USER_RATING)
      expect(rating).toBe(RATING)
      expect(displayTitle).toBe(DISPLAY_TITLE)
      expect(displaySubtitle).toBe(DISPLAY_SUBTITLE)
      expect(displayDescription).toBe(DISPLAY_DESCRIPTION)
      expect(displayIconUri).toBe(DISPLAY_ICON_URI)
      expect(mediaUri).toBe(MEDIA_URI)
      expect(downloadStatus).toBe(DOWNLOAD_STATUS)
    }
  }
}
