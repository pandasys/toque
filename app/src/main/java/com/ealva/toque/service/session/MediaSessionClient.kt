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

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.compatToRepeatMode
import com.ealva.toque.common.compatToShuffleMode
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

private val LOG by lazyLogger(MediaSessionClient::class)

sealed class LoadEvent {
  data class ChildrenLoaded(
    val parentId: String,
    val children: List<MediaBrowserCompat.MediaItem>,
    val options: Bundle
  )

  data class Error(val parentId: String, val options: Bundle)
}

private typealias ChildList = MutableList<MediaBrowserCompat.MediaItem>

interface MediaSessionClient {
  val isConnected: StateFlow<Boolean>
  val networkError: StateFlow<Boolean>
  val playbackState: StateFlow<PlaybackStateCompat>
  val nowPlaying: StateFlow<MediaMetadataCompat>
  val queue: StateFlow<List<MediaSessionCompat.QueueItem>>
  val queueTitle: StateFlow<String>
  val extras: StateFlow<Bundle>
  val captioningEnabled: StateFlow<Boolean>
  val repeatMode: StateFlow<RepeatMode>
  val shuffleMode: StateFlow<ShuffleMode>
  val rootMediaId: String

  val sessionControl: StateFlow<SessionControl>

  fun loadChildren(parentId: String, options: Bundle = Bundle.EMPTY): Flow<LoadEvent>

  companion object {
    operator fun invoke(
      context: Context,
      serviceComponent: ComponentName,
      lifecycleOwner: LifecycleOwner
    ): MediaSessionClient = MediaSessionClientImpl(context, serviceComponent).apply {
      lifecycleOwner.lifecycle.addObserver(this)
    }
  }
}

private class MediaSessionClientImpl(
  ctx: Context,
  name: ComponentName
) : MediaSessionClient, LifecycleObserver {
  override val isConnected = MutableStateFlow(false)
  override val networkError = MutableStateFlow(false)
  override val playbackState = MutableStateFlow(EMPTY_PLAYBACK_STATE)
  override val nowPlaying = MutableStateFlow(NOTHING_PLAYING)
  override val queue = MutableStateFlow<List<MediaSessionCompat.QueueItem>>(emptyList())
  override val queueTitle = MutableStateFlow("")
  override val extras = MutableStateFlow(Bundle.EMPTY)
  override val captioningEnabled = MutableStateFlow(false)
  override val repeatMode = MutableStateFlow(RepeatMode.None)
  override val shuffleMode = MutableStateFlow(ShuffleMode.None)
  override val rootMediaId: String
    get() = mediaBrowser.root

  // Would like to map from isConnected but there's currently no good way to do so
  override var sessionControl = MutableStateFlow<SessionControl>(NullSessionControl)

  private lateinit var mediaController: MediaControllerCompat
  private val connectionCallback: MediaBrowserCompat.ConnectionCallback = ConnectionCallback(ctx)
  private var controllerCallback = ControllerCallback()
  private val mediaBrowser = MediaBrowserCompat(ctx, name, connectionCallback, null).apply {
    connect()
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  fun onDestroy() {
    LOG._e { it("onDestroy release browser") }
    mediaBrowser.disconnect()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun loadChildren(parentId: String, options: Bundle): Flow<LoadEvent> = callbackFlow {
    val callback = object : MediaBrowserCompat.SubscriptionCallback() {
      override fun onChildrenLoaded(parentId: String, children: ChildList) =
        sendBlocking(LoadEvent.ChildrenLoaded(parentId, children, Bundle.EMPTY))

      override fun onChildrenLoaded(parentId: String, children: ChildList, options: Bundle) =
        sendBlocking(LoadEvent.ChildrenLoaded(parentId, children, options))

      override fun onError(parentId: String) =
        sendBlocking(LoadEvent.Error(parentId, Bundle.EMPTY))

      override fun onError(parentId: String, options: Bundle) =
        sendBlocking(LoadEvent.Error(parentId, options))
    }

    if (options !== Bundle.EMPTY)
      mediaBrowser.subscribe(parentId, options, callback)
    else
      mediaBrowser.subscribe(parentId, callback)

    awaitClose { mediaBrowser.unsubscribe(parentId) }
  }

  inner class ConnectionCallback(
    private val context: Context
  ) : MediaBrowserCompat.ConnectionCallback() {
    override fun onConnected() {
      LOG._i { it("browser onConnected") }
      mediaBrowser.sessionToken.also { token ->
        mediaController = MediaControllerCompat(context, token)
        mediaController.registerCallback(controllerCallback)
      }
      sessionControl.value = SessionControl(mediaController.transportControls)
      isConnected.value = true
    }

    override fun onConnectionSuspended() {
      LOG.w { it("onConnectionSuspended") }
      sessionControl.value = NullSessionControl
      isConnected.value = false
    }

    override fun onConnectionFailed() {
      LOG.w { it("onConnectFailed") }
      sessionControl.value = NullSessionControl
      isConnected.value = false
    }
  }

  inner class ControllerCallback : MediaControllerCompat.Callback() {
//    override fun onSessionEvent(event: String?, extras: Bundle?) {
//      when (event) {
//        NETWORK_FAILURE -> networkError.value = true
//      }
//    }

    /**
     * Since other connection status events are sent to [MediaBrowserCompat.ConnectionCallback], we
     * catch the disconnect here and send it on to the other callback.
     */
    override fun onSessionDestroyed() {
      connectionCallback.onConnectionSuspended()
    }

    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
      playbackState.value = state ?: EMPTY_PLAYBACK_STATE
    }

    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
      nowPlaying.value = if (metadata?.id == null) NOTHING_PLAYING else metadata
    }

    override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>) {
      this@MediaSessionClientImpl.queue.value = queue
    }

    override fun onQueueTitleChanged(title: CharSequence) {
      queueTitle.value = title.toString()
    }

    override fun onExtrasChanged(extras: Bundle) {
      this@MediaSessionClientImpl.extras.value = extras
    }

    override fun onCaptioningEnabledChanged(enabled: Boolean) {
      captioningEnabled.value = enabled
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
      this@MediaSessionClientImpl.repeatMode.value = repeatMode.compatToRepeatMode()
    }

    override fun onShuffleModeChanged(shuffleMode: Int) {
      this@MediaSessionClientImpl.shuffleMode.value = shuffleMode.compatToShuffleMode()
    }
  }
}
