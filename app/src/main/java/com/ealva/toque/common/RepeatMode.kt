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

@Parcelize
enum class RepeatMode(
  override val id: Int,
  val current: Boolean,
  val queue: Boolean,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle, Parcelable {
  None(1, false, false, R.string.RepeatOff),
  All(2, false, true, R.string.RepeatQueue),
  One(3, true, false, R.string.RepeatCurrent);

  fun isOn(): Boolean = this !== None

  fun getNext(): RepeatMode = when (this) {
    None -> All
    All -> One
    One -> None
  }
}

fun Int.compatToRepeatMode(): RepeatMode = when (this) {
  PlaybackStateCompat.REPEAT_MODE_NONE -> RepeatMode.None
  PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.One
  PlaybackStateCompat.REPEAT_MODE_ALL -> RepeatMode.All
  PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.All
  else -> RepeatMode.None
}

val RepeatMode.asCompat
  get() = when (this) {
    RepeatMode.None -> PlaybackStateCompat.REPEAT_MODE_NONE
    RepeatMode.All -> PlaybackStateCompat.REPEAT_MODE_ALL
    RepeatMode.One -> PlaybackStateCompat.REPEAT_MODE_ONE
  }

val RepeatMode.drawable: Int
  @DrawableRes get() = when (this) {
    RepeatMode.None -> R.drawable.ic_repeat_off
    RepeatMode.All -> R.drawable.ic_repeat
    RepeatMode.One -> R.drawable.ic_repeat_once
  }

