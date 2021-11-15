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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.service.session.common

import android.support.v4.media.session.PlaybackStateCompat
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap

private val DEFAULT_ACTION_FLAGS = PlaybackActions.Action.Stop.value or
  PlaybackActions.Action.SetRating.value or
  PlaybackActions.Action.PlayFromMediaId.value or
  PlaybackActions.Action.PlayFromSearch.value or
  PlaybackActions.Action.PlayFromUri.value or
  PlaybackActions.Action.SetRepeatMode.value or
  PlaybackActions.Action.SetShuffleMode.value

data class PlaybackActions(private val actionFlags: Long) {

  @Suppress("unused")
  enum class Action(val value: Long) {
    Stop(PlaybackStateCompat.ACTION_STOP),
    Pause(PlaybackStateCompat.ACTION_PAUSE),
    Play(PlaybackStateCompat.ACTION_PLAY),
    Rewind(PlaybackStateCompat.ACTION_REWIND),
    SkipToPrevious(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS),
    SkipToNext(PlaybackStateCompat.ACTION_SKIP_TO_NEXT),
    FastForward(PlaybackStateCompat.ACTION_FAST_FORWARD),
    SetRating(PlaybackStateCompat.ACTION_SET_RATING),
    SeekTo(PlaybackStateCompat.ACTION_SEEK_TO),
    PlayPause(PlaybackStateCompat.ACTION_PLAY_PAUSE),
    PlayFromMediaId(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID),
    PlayFromSearch(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH),
    SkipToQueueItem(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM),
    PlayFromUri(PlaybackStateCompat.ACTION_PLAY_FROM_URI),
    Prepare(PlaybackStateCompat.ACTION_PREPARE),
    PrepareFromMediaId(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID),
    PrepareFromSearch(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH),
    PrepareFromUri(PlaybackStateCompat.ACTION_PREPARE_FROM_URI),
    SetRepeatMode(PlaybackStateCompat.ACTION_SET_REPEAT_MODE),
    SetShuffleMode(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE),
    SetCaptioningEnabled(PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED)
  }

  val hasSkipToNext: Boolean
    get() = (actionFlags and Action.SkipToNext.value) != 0L

  val hasSkipToPrevious: Boolean
    get() = (actionFlags and Action.SkipToPrevious.value) != 0L

  /** Suitable to pass to [PlaybackStateCompat.Builder.setActions] */
  val asCompat: Long
    get() = actionFlags

  override fun toString(): String = actionFlags.flagsToString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PlaybackActions) return false

    if (actionFlags != other.actionFlags) return false

    return true
  }

  override fun hashCode(): Int {
    return actionFlags.hashCode()
  }

  companion object {
    operator fun invoke(hasMedia: Boolean, isPlaying: Boolean, hasPrev: Boolean): PlaybackActions {
      var flags: Long = DEFAULT_ACTION_FLAGS
      if (hasMedia) flags = flags.addAll(Action.SkipToQueueItem, Action.PlayPause)
      if (hasPrev) flags = flags.add(Action.SkipToPrevious)
      if (hasMedia) flags = flags.add(Action.SkipToNext)
      if (isPlaying) {
        flags = flags.addAll(Action.Pause, Action.Rewind, Action.FastForward, Action.SeekTo)
      } else if (hasMedia) {
        flags = flags.addAll(Action.Play, Action.Rewind, Action.FastForward, Action.SeekTo)
      }
      return PlaybackActions(flags)
    }

    val DEFAULT = PlaybackActions(
      hasMedia = false,
      isPlaying = false,
      hasPrev = false
    )
  }
}

private inline fun Long.add(action: PlaybackActions.Action): Long = this or action.value

private inline fun Long.addAll(vararg actions: PlaybackActions.Action): Long {
  var flags = this
  actions.forEach { action -> flags = flags.add(action) }
  return flags
}

private fun Long.flagsToString() = actionNameMap
  .long2ReferenceEntrySet()
  .asSequence()
  .mapNotNull { entry -> if (this and entry.longKey > 0) entry.value else null }
  .joinToString()

private val actionNameMap: Long2ReferenceMap<String> by lazy {
  Long2ReferenceLinkedOpenHashMap<String>().apply {
    defaultReturnValue("UNKNOWN")
    PlaybackActions.Action.values().forEach { action ->
      put(action.value, action.name)
    }
  }
}
