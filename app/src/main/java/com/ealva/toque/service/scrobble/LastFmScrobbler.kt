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
import com.ealva.toque.common.PackageName
import com.ealva.toque.common.debug
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItem

private val LOG by lazyLogger(LastFmScrobbler::class)
private const val ACTION_META_CHANGED = "fm.last.android.metachanged"
private const val ACTION_PLAYBACK_COMPLETE = "fm.last.android.playbackcomplete"
private const val ACTION_PLAY_STATE_CHANGED =
  "fm.last.android.playstatechanged" // "com.android.music.playstatechanged"

//    private val ACTION_STATION_CHANGED = "fm.last.android.stationchanged"
//    private val ACTION_PLAYBACK_ERROR = "fm.last.android.playbackerror"
//    private val ACTION_PLAYBACK_PAUSED = "fm.last.android.playbackpaused"
//    private val ACTION_UNKNOWN = "fm.last.android.unknown"
//    private val ACTION_LOVE = "fm.last.android.LOVE"
//    private val ACTION_BAN = "fm.last.android.BAN"
private const val EXTRA_TRACK = "track"
private const val EXTRA_ARTIST = "artist"
private const val EXTRA_ALBUM = "album"
private const val EXTRA_DURATION = "duration"
private const val EXTRA_POSITION = "position"
private const val EXTRA_PLAYING = "playing"
private const val EXTRA_PLAYER = "player"

/**
 * Scrobble to the last.fm android app
 */
internal class LastFmScrobbler(
  private val pkgName: PackageName,
  private val intentBroadcaster: IntentBroadcaster
) : Scrobbler {
  private var lastItem: PlayableAudioItem = NullPlayableAudioItem

  override fun start(item: PlayableAudioItem) {
    broadcastIntent(ACTION_META_CHANGED, item, item.isPlaying)
  }

  override fun resume(item: PlayableAudioItem) {
    broadcastIntent(ACTION_PLAY_STATE_CHANGED, item, true)
  }

  override fun pause(item: PlayableAudioItem) {
    broadcastIntent(ACTION_PLAY_STATE_CHANGED, item, false)
  }

  override fun complete(item: PlayableAudioItem) {
    if (item == lastItem) {
      broadcastIntent(ACTION_PLAYBACK_COMPLETE, item, false)
    }
  }

  override fun shutdown() {
    if (lastItem !== NullPlayableAudioItem) {
      pause(lastItem)
      lastItem = NullPlayableAudioItem
    }
  }

  private fun broadcastIntent(action: String, item: PlayableAudioItem, isPlaying: Boolean) {
    lastItem = item
    val intent = Intent(action)
      .putExtra(EXTRA_TRACK, item.title.value)
      .putExtra(EXTRA_ARTIST, item.albumArtist.value)
      .putExtra(EXTRA_ALBUM, item.albumTitle.value)
      .putExtra(EXTRA_DURATION, item.duration.value)
      .putExtra(EXTRA_POSITION, item.position.value)
      .putExtra(EXTRA_PLAYING, isPlaying)
      .putExtra(EXTRA_PLAYER, pkgName.prop)
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
      append(intent.getLongExtra(EXTRA_DURATION, -1L))
      append(", position=")
      append(intent.getLongExtra(EXTRA_POSITION, -1L))
      append(", isPlaying=")
      append(intent.getBooleanExtra(EXTRA_PLAYING, false))
      append(", player=")
      append(intent.getStringExtra(EXTRA_PLAYER))
    }
    LOG.i { it(msg) }
  }
}
