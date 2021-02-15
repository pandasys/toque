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

package com.ealva.toque.service.session

import android.support.v4.media.session.PlaybackStateCompat
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._i
import com.ealva.toque.service.session.PlaybackActions.Action.PlayFromMediaId
import com.ealva.toque.service.session.PlaybackActions.Action.PlayFromSearch
import com.ealva.toque.service.session.PlaybackActions.Action.PlayFromUri
import com.ealva.toque.service.session.PlaybackActions.Action.PlayPause
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap

private val LOG by lazyLogger(PlaybackActions::class)

@Suppress("NOTHING_TO_INLINE")
private inline fun Long.add(action: PlaybackActions.Action): Long = this or action.value

class PlaybackActions(
  initial: List<Action> = listOf(PlayFromSearch, PlayFromMediaId, PlayFromUri, PlayPause)
) {
  private var actionFlags: Long = 0L

  init {
    addAll(initial)
  }

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

  fun add(action: Action) {
    LOG._i { it("add flag ${action.value.flagsToString()}") }
    actionFlags = actionFlags.add(action)
  }

  fun addAll(vararg actions: Action) {
    addAll(actions.toList())
  }

  private fun addAll(list: List<Action>) {
    list.forEach { add(it) }
  }

  /** Suitable to pass to [PlaybackStateCompat.Builder.setActions] */
  val asCompat: Long
    get() = actionFlags

  override fun toString(): String = actionFlags.flagsToString()

  private fun Long.flagsToString() = actionNameMap
    .long2ReferenceEntrySet()
    .asSequence()
    .mapNotNull { entry -> if (this and entry.longKey > 0) entry.value else null }
    .joinToString()

  companion object {
    val actionNameMap: Long2ReferenceMap<String> by lazy {
      Long2ReferenceLinkedOpenHashMap<String>().apply {
        defaultReturnValue("UNKNOWN")
        Action.values().forEach { action ->
          put(action.value, action.name)
        }
      }
    }
  }
}
