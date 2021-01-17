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

package com.ealva.toque.service.media

import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.toque.common.Millis

interface MediaMetadata : AutoCloseable {
  val duration: Millis
  val title: String
  val titleSort: String
  val artists: List<String>
  val artistsSort: List<String>
  val album: String
  val albumSort: String
  val albumArtist: String
  val albumArtistSort: String
  val composer: String
  val composerSort: String
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
}
