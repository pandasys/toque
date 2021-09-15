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

import android.support.v4.media.session.PlaybackStateCompat

enum class PlayState(private val compat: Int) {
  None(PlaybackStateCompat.STATE_NONE),
  Stopped(PlaybackStateCompat.STATE_STOPPED),
  Playing(PlaybackStateCompat.STATE_PLAYING),
  Paused(PlaybackStateCompat.STATE_PAUSED),
  FastForwarding(PlaybackStateCompat.STATE_FAST_FORWARDING),
  Rewinding(PlaybackStateCompat.STATE_REWINDING),
  Buffering(PlaybackStateCompat.STATE_BUFFERING),
  Error(PlaybackStateCompat.STATE_ERROR),
  Connecting(PlaybackStateCompat.STATE_CONNECTING),
  SkippingToPrevious(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS),
  SkippingToNext(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT),
  SkippingToItem(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM);

  val isPlaying: Boolean
    get() = when (compat) {
      PlaybackStateCompat.STATE_PLAYING,
      PlaybackStateCompat.STATE_FAST_FORWARDING,
      PlaybackStateCompat.STATE_REWINDING,
      PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
      PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
      PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> true
      else -> false
    }

  val isStopped: Boolean
    get() = PlaybackStateCompat.STATE_STOPPED == compat

  operator fun invoke(): Int = compat
}
