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
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_AUTHOR
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_COMPOSER
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DATE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISC_NUMBER
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_GENRE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_NUM_TRACKS
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_RATING
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_WRITER
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_YEAR
import android.support.v4.media.RatingCompat
import com.ealva.toque.common.Millis
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.coerceToValid

inline fun buildMetadata(
  builderAction: MediaMetadataCompat.Builder.() -> Unit
): MediaMetadataCompat = MediaMetadataCompat.Builder().apply(builderAction).build()

val NOTHING_PLAYING: MediaMetadata = MediaMetadata().apply {
  edit {
    id = ""
    duration = Millis.ZERO
  }
}

inline fun MediaMetadataCompat.Builder.mediaItemId(mediaId: () -> PersistentId) {
  mediaItemIdString { MediaSessionBrowser.makeMediaId(mediaId()) }
}

inline fun MediaMetadataCompat.Builder.mediaItemIdString(mediaId: () -> String) {
  putString(METADATA_KEY_MEDIA_ID, mediaId())
}

inline fun MediaMetadataCompat.Builder.mediaUri(mediaUri: () -> Uri) {
  val uri = mediaUri()
  if (uri != Uri.EMPTY) putString(METADATA_KEY_MEDIA_URI, uri.toString())
}

inline fun MediaMetadataCompat.Builder.title(text: () -> String) {
  putString(METADATA_KEY_TITLE, text())
}

inline fun MediaMetadataCompat.Builder.artist(text: () -> String) {
  putString(METADATA_KEY_ARTIST, text())
}

inline fun MediaMetadataCompat.Builder.album(text: () -> String) {
  putString(METADATA_KEY_ALBUM, text())
}

inline fun MediaMetadataCompat.Builder.author(text: () -> String) {
  putString(METADATA_KEY_AUTHOR, text())
}

inline fun MediaMetadataCompat.Builder.writer(text: () -> String) {
  putString(METADATA_KEY_WRITER, text())
}

inline fun MediaMetadataCompat.Builder.composer(text: () -> String) {
  putString(METADATA_KEY_COMPOSER, text())
}

inline fun MediaMetadataCompat.Builder.date(text: () -> String) {
  putString(METADATA_KEY_DATE, text())
}

inline fun MediaMetadataCompat.Builder.genre(text: () -> String) {
  putString(METADATA_KEY_GENRE, text())
}

inline fun MediaMetadataCompat.Builder.albumArtist(text: () -> String) {
  putString(METADATA_KEY_ALBUM_ARTIST, text())
}

inline fun MediaMetadataCompat.Builder.artUri(artUri: () -> Uri) {
  val uri = artUri()
  if (uri != Uri.EMPTY) putString(METADATA_KEY_ART_URI, uri.toString())
}

inline fun MediaMetadataCompat.Builder.albumArtUri(artUri: () -> Uri) {
  val uri = artUri()
  if (uri != Uri.EMPTY) putString(METADATA_KEY_ALBUM_ART_URI, uri.toString())
}

inline fun MediaMetadataCompat.Builder.displayTitle(text: () -> String) {
  putString(METADATA_KEY_DISPLAY_TITLE, text())
}

inline fun MediaMetadataCompat.Builder.displaySubtitle(text: () -> String) {
  putString(METADATA_KEY_DISPLAY_SUBTITLE, text())
}

inline fun MediaMetadataCompat.Builder.displayDescription(text: () -> String) {
  putString(METADATA_KEY_DISPLAY_DESCRIPTION, text())
}

inline fun MediaMetadataCompat.Builder.displayIconUri(iconUri: () -> Uri) {
  val uri = iconUri()
  if (uri != Uri.EMPTY) putString(METADATA_KEY_DISPLAY_ICON_URI, uri.toString())
}

/*
METADATA_KEY_BT_FOLDER_TYPE
METADATA_KEY_ADVERTISEMENT
METADATA_KEY_DOWNLOAD_STATUS
 */
inline fun MediaMetadataCompat.Builder.duration(duration: () -> Millis) {
  putLong(METADATA_KEY_DURATION, duration().value)
}

inline fun MediaMetadataCompat.Builder.track(trackNumber: () -> Long) {
  putLong(METADATA_KEY_TRACK_NUMBER, trackNumber())
}

inline fun MediaMetadataCompat.Builder.trackCount(totalTracks: () -> Long) {
  putLong(METADATA_KEY_NUM_TRACKS, totalTracks())
}

inline fun MediaMetadataCompat.Builder.disc(discNumber: () -> Long) {
  putLong(METADATA_KEY_DISC_NUMBER, discNumber())
}

inline fun MediaMetadataCompat.Builder.year(year: () -> Long) {
  putLong(METADATA_KEY_YEAR, year())
}

inline fun MediaMetadataCompat.Builder.rating(rating: () -> StarRating) {
  putRating(METADATA_KEY_RATING, rating().toRatingCompat())
}

fun StarRating.toRatingCompat(): RatingCompat = if (isValid) {
  RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, coerceToValid().value)
} else {
  RatingCompat.newUnratedRating(RatingCompat.RATING_5_STARS)
}

inline fun MediaMetadataCompat.Builder.art(bitmap: () -> Bitmap) {
  putBitmap(METADATA_KEY_ART, bitmap())
}

inline fun MediaMetadataCompat.Builder.albumArt(bitmap: () -> Bitmap) {
  putBitmap(METADATA_KEY_ALBUM_ART, bitmap())
}

inline fun MediaMetadataCompat.Builder.displayIcon(bitmap: () -> Bitmap) {
  putBitmap(METADATA_KEY_DISPLAY_ICON, bitmap())
}
