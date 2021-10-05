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

package com.ealva.toque.service.session.client

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.audio.QueueAudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.compatToRepeatMode
import com.ealva.toque.common.compatToShuffleMode
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.log._e
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toRating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.service.session.common.Metadata
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.common.toPersistentId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(MediaSessionClient::class)

sealed class LoadEvent {
  data class ChildrenLoaded(
    val parentId: String,
    val children: List<MediaBrowserCompat.MediaItem>,
    val options: Bundle
  ) : LoadEvent()

  data class Error(val parentId: String, val options: Bundle) : LoadEvent()
}

private typealias ChildList = MutableList<MediaBrowserCompat.MediaItem>

class MediaSessionClientProvider(
  private val context: Context,
  private val componentName: ComponentName,
  private val lifecycleOwner: LifecycleOwner
) {
  @Volatile
  private var instance: MediaSessionClient? = null
  private val lock = ReentrantLock()
  fun getSessionClient(): MediaSessionClient {
    return instance ?: lock.withLock {
      instance ?: MediaSessionClientImpl(context, componentName).apply {
        lifecycleOwner.lifecycle.addObserver(this)
      }.also { instance = it }
    }
  }
}

interface MediaSessionClient {
  val isConnected: StateFlow<Boolean>
  val networkError: StateFlow<Boolean>
  val playbackState: StateFlow<PlaybackState>
  val nowPlaying: StateFlow<Metadata>
  val queue: StateFlow<List<AudioItem>>
  val queueTitle: StateFlow<String>
  val extras: StateFlow<Bundle>
  val captioningEnabled: StateFlow<Boolean>
  val repeatMode: StateFlow<RepeatMode>
  val shuffleMode: StateFlow<ShuffleMode>
  val rootMediaId: String

  val sessionControl: StateFlow<SessionControl>

  fun loadChildren(parentId: String, options: Bundle = Bundle.EMPTY): Flow<LoadEvent>
}

private class MediaSessionClientImpl(
  ctx: Context,
  name: ComponentName
) : MediaSessionClient, DefaultLifecycleObserver {
  override val isConnected = MutableStateFlow(false)
  override val networkError = MutableStateFlow(false)
  override val playbackState = MutableStateFlow(PlaybackState.NullPlaybackState)
  override val nowPlaying = MutableStateFlow(Metadata.NullMetadata)
  override val queue = MutableStateFlow<List<AudioItem>>(emptyList())
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
    LOG._e { it("connecting to %s", name) }
  }

  override fun onDestroy(owner: LifecycleOwner) {
    mediaBrowser.disconnect()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun loadChildren(parentId: String, options: Bundle): Flow<LoadEvent> = callbackFlow {
    val callback = object : MediaBrowserCompat.SubscriptionCallback() {
      override fun onChildrenLoaded(parentId: String, children: ChildList) =
        doSendBlocking(LoadEvent.ChildrenLoaded(parentId, children, Bundle.EMPTY))

      override fun onChildrenLoaded(parentId: String, children: ChildList, options: Bundle) =
        doSendBlocking(LoadEvent.ChildrenLoaded(parentId, children, options))

      override fun onError(parentId: String) =
        doSendBlocking(LoadEvent.Error(parentId, Bundle.EMPTY))

      override fun onError(parentId: String, options: Bundle) =
        doSendBlocking(LoadEvent.Error(parentId, options))

      private fun doSendBlocking(event: LoadEvent) {
        @Suppress("BlockingMethodInNonBlockingContext")
        trySendBlocking(event)
          .onFailure { exception: Throwable? ->
            exception?.let { ex -> LOG.w(ex) { it("LoadChildren flow failure") } }
              ?: LOG.w { it("LoadChildren flow failure") }
          }
      }
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
      LOG._e { it("browser onConnected") }
      mediaBrowser.sessionToken.also { token ->
        mediaController = MediaControllerCompat(context, token)
        mediaController.registerCallback(controllerCallback)
      }
      sessionControl.value = SessionControl(mediaController.transportControls)
      isConnected.value = true
    }

    override fun onConnectionSuspended() {
      LOG._e { it("onConnectionSuspended") }
      sessionControl.value = NullSessionControl
      isConnected.value = false
    }

    override fun onConnectionFailed() {
      LOG._e { it("onConnectFailed") }
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

    override fun onSessionEvent(event: String?, extras: Bundle?) {
      val eventName = event.orEmpty()
      val bundle = extras ?: Bundle.EMPTY
    }

    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {

      //playbackState.value =
    }

    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
      //nowPlaying.value =
    }

    override fun onQueueChanged(queue: List<MediaSessionCompat.QueueItem>?) {
      this@MediaSessionClientImpl.queue.value = queue.toAudioQueueItemList()
    }

    override fun onQueueTitleChanged(title: CharSequence?) {
      queueTitle.value = title?.toString() ?: ""
    }

    override fun onExtrasChanged(extras: Bundle?) {
      this@MediaSessionClientImpl.extras.value = extras ?: Bundle.EMPTY
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

private fun List<MediaSessionCompat.QueueItem>?.toAudioQueueItemList(): List<AudioItem> {
  if (this == null) return emptyList()
  return List(size) { index -> get(index).toAudioItem() }
}

private fun MediaSessionCompat.QueueItem.toAudioItem(): AudioItem {
  return description.run {
    val extras = extras ?: Bundle.EMPTY
    QueueAudioItemImpl(
      id = mediaId.toPersistentId() as MediaId,
      title = Title(title.toString()),
      albumTitle = AlbumTitle(description.toString()),
      albumArtist = ArtistName(subtitle.toString()),
      artist = ArtistName(subtitle.toString()),
      duration = Millis(extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)),
      trackNumber = extras.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER).toInt(),
      localAlbumArt = iconUri ?: Uri.EMPTY,
      albumArt = extras.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI).toUriOrEmpty(),
      rating = extras.getParcelable<RatingCompat>(MediaMetadataCompat.METADATA_KEY_RATING)
        ?.toStarRating()
        ?.toRating()
        ?: Rating.RATING_NONE,
      location = mediaUri ?: Uri.EMPTY,
      fileUri = Uri.EMPTY,
      instanceId = queueId
    )
  }
}

private data class QueueAudioItemImpl(
  override val id: MediaId,
  override val title: Title,
  override val albumTitle: AlbumTitle,
  override val albumArtist: ArtistName,
  override val artist: ArtistName,
  override val duration: Millis,
  override val trackNumber: Int,
  override val localAlbumArt: Uri,
  override val albumArt: Uri,
  override val rating: Rating,
  override val location: Uri,
  override val fileUri: Uri,
  override val instanceId: Long
) : QueueAudioItem

const val MAX_3_STAR = 3f
const val MAX_4_STAR = 4f
const val MAX_5_STAR = 5f
const val MAX_PERCENTAGE = 100
val STAR_3_RANGE = 0f..MAX_3_STAR
val STAR_4_RANGE = 0f..MAX_4_STAR
val STAR_5_RANGE = 0f..MAX_5_STAR
val PERCENT_RANGE = 0..MAX_PERCENTAGE
fun RatingCompat.toStarRating(): StarRating {
  if (!isRated) StarRating.STAR_NONE
  return when (ratingStyle) {
    RatingCompat.RATING_HEART -> if (hasHeart()) StarRating.STAR_5 else StarRating.STAR_NONE
    RatingCompat.RATING_THUMB_UP_DOWN -> if (isThumbUp) StarRating.STAR_5 else StarRating.STAR_NONE
    RatingCompat.RATING_3_STARS -> STAR_3_RANGE.convert(starRating.coerceIn(STAR_3_RANGE))
    RatingCompat.RATING_4_STARS -> STAR_4_RANGE.convert(starRating.coerceIn(STAR_4_RANGE))
    RatingCompat.RATING_5_STARS -> StarRating(starRating.coerceIn(STAR_5_RANGE))
    RatingCompat.RATING_PERCENTAGE ->
      Rating(percentRating.toInt().coerceIn(PERCENT_RANGE)).toStarRating()
    else -> StarRating.STAR_NONE
  }
}

private fun ClosedFloatingPointRange<Float>.convert(number: Float): StarRating {
  val ratio = number / (endInclusive - start)
  return StarRating(
    (ratio * (STAR_5_RANGE.endInclusive - STAR_5_RANGE.start)).coerceIn(STAR_5_RANGE)
  )
}
