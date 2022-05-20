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

package com.ealva.toque.common

import android.os.Parcelable
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle
import kotlinx.parcelize.Parcelize
import javax.annotation.CheckReturnValue

/**
 * Describes the state of "shuffle"
 */
@Parcelize
enum class ShuffleMode(
  override val id: Int,
  /** Should the media in a list be randomly shuffled */
  private val media: Boolean,
  /** Should list selection be random */
  private val lists: Boolean,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle, Parcelable {
  None(1, false, false, R.string.ShuffleOff),
  Media(2, true, false, R.string.ShuffleMedia),
  Lists(3, false, true, R.string.ShuffleCategories),
  MediaAndLists(4, true, true, R.string.ShuffleMediaAndCategories);

  val shuffleMedia: ShuffleMedia get() = ShuffleMedia(media)
  val shuffleLists: ShuffleLists get() = ShuffleLists(lists)

  fun isOn(): Boolean = this !== None

  @CheckReturnValue
  fun ensureShuffleMedia(): ShuffleMode = if (!media) getNext() else this

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

  fun shuffleListsDiffers(other: ShuffleMode): Boolean = lists != other.lists

  fun shuffleMediaDiffers(other: ShuffleMode): Boolean = media != other.media
}

fun Int.compatToShuffleMode(): ShuffleMode = when (this) {
  PlaybackStateCompat.SHUFFLE_MODE_NONE -> ShuffleMode.None
  PlaybackStateCompat.SHUFFLE_MODE_ALL -> ShuffleMode.Media
  PlaybackStateCompat.SHUFFLE_MODE_GROUP -> ShuffleMode.Lists
  else -> ShuffleMode.None
}

val ShuffleMode.asCompat
  get() = when (this) {
    ShuffleMode.None -> PlaybackStateCompat.SHUFFLE_MODE_NONE
    ShuffleMode.Media -> PlaybackStateCompat.SHUFFLE_MODE_ALL
    ShuffleMode.Lists -> PlaybackStateCompat.SHUFFLE_MODE_GROUP
    ShuffleMode.MediaAndLists -> PlaybackStateCompat.SHUFFLE_MODE_GROUP
  }

val ShuffleMode.drawable: Int
  @DrawableRes get() = when (this) {
    ShuffleMode.None -> R.drawable.ic_shuffle_disabled
    ShuffleMode.Media -> R.drawable.ic_shuffle_media
    ShuffleMode.Lists -> R.drawable.ic_shuffle_lists
    ShuffleMode.MediaAndLists -> R.drawable.ic_shuffle_both
  }

@JvmInline
value class ShuffleMedia(val value: Boolean) {
  inline val shuffle: Boolean
    get() = value

  @Suppress("unused")
  inline val doNotShuffle: Boolean
    get() = !value
}

@JvmInline
value class ShuffleLists(val value: Boolean) {
  inline val shuffle: Boolean
    get() = value

  @Suppress("unused")
  inline val doNotShuffle: Boolean
    get() = !value
}
