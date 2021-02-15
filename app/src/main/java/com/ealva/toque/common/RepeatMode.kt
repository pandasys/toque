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
import com.ealva.toque.res.HasDescription

enum class RepeatMode(
  override val id: Int,
  @StringRes override val stringRes: Int
) : HasConstId, HasDescription {
  None(1, R.string.RepeatOff),
  All(2, R.string.RepeatQueue),
  One(3, R.string.RepeatCurrent);

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
