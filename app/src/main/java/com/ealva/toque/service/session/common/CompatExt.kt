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

import android.support.v4.media.RatingCompat
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.coerceToValid
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.session.common.IdPrefixes.ALBUM_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.ARTIST_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.COMPOSER_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.GENRE_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.MEDIA_PREFIX
import com.ealva.toque.service.session.common.IdPrefixes.PLAYLIST_PREFIX

fun StarRating.toRatingCompat(): RatingCompat = if (isValid) {
  RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, coerceToValid().value)
} else {
  RatingCompat.newUnratedRating(RatingCompat.RATING_5_STARS)
}

object IdPrefixes {
  const val MEDIA_PREFIX = "media"
  const val ALBUM_PREFIX = "album"
  const val ARTIST_PREFIX = "artist"
  const val GENRE_PREFIX = "genre"
  const val PLAYLIST_PREFIX = "playlist"
  const val COMPOSER_PREFIX = "composer"
}

/**
 * Converts the specific PersistentId type to a string or throws [IllegalArgumentException] if
 * a new PersistentId type has been introduced and this function has not been updated.
 *
 * Note: calling this function causes boxing
 */
fun PersistentId<*>.toCompatMediaId(): String {
  val prefix = when (this) {
    is MediaId -> MEDIA_PREFIX
    is AlbumId -> ALBUM_PREFIX
    is ArtistId -> ARTIST_PREFIX
    is GenreId -> GENRE_PREFIX
    is PlaylistId -> PLAYLIST_PREFIX
    is ComposerId -> COMPOSER_PREFIX
    else -> throw IllegalArgumentException("Unrecognized PersistentId type for $this")
  }
  return "${prefix}_$value"
}

//fun logInvalidMediaId(id: String?) {
//  LOG._e { it("Cannot parse Media ID '%s'", id ?: "null") }
//}
//
//inline fun String?.handleAsMediaId(
//  onMediaId: (MediaId) -> Unit = {},
//  onArtistId: (ArtistId) -> Unit = {},
//  onAlbumId: (AlbumId) -> Unit = {},
//  onGenreId: (GenreId) -> Unit = {},
//  onComposerId: (ComposerId) -> Unit = {},
//  onPlaylistId: (PlaylistId) -> Unit = {},
//  onInvalid: (String?) -> Unit = ::logInvalidMediaId
//) {
//  if (this == null) onInvalid(this) else {
//    try {
//      val list = split('_')
//      when (list.size) {
//        2 -> {
//          val id = list[1].toLong()
//          when (list[0]) {
//            MEDIA_PREFIX -> onMediaId(MediaId(id))
//            ARTIST_PREFIX -> onArtistId(ArtistId(id))
//            ALBUM_PREFIX -> onAlbumId(AlbumId(id))
//            GENRE_PREFIX -> onGenreId(GenreId(id))
//            COMPOSER_PREFIX -> onComposerId(ComposerId(id))
//            PLAYLIST_PREFIX -> onPlaylistId(PlaylistId(id))
//            else -> onInvalid(this)
//          }
//        }
//        else -> onMediaId(MediaId(toLong()))
//      }
//    } catch (e: Exception) {
//      onInvalid(this)
//    }
//  }
//}

/**
 * Converts this String to a specific PersistentId type if the prefix is recognized or to a MediaId
 * if it's all digits. If this String is null returns [PersistentId.INVALID].
 *
 * It's expected that the String will have a prefix indicating it's type followed by an underscore
 * '_' character and end in a number which may be parsed as a long. If no prefix and all digits,
 * returns a [MediaId]. Throws an [IllegalArgumentException] if the prefix unrecognized. If the
 * number part after the underscore is missing or cannot be parsed as a long, the returned
 * persistent ID will have a value of -1.
 *
 * Note: This function causes boxing as it returns the base interface
 */
fun String?.toPersistentId(): PersistentId<*> {
  if (this == null) return PersistentId.INVALID
  val list = split('_')
  return when {
    list.size == 2 -> {
      val id = list[1].toLongOrNull() ?: -1
      when (list[0]) {
        MEDIA_PREFIX -> MediaId(id)
        ARTIST_PREFIX -> ArtistId(id)
        ALBUM_PREFIX -> AlbumId(id)
        GENRE_PREFIX -> GenreId(id)
        COMPOSER_PREFIX -> ComposerId(id)
        PLAYLIST_PREFIX -> PlaylistId(id)
        else -> throw IllegalArgumentException("Unrecognized MediaId:$this")
      }
    }
    isNumeric() -> MediaId(toLong())
    else -> throw IllegalArgumentException("Unrecognized MediaId:$this")
  }
}

fun String?.isNumeric(): Boolean = if (!isNullOrEmpty()) all { c -> c.isDigit() } else false
