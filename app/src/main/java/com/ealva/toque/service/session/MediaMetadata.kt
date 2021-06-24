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

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.RatingCompat.RATING_3_STARS
import android.support.v4.media.RatingCompat.RATING_4_STARS
import android.support.v4.media.RatingCompat.RATING_5_STARS
import android.support.v4.media.RatingCompat.RATING_HEART
import android.support.v4.media.RatingCompat.RATING_PERCENTAGE
import android.support.v4.media.RatingCompat.RATING_THUMB_UP_DOWN
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.toAlbumTitle
import com.ealva.ealvabrainz.common.toArtistName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toTitle
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toStarRating

interface MediaMetadata {
  val id: String
  val title: Title
  val artist: ArtistName
  val duration: Millis
  val album: AlbumTitle
  val author: String
  val writer: String
  val composer: String
  val compilation: String
  val date: String
  val year: Long
  val genre: String
  val trackNumber: Long
  val trackCount: Long
  val discNumber: Long
  val albumArtist: ArtistName
  val art: Bitmap?
  val artUri: Uri
  val albumArt: Bitmap?
  val albumArtUri: Uri
  val userRating: StarRating
  val rating: StarRating
  val displayTitle: String
  val displaySubtitle: String
  val displayDescription: String
  val displayIcon: Bitmap?
  val displayIconUri: Uri
  val mediaUri: Uri
  val downloadStatus: Long

  /**
   * Modify the contents of this MediaMetadata. A "commit" is not performed until [block] returns,
   * so none of the val fields will return the new value until after [block]
   */
  fun edit(block: MutableMediaMetadata.() -> Unit)

  /**
   * This returns the [MediaMetadataCompat] equivalent of this MediaMetadata
   */
  val metadataCompat: MediaMetadataCompat

  companion object {
    operator fun invoke(): MediaMetadata = MediaMetadataImpl()
    operator fun invoke(compat: MediaMetadataCompat): MediaMetadata =
      MediaMetadataImpl(MediaMetadataCompat.Builder(compat))

    operator fun invoke(builder: MediaMetadataCompat.Builder): MediaMetadata =
      MediaMetadataImpl(builder)
  }
}

interface MutableMediaMetadata : MediaMetadata {
  override var id: String
  override var title: Title
  override var artist: ArtistName
  override var duration: Millis
  override var album: AlbumTitle
  override var author: String
  override var writer: String
  override var composer: String
  override var compilation: String
  override var date: String
  override var year: Long
  override var genre: String
  override var trackNumber: Long
  override var trackCount: Long
  override var discNumber: Long
  override var albumArtist: ArtistName
  override var art: Bitmap?
  override var artUri: Uri
  override var albumArt: Bitmap?
  override var albumArtUri: Uri
  override var userRating: StarRating
  override var rating: StarRating
  override var displayTitle: String
  override var displaySubtitle: String
  override var displayDescription: String
  override var displayIcon: Bitmap?
  override var displayIconUri: Uri
  override var mediaUri: Uri
  override var downloadStatus: Long
}

private class MediaMetadataImpl(
  private val builder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder(),
) : MutableMediaMetadata {
  private var compat: MediaMetadataCompat = builder.build()
  override val metadataCompat: MediaMetadataCompat
    get() = compat

  override var id: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, value)
    }

  override var title: Title
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_TITLE).toTitle()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, value.value)
    }

  override var artist: ArtistName
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST).toArtistName()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, value.value)
    }

  override var duration: Millis
    get() = Millis(compat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, value.value)
    }

  override var album: AlbumTitle
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM).toAlbumTitle()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, value.value)
    }

  override var author: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_AUTHOR).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, value)
    }

  override var writer: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_WRITER).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_WRITER, value)
    }

  override var composer: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_COMPOSER).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, value)
    }

  override var compilation: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_COMPILATION).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_COMPILATION, value)
    }

  override var date: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_DATE).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_DATE, value)
    }

  override var year: Long
    get() = compat.getLong(MediaMetadataCompat.METADATA_KEY_YEAR)
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, value)
    }

  override var genre: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_GENRE).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_GENRE, value)
    }

  override var trackNumber
    get() = compat.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER)
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, value)
    }

  override var trackCount
    get() = compat.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS)
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, value)
    }

  override var discNumber
    get() = compat.getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER)
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, value)
    }

  override var albumArtist: ArtistName
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST).toArtistName()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, value.value)
    }

  override var art: Bitmap?
    get() = compat.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
    set(value) {
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, value)
    }

  override var artUri: Uri
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_ART_URI).toUriOrEmpty()
    set(value) {
      builder.putUri(MediaMetadataCompat.METADATA_KEY_ART_URI, value)
    }

  override var albumArt: Bitmap?
    get() = compat.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
    set(value) {
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, value)
    }

  override var albumArtUri: Uri
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI).toUriOrEmpty()
    set(value) {
      builder.putUri(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, value)
    }

  override var userRating: StarRating
    get() = compat.getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING).toStarRating()
    set(value) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING, value.toRatingCompat())
    }

  override var rating: StarRating
    get() = compat.getRating(MediaMetadataCompat.METADATA_KEY_RATING).toStarRating()
    set(value) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_RATING, value.toRatingCompat())
    }

  override var displayTitle: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, value)
    }

  override var displaySubtitle: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, value)
    }

  override var displayDescription: String
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION).orEmpty()
    set(value) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, value)
    }

  override var displayIcon: Bitmap?
    get() = compat.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
    set(value) {
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, value)
    }

  override var displayIconUri: Uri
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI).toUriOrEmpty()
    set(value) {
      builder.putUri(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, value)
    }

  override var mediaUri: Uri
    get() = compat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUriOrEmpty()
    set(value) {
      builder.putUri(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, value)
    }

  override var downloadStatus
    get() = compat.getLong(MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS)
    set(value) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS, value)
    }

  override fun edit(block: MutableMediaMetadata.() -> Unit) {
    block()
    compat = builder.build()
  }
}

private fun RatingCompat.toStarRating(): StarRating {
  if (!isRated) StarRating.STAR_NONE
  return when (ratingStyle) {
    RATING_HEART -> if (hasHeart()) StarRating.STAR_5 else StarRating.STAR_NONE
    RATING_THUMB_UP_DOWN -> if (isThumbUp) StarRating.STAR_5 else StarRating.STAR_NONE
    RATING_3_STARS -> STAR_3_RANGE.convert(starRating.coerceIn(STAR_3_RANGE))
    RATING_4_STARS -> STAR_4_RANGE.convert(starRating.coerceIn(STAR_4_RANGE))
    RATING_5_STARS -> StarRating(starRating.coerceIn(STAR_5_RANGE))
    RATING_PERCENTAGE -> Rating(percentRating.toInt().coerceIn(PERCENT_RANGE)).toStarRating()
    else -> StarRating.STAR_NONE
  }
}

private fun MediaMetadataCompat.Builder.putUri(key: String, value: Uri) {
  putString(key, value.toString())
}

private fun ClosedFloatingPointRange<Float>.convert(number: Float): StarRating {
  val ratio = number / (endInclusive - start)
  return StarRating(
    (ratio * (STAR_5_RANGE.endInclusive - STAR_5_RANGE.start)).coerceIn(STAR_5_RANGE)
  )
}

const val MAX_3_STAR = 3f
const val MAX_4_STAR = 4f
const val MAX_5_STAR = 5f
const val MAX_PERCENTAGE = 100
val STAR_3_RANGE = 0f..MAX_3_STAR
val STAR_4_RANGE = 0f..MAX_4_STAR
val STAR_5_RANGE = 0f..MAX_5_STAR
val PERCENT_RANGE = 0..MAX_PERCENTAGE
