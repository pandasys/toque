/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.service

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.annotation.IdRes
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.drawable
import com.ealva.toque.service.MediaPlayerService.Action
import com.ealva.toque.service.session.common.Metadata
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.server.MediaSession
import com.ealva.toque.service.widget.PlayerAppWidgetProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus

@Suppress("unused")
private val LOG by lazyLogger(WidgetUpdater::class)

interface WidgetUpdater {
  fun updateWidgets(appWidgetIds: IntArray)

  companion object {
    operator fun invoke(
      ctx: Context,
      scope: CoroutineScope,
      mediaSession: MediaSession,
      toPendingIntent: ActionToPendingIntent,
      dispatcher: CoroutineDispatcher
    ): WidgetUpdater = WidgetUpdaterImpl(ctx, scope, mediaSession, toPendingIntent, dispatcher)
  }
}

class WidgetUpdaterImpl(
  private val ctx: Context,
  private val scope: CoroutineScope,
  private val mediaSession: MediaSession,
  private val toPendingIntent: ActionToPendingIntent,
  private val dispatcher: CoroutineDispatcher
) : WidgetUpdater {
  private val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(ctx)

  private val widgetIds: IntArray
    get() = widgetManager.getAppWidgetIds(ComponentName(ctx, PlayerAppWidgetProvider::class.java))
  private val widgetState = MutableStateFlow(WidgetInfo.NullWidgetInfo)

  init {
    listenToMediaSession()
    listenToWidgetFlow()
  }

  override fun updateWidgets(appWidgetIds: IntArray) {
    val metadata = mediaSession.metadataFlow.value
    widgetState.value = WidgetInfo(
      appWidgetIds = appWidgetIds,
      iconBitmap = mediaSession.getIconBitmap(),
      title = metadata.title,
      album = metadata.albumTitle,
      artist = metadata.albumArtist,
      shuffleMode = mediaSession.shuffleModeFlow.value,
      repeatMode = mediaSession.repeatModeFlow.value,
      isPlaying = mediaSession.playbackStateFlow.value.isPlaying
    )
  }

  private fun listenToMediaSession() {
    mediaSession.playbackStateFlow
      .drop(1)
      .onEach { state -> handlePlaybackState(state) }
      .catch { cause -> LOG.e(cause) { it("Error collecting playbackStateFlow") } }
      .launchIn(scope + dispatcher)
    mediaSession.metadataFlow
      .drop(1)
      .onEach { metadata -> handleMetadata(metadata) }
      .catch { cause -> LOG.e(cause) { it("Error collecting metadataFlow") } }
      .launchIn(scope + dispatcher)
    mediaSession.repeatModeFlow
      .drop(1)
      .onEach { repeatMode -> handleRepeatMode(repeatMode) }
      .catch { cause -> LOG.e(cause) { it("Error collecting repeatModeFlow") } }
      .launchIn(scope + dispatcher)
    mediaSession.shuffleModeFlow
      .drop(1)
      .onEach { shuffleMode -> handleShuffleMode(shuffleMode) }
      .catch { cause -> LOG.e(cause) { it("Error collecting shuffleModeFlow") } }
      .launchIn(scope + dispatcher)
  }


  private fun handlePlaybackState(state: PlaybackState) =
    widgetState.update { it.copy(appWidgetIds = widgetIds, isPlaying = state.isPlaying) }

  private fun handleMetadata(metadata: Metadata) = widgetState.update {
    it.copy(
      appWidgetIds = widgetIds,
      iconBitmap = mediaSession.getIconBitmap(),
      title = metadata.title,
      album = metadata.albumTitle,
      artist = metadata.albumArtist
    )
  }

  private fun handleRepeatMode(repeatMode: RepeatMode) =
    widgetState.update { it.copy(appWidgetIds = widgetIds, repeatMode = repeatMode) }

  private fun handleShuffleMode(shuffleMode: ShuffleMode) =
    widgetState.update { it.copy(appWidgetIds = widgetIds, shuffleMode = shuffleMode) }

  private fun listenToWidgetFlow() {
    widgetState
      .onEach { widgetInfo -> handleWidgetInfo(widgetInfo) }
      .catch { cause -> LOG.e(cause) { it("Error collecting widgetState flow") } }
      .launchIn(scope + Dispatchers.IO) // let's handle widget update off main thread
  }

  private fun RemoteViews.setActionIntent(
    @IdRes buttonId: Int,
    action: Action
  ) {
    setOnClickPendingIntent(buttonId, toPendingIntent.makeIntent(ctx, action))
  }

  private fun handleWidgetInfo(info: WidgetInfo) {
    info.appWidgetIds.forEach { appWidgetId ->
      val views = RemoteViews(ctx.packageName, R.layout.medium_widget)
      views.setOnClickPendingIntent(R.id.rootLayout, mediaSession.sessionActivity)
      views.setActionIntent(R.id.prevButton, Action.Previous)
      views.setActionIntent(R.id.playPauseButton, Action.TogglePlayPause)
      views.setActionIntent(R.id.nextButton, Action.Next)
      views.setActionIntent(R.id.repeatButton, Action.NextRepeat)
      views.setActionIntent(R.id.shuffleButton, Action.NextShuffle)

      views.setImageViewResource(
        R.id.playPauseButton,
        if (info.isPlaying)
          R.drawable.ic_pause
        else
          R.drawable.ic_play
      )

      views.setImageViewResource(R.id.repeatButton, info.repeatMode.drawable)
      views.setImageViewResource(R.id.shuffleButton, info.shuffleMode.drawable)

      views.setTextViewText(R.id.songTitle, info.title.value)
      views.setTextViewText(R.id.artistName, info.artist.value)
      views.setTextViewText(R.id.albumName, info.album.value)

      when {
        info.iconBitmap != null -> views.setImageViewBitmap(R.id.albumImage, info.iconBitmap)
        else -> views.setImageViewResource(R.id.albumImage, R.drawable.ic_big_album)
      }
      widgetManager.updateAppWidget(appWidgetId, views)
    }
  }
}

private data class WidgetInfo(
  val appWidgetIds: IntArray,
  val iconBitmap: Bitmap?,
  val title: Title,
  val album: AlbumTitle,
  val artist: ArtistName,
  val shuffleMode: ShuffleMode,
  val repeatMode: RepeatMode,
  val isPlaying: Boolean
) {
  companion object {
    val NullWidgetInfo = WidgetInfo(
      appWidgetIds = IntArray(0),
      iconBitmap = null,
      title = Title(""),
      album = AlbumTitle(""),
      artist = ArtistName(""),
      shuffleMode = ShuffleMode.None,
      repeatMode = RepeatMode.None,
      isPlaying = false
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WidgetInfo) return false

    if (!appWidgetIds.contentEquals(other.appWidgetIds)) return false
    if (iconBitmap != other.iconBitmap) return false
    if (title != other.title) return false
    if (album != other.album) return false
    if (artist != other.artist) return false
    if (shuffleMode != other.shuffleMode) return false
    if (repeatMode != other.repeatMode) return false
    if (isPlaying != other.isPlaying) return false

    return true
  }

  override fun hashCode(): Int {
    var result = appWidgetIds.contentHashCode()
    result = 31 * result + (iconBitmap?.hashCode() ?: 0)
    result = 31 * result + title.hashCode()
    result = 31 * result + album.hashCode()
    result = 31 * result + artist.hashCode()
    result = 31 * result + shuffleMode.hashCode()
    result = 31 * result + repeatMode.hashCode()
    result = 31 * result + isPlaying.hashCode()
    return result
  }
}
