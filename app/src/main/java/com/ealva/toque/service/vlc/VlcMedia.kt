/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.service.vlc

import com.ealva.toque.service.media.MediaState
import com.ealva.toque.service.media.MetadataField
import com.ealva.toque.service.media.ParsedStatus
import org.videolan.libvlc.interfaces.IMedia

private fun Int.toMetadataField(): MetadataField = when (this) {
  IMedia.Meta.Title -> MetadataField.Title
  IMedia.Meta.Artist -> MetadataField.Artist
  IMedia.Meta.Genre -> MetadataField.Genre
  IMedia.Meta.Copyright -> MetadataField.Copyright
  IMedia.Meta.Album -> MetadataField.Album
  IMedia.Meta.TrackNumber -> MetadataField.TrackNumber
  IMedia.Meta.Description -> MetadataField.Description
  IMedia.Meta.Rating -> MetadataField.Rating
  IMedia.Meta.Date -> MetadataField.Date
  IMedia.Meta.Setting -> MetadataField.Setting
  IMedia.Meta.URL -> MetadataField.URL
  IMedia.Meta.Language -> MetadataField.Language
  IMedia.Meta.NowPlaying -> MetadataField.NowPlaying
  IMedia.Meta.Publisher -> MetadataField.Publisher
  IMedia.Meta.EncodedBy -> MetadataField.EncodedBy
  IMedia.Meta.ArtworkURL -> MetadataField.ArtworkURL
  IMedia.Meta.TrackID -> MetadataField.TrackID
  IMedia.Meta.TrackTotal -> MetadataField.TrackTotal
  IMedia.Meta.Director -> MetadataField.Director
  IMedia.Meta.Season -> MetadataField.Season
  IMedia.Meta.Episode -> MetadataField.Episode
  IMedia.Meta.ShowName -> MetadataField.ShowName
  IMedia.Meta.Actors -> MetadataField.Actors
  IMedia.Meta.AlbumArtist -> MetadataField.AlbumArtist
  IMedia.Meta.DiscNumber -> MetadataField.DiscNumber
  else -> MetadataField.Unknown
}

private fun Int.toMediaState(): MediaState = when (this) {
  IMedia.State.NothingSpecial -> MediaState.NothingSpecial
  IMedia.State.Opening -> MediaState.Opening
  IMedia.State.Playing -> MediaState.Playing
  IMedia.State.Paused -> MediaState.Paused
  IMedia.State.Stopped -> MediaState.Stopped
  IMedia.State.Ended -> MediaState.Ended
  IMedia.State.Error -> MediaState.Error
  else -> MediaState.Unknown
}

private fun Int.toParsedStatus(): ParsedStatus = when (this) {
  IMedia.ParsedStatus.Skipped -> ParsedStatus.Skipped
  IMedia.ParsedStatus.Failed -> ParsedStatus.Failed
  IMedia.ParsedStatus.Timeout -> ParsedStatus.Timeout
  IMedia.ParsedStatus.Done -> ParsedStatus.Done
  else -> ParsedStatus.Unknown
}
