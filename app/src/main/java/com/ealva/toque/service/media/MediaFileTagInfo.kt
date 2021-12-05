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

package com.ealva.toque.service.media

import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.toque.common.Millis

interface MediaFileTagInfo : AutoCloseable {
  val duration: Millis
  val title: String
  val titleSort: String

  /**
   * An artist in a file Tag can be a comma delimited list of some sort. There can also be multiple
   * artist fields in a tag. This is all of the tag fields, which also may be further parsed into
   * separate strings.  TODO - clarify implementation
   */
  val artists: List<String>

  /**
   * This field is very much like [artists] in it's construction. If any artist does not have a
   * sort, one is created from the artist itself. TODO - clarify implementation
   */
  val artistsSort: List<String>
  val album: String
  val albumSort: String
  val albumArtist: String
  val albumArtistSort: String
  val composer: String
  val composerSort: String
  val genre: String
  val genres: List<String>
  val trackNumber: Int
  val totalTracks: Int
  val discNumber: Int
  val totalDiscs: Int
  val year: Int
  val rating: StarRating
  val comment: String
  val lyrics: String
  val artistMbid: ArtistMbid?
  val releaseArtistMbid: ArtistMbid?
  val releaseMbid: ReleaseMbid?
  val trackMbid: TrackMbid?
  val releaseGroupMbid: ReleaseGroupMbid?
  val language: String
  val copyright: String
  val description: String
  val setting: String
  val nowPlaying: String
  val publisher: String
  val encodedBy: String
  val director: String
  val season: String
  val episode: String
  val showName: String
  val actors: String
  val fullDescription: String
  val embeddedArtwork: EmbeddedArtwork // read tag can ignore artwork, then this is empty
}

interface EmbeddedArtwork {
  /** If [exists] is false there is no embedded data - other fields have no data */
  val exists: Boolean

  /** Is the artwork a Url - a pointer to artwork on the Internet */
  val isUrl: Boolean

  /** Does this contain binary data which can be converted to a bitmap */
  val isBinary: Boolean

  /** The data if [isBinary] is true, otherwise a zero length ByteArray */
  val data: ByteArray

  /** The data url if [isUrl] is true, otherwise an empty string */
  val url: String

  /** Describes artwork type if available - front cover, back cover, etc. */
  val pictureType: String
}

val EmbeddedArtwork.asString: String
  get() = "exists=$exists isUrl=$isUrl isBinary=$isBinary pictureType=$pictureType"
