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

package com.ealva.toque.common

import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle

/**
 * Describes the state of "shuffle"
 */
enum class ShuffleMode(
  override val id: Int,
  /** Should the media in a list be randomly shuffled */
  val shuffleMedia: Boolean,
  /** Should list selection be random */
  val shuffleLists: Boolean,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle {
  None(1, false, false, R.string.ShuffleNone),
  Media(2, true, false, R.string.ShuffleMedia),
  Lists(3, false, true, R.string.ShuffleLists),
  MediaAndLists(4, true, true, R.string.ShuffleMediaAndLists);

  /**
   * This function is useful when the user presses a "shuffle" button and the value needs to be
   * rotated to the next.
   */
  fun getNext(): ShuffleMode = when (this) {
    None -> Media
    Media -> Lists
    Lists -> MediaAndLists
    MediaAndLists -> None
  }
}

fun Int.compatToShuffleMode(): ShuffleMode = when (this) {
  PlaybackStateCompat.SHUFFLE_MODE_NONE -> ShuffleMode.None
  PlaybackStateCompat.SHUFFLE_MODE_ALL -> ShuffleMode.MediaAndLists
  PlaybackStateCompat.SHUFFLE_MODE_GROUP -> ShuffleMode.Lists
  else -> ShuffleMode.None
}

val ShuffleMode.asCompat
  get() = when (this) {
    ShuffleMode.None -> PlaybackStateCompat.SHUFFLE_MODE_NONE
    ShuffleMode.Media -> PlaybackStateCompat.SHUFFLE_MODE_ALL
    ShuffleMode.Lists -> PlaybackStateCompat.SHUFFLE_MODE_GROUP
    ShuffleMode.MediaAndLists -> PlaybackStateCompat.SHUFFLE_MODE_ALL
  }
