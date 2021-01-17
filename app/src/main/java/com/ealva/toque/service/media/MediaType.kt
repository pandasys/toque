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

import com.ealva.toque.persist.HasConstId

fun Int.toMediaType(): MediaType = when (this) {
  MediaType.Unknown.id -> MediaType.Unknown
  MediaType.Video.id -> MediaType.Video
  MediaType.Audio.id -> MediaType.Audio
  MediaType.Group.id -> MediaType.Group
  MediaType.Dir.id -> MediaType.Dir
  MediaType.Subtitle.id -> MediaType.Subtitle
  MediaType.Playlist.id -> MediaType.Playlist
  MediaType.Image.id -> MediaType.Image
  else -> MediaType.Invalid
}

enum class MediaType(override val id: Int) : HasConstId {
  Unknown(-1),
  Video(0),
  Audio(1),
  Group(2),
  Dir(3),
  Subtitle(4),
  Playlist(5),
  Image(6),
  Invalid(Integer.MIN_VALUE);

  inline val isAudio: Boolean
    get() = this === Audio

  inline val isVideo: Boolean
    get() = this === Video

  inline val hasAudio: Boolean
    get() = isAudio || isVideo

  inline val isValid: Boolean
    get() = this !== Invalid

  inline val isNotValid: Boolean
    get() = this === Invalid
}
