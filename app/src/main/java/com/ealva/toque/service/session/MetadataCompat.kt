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

@file:Suppress("unused")

package com.ealva.toque.service.session

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvabrainz.common.toAlbumTitle
import com.ealva.ealvabrainz.common.toArtistName
import com.ealva.ealvabrainz.common.toComposerName
import com.ealva.ealvabrainz.common.toGenreName
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toTitle
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toStarRating

/** Returns Media ID if set else [PersistentId.INVALID] */
inline val MediaMetadataCompat.id: PersistentId
  get() = getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toPersistentId()

/** Returns the Title if set else [Title.UNKNOWN] */
inline val MediaMetadataCompat.title: Title
  get() = getString(MediaMetadataCompat.METADATA_KEY_TITLE).toTitle()

/** Returns the Artist name if set else [ArtistName.UNKNOWN] */
inline val MediaMetadataCompat.artist: ArtistName
  get() = getString(MediaMetadataCompat.METADATA_KEY_ARTIST).toArtistName()

/** Returns Duration in Millis if set else Millis(0L) */
inline val MediaMetadataCompat.duration: Millis
  get() = Millis(getLong(MediaMetadataCompat.METADATA_KEY_DURATION))

/** Returns the Album title if set else [AlbumTitle.UNKNOWN] */
inline val MediaMetadataCompat.album: AlbumTitle
  get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM).toAlbumTitle()

inline val MediaMetadataCompat.author: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_AUTHOR)

inline val MediaMetadataCompat.writer: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_WRITER)

/** Returns Composer name if exists else [ComposerName.UNKNOWN] */
inline val MediaMetadataCompat.composer: ComposerName
  get() = getString(MediaMetadataCompat.METADATA_KEY_COMPOSER).toComposerName()

inline val MediaMetadataCompat.compilation: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_COMPILATION)

inline val MediaMetadataCompat.date: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_DATE)

inline val MediaMetadataCompat.year: Long
  get() = getLong(MediaMetadataCompat.METADATA_KEY_YEAR)

/** Returns Genre name if set else [GenreName.UNKNOWN] */
inline val MediaMetadataCompat.genre: GenreName
  get() = getString(MediaMetadataCompat.METADATA_KEY_GENRE).toGenreName()

inline val MediaMetadataCompat.trackNumber: Long
  get() = getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER)

inline val MediaMetadataCompat.trackCount: Long
  get() = getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS)

inline val MediaMetadataCompat.discNumber: Long
  get() = getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER)

/** Returns the Album Artist name if set else [ArtistName.UNKNOWN] */
inline val MediaMetadataCompat.albumArtist: ArtistName
  get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST).toArtistName()

inline val MediaMetadataCompat.art: Bitmap?
  get() = getBitmap(MediaMetadataCompat.METADATA_KEY_ART)

/** Returns Art URI if set else [Uri.EMPTY] */
inline val MediaMetadataCompat.artUri: Uri
  get() = getString(MediaMetadataCompat.METADATA_KEY_ART_URI).toUriOrEmpty()

inline val MediaMetadataCompat.albumArt: Bitmap?
  get() = getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)

/** Returns Album Art URI if set else [Uri.EMPTY] */
inline val MediaMetadataCompat.albumArtUri: Uri
  get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI).toUriOrEmpty()

inline val MediaMetadataCompat.userRating: StarRating
  get() = getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING).toStarRating()

inline val MediaMetadataCompat.rating
  get() = getRating(MediaMetadataCompat.METADATA_KEY_RATING).toStarRating()

inline val MediaMetadataCompat.displayTitle: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)

inline val MediaMetadataCompat.displaySubtitle: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)

inline val MediaMetadataCompat.displayDescription: String?
  get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)

inline val MediaMetadataCompat.displayIcon: Bitmap?
  get() = getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)

/** Display Icon URI if set else Uri.EMPTY */
inline val MediaMetadataCompat.displayIconUri: Uri
  get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI).toUriOrEmpty()

/** Media location URI if set else Uri.EMPTY */
inline val MediaMetadataCompat.mediaUri: Uri
  get() = getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUriOrEmpty()

inline val MediaMetadataCompat.downloadStatus
  get() = getLong(MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS)


const val MAX_3_STAR = 3f
const val MAX_4_STAR = 4f
const val MAX_5_STAR = 5f
const val MAX_PERCENTAGE = 100
val STAR_3_RANGE = 0f..MAX_3_STAR
val STAR_4_RANGE = 0f..MAX_4_STAR
val STAR_5_RANGE = 0f..MAX_5_STAR
val PERCENT_RANGE = 0..MAX_PERCENTAGE
fun RatingCompat.toStarRating(): StarRating {
  if (!isRated) StarRating.STAR_NONE
  return when (ratingStyle) {
    RatingCompat.RATING_HEART -> if (hasHeart()) StarRating.STAR_5 else StarRating.STAR_NONE
    RatingCompat.RATING_THUMB_UP_DOWN -> if (isThumbUp) StarRating.STAR_5 else StarRating.STAR_NONE
    RatingCompat.RATING_3_STARS -> STAR_3_RANGE.convert(starRating.coerceIn(STAR_3_RANGE))
    RatingCompat.RATING_4_STARS -> STAR_4_RANGE.convert(starRating.coerceIn(STAR_4_RANGE))
    RatingCompat.RATING_5_STARS -> StarRating(starRating.coerceIn(STAR_5_RANGE))
    RatingCompat.RATING_PERCENTAGE -> Rating(percentRating.toInt().coerceIn(PERCENT_RANGE)).toStarRating()
    else -> StarRating.STAR_NONE
  }
}

private fun ClosedFloatingPointRange<Float>.convert(number: Float): StarRating {
  val ratio = number / (endInclusive - start)
  return StarRating(
    (ratio * (STAR_5_RANGE.endInclusive - STAR_5_RANGE.start)).coerceIn(STAR_5_RANGE)
  )
}
