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

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
import com.ealva.toque.common.Millis
import com.ealva.toque.common.toMillis
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.QueueType

private const val TEN_SECONDS = 10000L

val UiModeManager.inCarMode: Boolean
  get() = currentModeType == Configuration.UI_MODE_TYPE_CAR

class MediaSessionCallback(
  private val playerService: ToqueMediaController,
  private val uiModeManager: UiModeManager
) : MediaSessionCompat.Callback() {
  private var seeking = false

  override fun onPlay() {
    if (playerService.mediaIsLoaded) playerService.play(false)
    else playerService.setCurrentQueue(QueueType.Audio)
  }

  override fun onPause() = playerService.pause()

  override fun onStop() = playerService.stop()

  override fun onSkipToNext() = playerService.next()

  override fun onSkipToPrevious() = playerService.previous()

  override fun onSeekTo(pos: Long) =
    playerService.seekTo(if (pos < 0) playerService.position + pos else pos.toMillis())

  override fun onFastForward() = playerService.seekTo(
    (playerService.position + TEN_SECONDS).coerceAtMost(playerService.duration)
  )

  override fun onRewind() =
    playerService.seekTo((playerService.position - TEN_SECONDS).coerceAtLeast(Millis.ZERO))

  override fun onSkipToQueueItem(id: Long) = playerService.goToQueueIndexMaybePlay(id.toInt())

  override fun onCustomAction(action: String?, extras: Bundle?) {
    when (action) {
      "shuffle" -> playerService.nextShuffleMode()
      "repeat" -> playerService.nextRepeatMode()
    }
  }

  override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
    if (uri != null) playerService.loadUri(uri)
  }

  @Suppress("MaxLineLength")
  override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
//    playerService.lifecycleScope.launch {
//      val context = playerService.applicationContext
//      when {
//        mediaId == MediaSessionBrowser.ID_NO_MEDIA -> playerService.displayPlaybackError(R.string.search_no_result)
//        mediaId == MediaSessionBrowser.ID_NO_PLAYLIST -> playerService.displayPlaybackError(R.string.noplaylist)
//        mediaId == MediaSessionBrowser.ID_SHUFFLE_ALL -> {
//          val tracks = context.getFromMl { audio }
//          if (tracks.isNotEmpty() && isActive) {
//            loadMedia(tracks.toList(), Random().nextInt(min(tracks.size, MEDIALIBRARY_PAGE_SIZE)))
//            if (!playerService.isShuffling) playerService.shuffle()
//          } else {
//            playerService.displayPlaybackError(R.string.search_no_result)
//          }
//        }
//        mediaId == MediaSessionBrowser.ID_LAST_ADDED -> {
//          val tracks = context.getFromMl { recentAudio?.toList() }
//          if (!tracks.isNullOrEmpty() && isActive) {
//            val mediaList = tracks.subList(0, tracks.size.coerceAtMost(MediaSessionBrowser.MAX_HISTORY_SIZE))
//            loadMedia(mediaList)
//          }
//        }
//        mediaId == MediaSessionBrowser.ID_HISTORY -> {
//          val tracks = context.getFromMl { lastMediaPlayed()?.toList()?.filter { MediaSessionBrowser.isMediaAudio(it) } }
//          if (!tracks.isNullOrEmpty() && isActive) {
//            val mediaList = tracks.subList(0, tracks.size.coerceAtMost(MediaSessionBrowser.MAX_HISTORY_SIZE))
//            loadMedia(mediaList)
//          }
//        }
//        mediaId.startsWith(MediaSessionBrowser.ALBUM_PREFIX) -> {
//          val tracks = context.getFromMl { getAlbum(mediaId.extractId())?.tracks }
//          if (isActive) tracks?.let { loadMedia(it.toList()) }
//        }
//        mediaId.startsWith(MediaSessionBrowser.ARTIST_PREFIX) -> {
//          val tracks = context.getFromMl { getArtist(mediaId.extractId())?.tracks }
//          if (isActive) tracks?.let { loadMedia(it.toList()) }
//        }
//        mediaId.startsWith(MediaSessionBrowser.GENRE_PREFIX) -> {
//          val tracks = context.getFromMl { getGenre(mediaId.extractId())?.tracks }
//          if (isActive) tracks?.let { loadMedia(it.toList()) }
//        }
//        mediaId.startsWith(MediaSessionBrowser.PLAYLIST_PREFIX) -> {
//          val tracks = context.getFromMl { getPlaylist(mediaId.extractId())?.tracks }
//          if (isActive) tracks?.let { loadMedia(it.toList()) }
//        }
//        mediaId.startsWith(ExtensionsManager.EXTENSION_PREFIX) -> {
//          val id = mediaId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] + "_", "")
//          onPlayFromUri(id.toUri(), null)
//        }
//        else -> try {
//          context.getFromMl { getMedia(mediaId.toLong()) }?.let { if (isActive) loadMedia(listOf(it)) }
//        } catch (e: NumberFormatException) {
//          if (isActive) playerService.loadLocation(mediaId)
//        }
//      }
//    }
  }

  @Suppress("MaxLineLength")
  override fun onPlayFromSearch(query: String?, extras: Bundle?) {
//    playerService.mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, playerService.time, 1.0f).build())
//    playerService.lifecycleScope.launch(Dispatchers.IO) {
//      if (!isActive) return@launch
//      playerService.awaitMedialibraryStarted()
//      val vsp = VoiceSearchParams(query ?: "", extras)
//      var items: Array<out MediaLibraryItem>? = null
//      var tracks: Array<MediaWrapper>? = null
//      when {
//        vsp.isAny -> {
//          items = playerService.medialibrary.audio.also { if (!playerService.isShuffling) playerService.shuffle() }
//        }
//        vsp.isArtistFocus -> items = playerService.medialibrary.searchArtist(vsp.artist)
//        vsp.isAlbumFocus -> items = playerService.medialibrary.searchAlbum(vsp.album)
//        vsp.isGenreFocus -> items = playerService.medialibrary.searchGenre(vsp.genre)
//        vsp.isPlaylistFocus -> items = playerService.medialibrary.searchPlaylist(vsp.playlist)
//        vsp.isSongFocus -> tracks = playerService.medialibrary.searchMedia(vsp.song)
//      }
//      if (!isActive) return@launch
//      if (tracks.isNullOrEmpty() && items.isNullOrEmpty() && query?.length ?: 0 > 2) playerService.medialibrary.search(query)?.run {
//        when {
//          !albums.isNullOrEmpty() -> tracks = albums!![0].tracks
//          !artists.isNullOrEmpty() -> tracks = artists!![0].tracks
//          !playlists.isNullOrEmpty() -> tracks = playlists!![0].tracks
//          !genres.isNullOrEmpty() -> tracks = genres!![0].tracks
//        }
//      }
//      if (!isActive) return@launch
//      if (tracks.isNullOrEmpty() && !items.isNullOrEmpty()) tracks = items[0].tracks
//      if (!tracks.isNullOrEmpty()) loadMedia(tracks?.toList())
//    }
  }

  override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
    val keyEvent: KeyEvent = mediaButtonEvent.keyEvent ?: return false
    val keyCode: Int = keyEvent.keyCode
    return if (!playerService.mediaIsLoaded && keyCode.isPlayOrToggle()) {
      keyEvent.isDownAction.also { if (it) playerService.setCurrentQueue(QueueType.Audio) }
    } else if (isCarHardKey(keyEvent) && keyCode.isPreviousOrNextMedia()) {
      when (keyEvent.action) {
        KeyEvent.ACTION_DOWN -> handleDownAction(keyEvent.isLongPress, keyCode)
        KeyEvent.ACTION_UP -> handleUpAction(keyCode)
      }
      true
    } else {
      super.onMediaButtonEvent(mediaButtonEvent)
    }
  }

  private fun handleUpAction(keyCode: Int) {
    if (!seeking) {
      when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
          if (playerService.enabledActions.hasSkipToNext) onSkipToNext()
        }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
          if (playerService.enabledActions.hasSkipToPrevious) onSkipToPrevious()
        }
      }
    }
    seeking = false
  }

  private fun handleDownAction(isLongPress: Boolean, keyCode: Int) {
    if (playerService.isSeekable && isLongPress) {
      when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> onFastForward()
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onRewind()
      }
      seeking = true
    }
  }

  private fun isCarHardKey(event: KeyEvent): Boolean =
    uiModeManager.inCarMode && event.deviceId == 0 && (event.flags and FLAG_KEEP_TOUCH_MODE != 0)
}

private val Intent.keyEvent: KeyEvent?
  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)

private val KeyEvent.isDownAction: Boolean
  get() = action == KeyEvent.ACTION_DOWN

private fun Int.isPlayOrToggle() =
  (this == KeyEvent.KEYCODE_MEDIA_PLAY || this == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

private fun Int.isPreviousOrNextMedia() =
  (this == KeyEvent.KEYCODE_MEDIA_PREVIOUS || this == KeyEvent.KEYCODE_MEDIA_NEXT)

/*
        const val ID_ROOT = "ID_ROOT"
        private const val ID_ARTISTS = "ID_ARTISTS"
        private const val ID_ALBUMS = "ID_ALBUMS"
        private const val ID_TRACKS = "ID_TRACKS"
        private const val ID_GENRES = "ID_GENRES"
        private const val ID_PLAYLISTS = "ID_PLAYLISTS"
        private const val ID_HOME = "ID_HOME"
        const val ID_HISTORY = "ID_HISTORY"
        const val ID_LAST_ADDED = "ID_RECENT"
        private const val ID_STREAMS = "ID_STREAMS"
        private const val ID_LIBRARY = "ID_LIBRARY"
        const val ID_SHUFFLE_ALL = "ID_SHUFFLE_ALL"
        const val ID_NO_MEDIA = "ID_NO_MEDIA"
        const val ID_NO_PLAYLIST = "ID_NO_PLAYLIST"
        const val ALBUM_PREFIX = "album"
        const val ARTIST_PREFIX = "artist"
        const val GENRE_PREFIX = "genre"
        const val PLAYLIST_PREFIX = "playlist"
        const val MAX_HISTORY_SIZE = 100
        private const val MAX_COVER_ART_ITEMS = 50
        private const val MAX_EXTENSION_SIZE = 100
        private const val MAX_RESULT_SIZE = 800
 */
