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

package com.ealva.toque.service.scrobble

import android.content.Intent
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.android.content.IntentBroadcaster
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PackageName
import com.ealva.toque.common.debug
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.scrobble.SimpleLastFmState.Complete
import com.ealva.toque.service.scrobble.SimpleLastFmState.Pause
import com.ealva.toque.service.scrobble.SimpleLastFmState.Resume
import com.ealva.toque.service.scrobble.SimpleLastFmState.Start
import com.ealva.toque.service.scrobble.SimpleLastFmState.Unknown

private val LOG by lazyLogger(SimpleLastFmScrobbler::class)

private enum class SimpleLastFmState(val value: Int) {
  Unknown(Int.MIN_VALUE),
  Start(0),
  Resume(1),
  Pause(2),
  Complete(3);
}

private fun Int.toState(): SimpleLastFmState = when (this) {
  Start.value -> Start
  Resume.value -> Resume
  Pause.value -> Pause
  Complete.value -> Complete
  else -> Unknown
}

private const val ACTION_SIMPLE_LAST_FM = "com.adam.aslfms.notify.playstatechanged"
private const val EXTRA_STATE = "state"
private const val EXTRA_APP_NAME = "app-name"
private const val EXTRA_APP_PKG = "app-package"
private const val EXTRA_ARTIST = "artist"
private const val EXTRA_ALBUM = "album"
private const val EXTRA_TRACK = "track"
private const val EXTRA_DURATION = "duration"
private const val EXTRA_TRACK_NUMBER = "track-number"
private const val EXTRA_SOURCE = "source"

private const val MILLIS_PER_SECOND = 1000
private fun Millis.toSeconds(): Int = (value / MILLIS_PER_SECOND).toInt()

/**
 * Implementation of a scrobbler for Simple Last.fm
 */
internal class SimpleLastFmScrobbler(
  private val appName: String,
  private val pkgName: PackageName,
  private val intentBroadcaster: IntentBroadcaster
) : Scrobbler {
  private var lastItem: PlayableAudioItem = NullPlayableAudioItem

  override fun start(item: PlayableAudioItem) {
    scrobble(Start, item)
  }

  override fun resume(item: PlayableAudioItem) {
    scrobble(Resume, item)
  }

  override fun pause(item: PlayableAudioItem) {
    scrobble(Pause, item)
  }

  override fun complete(item: PlayableAudioItem) {
    if (item == lastItem) {
      scrobble(Complete, item)
    }
  }

  override fun shutdown() {
    if (lastItem !== NullPlayableAudioItem) {
      pause(lastItem)
      lastItem = NullPlayableAudioItem
    }
  }

  private fun scrobble(state: SimpleLastFmState, item: PlayableAudioItem) {
    lastItem = item
    val intent = Intent(ACTION_SIMPLE_LAST_FM)
      .putExtra(EXTRA_TRACK, item.title())
      .putExtra(EXTRA_ARTIST, item.albumArtist.value)
      .putExtra(EXTRA_ALBUM, item.albumTitle.value)
      .putExtra(EXTRA_DURATION, item.duration.toSeconds())
      .putExtra(EXTRA_TRACK_NUMBER, item.trackNumber)
      .putExtra(EXTRA_STATE, state.value)
      .putExtra(EXTRA_APP_NAME, appName)
      .putExtra(EXTRA_APP_PKG, pkgName.prop)
      .putExtra(EXTRA_SOURCE, "P")
    debug { logIntent(intent) }
    intentBroadcaster.broadcast(intent)
  }

  @Suppress("unused")
  private fun logIntent(intent: Intent) {
    val msg = buildString {
      append("Intent(action=")
      append(intent.action)
      append(", title=")
      append(intent.getStringExtra(EXTRA_TRACK))
      append(", artist=")
      append(intent.getStringExtra(EXTRA_ARTIST))
      append(", album=")
      append(intent.getStringExtra(EXTRA_ALBUM))
      append(", duration=")
      append(intent.getIntExtra(EXTRA_DURATION, -1))
      append(", trackNumber=")
      append(intent.getIntExtra(EXTRA_TRACK_NUMBER, -1))
      append(", state=")
      append(intent.getIntExtra(EXTRA_STATE, -1).toState().name)
      append(", appName=")
      append(intent.getStringExtra(EXTRA_APP_NAME))
      append(", appPkg")
      append(intent.getStringExtra(EXTRA_APP_PKG))
      append(", source=")
      append(intent.getStringExtra(EXTRA_SOURCE))
    }
    LOG.i { it(msg) }
  }
}
