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

package com.ealva.toque.service.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.glance.appwidget.updateAll
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.service.image.loadAsBitmap
import com.ealva.toque.service.session.common.Metadata
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.server.MediaSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("unused")
private val LOG by lazyLogger(WidgetUpdater::class)

interface WidgetUpdater {
  /**
   * The last widget state. Is [WidgetState.NullWidgetState] until the media session is established.
   */
  val widgetState: WidgetState

  /**
   * The MediaPlayerService can set this during initialization
   */
  fun setMediaSession(session: MediaSession, toPending: ActionToPendingIntent)

  companion object {
    operator fun invoke(
      ctx: Context,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): WidgetUpdater = WidgetUpdaterImpl(ctx, dispatcher)
  }
}

class WidgetUpdaterImpl(private val ctx: Context, dispatcher: CoroutineDispatcher) : WidgetUpdater {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var mediaSession: MediaSession? = null
  private var toPendingIntent: ActionToPendingIntent? = null
  private val widgetStateFlow = MutableStateFlow(WidgetState.NullWidgetState)
  override val widgetState: WidgetState get() = widgetStateFlow.value

  override fun setMediaSession(session: MediaSession, toPending: ActionToPendingIntent) {
    mediaSession = session
    toPendingIntent = toPending
    session.listenToMediaSession()
    listenToWidgetStateFlow()
  }

  private fun MediaSession.listenToMediaSession() {
    listenToPlaybackState()
    listenToMetadata()
    listenToRepeatMode()
    listenToShuffleMode()
  }

  private fun MediaSession.listenToShuffleMode() = shuffleModeFlow
    .drop(1)
    .onEach { shuffleMode -> handleShuffleMode(shuffleMode) }
    .catch { cause -> LOG.e(cause) { it("Error collecting shuffleModeFlow") } }
    .launchIn(scope)

  private fun MediaSession.listenToRepeatMode() = repeatModeFlow
    .drop(1)
    .onEach { repeatMode -> handleRepeatMode(repeatMode) }
    .catch { cause -> LOG.e(cause) { it("Error collecting repeatModeFlow") } }
    .launchIn(scope)

  private fun MediaSession.listenToMetadata() = metadataFlow
    .drop(1)
    .onEach { metadata -> handleMetadata(metadata) }
    .catch { cause -> LOG.e(cause) { it("Error collecting metadataFlow") } }
    .launchIn(scope)

  private fun MediaSession.listenToPlaybackState() = playbackStateFlow
    .drop(1)
    .onEach { state -> handlePlaybackState(state) }
    .catch { cause -> LOG.e(cause) { it("Error collecting playbackStateFlow") } }
    .launchIn(scope)


  private fun handlePlaybackState(state: PlaybackState) =
    widgetStateFlow.update { it.copy(isPlaying = state.isPlaying) }

  private suspend fun handleMetadata(metadata: Metadata) = widgetStateFlow.update {
    it.copy(
      iconBitmap = getIconBitmap(metadata),
      title = metadata.title,
      album = metadata.albumTitle,
      artist = metadata.albumArtist
    )
  }

  /**
   * Typically the need to fall back to doing a loadBitmap here will only happen when the device
   * boots. While the bitmap will eventually come from the [mediaSession], doing this will present
   * the artwork faster to the user.
   */
  private suspend fun getIconBitmap(metadata: Metadata): Bitmap? = mediaSession?.getIconBitmap()
    ?: metadata.artwork.takeIf { artworkUri -> artworkUri !== Uri.EMPTY }?.loadAsBitmap()

  private fun handleRepeatMode(repeatMode: RepeatMode) = widgetStateFlow.update {
    it.copy(repeatMode = repeatMode)
  }

  private fun handleShuffleMode(shuffleMode: ShuffleMode) = widgetStateFlow.update {
    it.copy(shuffleMode = shuffleMode)
  }

  private fun listenToWidgetStateFlow() = widgetStateFlow
    .onEach { widgetInfo -> handleWidgetInfo(widgetInfo) }
    .catch { cause -> LOG.e(cause) { it("Error collecting widgetState flow") } }
    .launchIn(scope)

  private fun handleWidgetInfo(state: WidgetState) =
    scope.launch { MediumWidget(state).updateAll(ctx) }
}
