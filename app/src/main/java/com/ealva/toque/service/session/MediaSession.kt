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

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.media.session.MediaButtonReceiver
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.asCompat
import com.ealva.toque.common.compatToRepeatMode
import com.ealva.toque.common.compatToShuffleMode
import com.ealva.toque.common.toMillis
import com.ealva.toque.log._i
import com.ealva.toque.service.controller.MediaSessionEvent
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.service.controller.MediaSessionEvent.AddItemAt
import com.ealva.toque.service.controller.MediaSessionEvent.EnableCaption
import com.ealva.toque.service.controller.MediaSessionEvent.FastForward
import com.ealva.toque.service.controller.MediaSessionEvent.Pause
import com.ealva.toque.service.controller.MediaSessionEvent.Play
import com.ealva.toque.service.controller.MediaSessionEvent.PlayFromId
import com.ealva.toque.service.controller.MediaSessionEvent.PlayFromSearch
import com.ealva.toque.service.controller.MediaSessionEvent.PlayFromUri
import com.ealva.toque.service.controller.MediaSessionEvent.Prepare
import com.ealva.toque.service.controller.MediaSessionEvent.PrepareFromId
import com.ealva.toque.service.controller.MediaSessionEvent.PrepareFromSearch
import com.ealva.toque.service.controller.MediaSessionEvent.PrepareFromUri
import com.ealva.toque.service.controller.MediaSessionEvent.RemoveItem
import com.ealva.toque.service.controller.MediaSessionEvent.Repeat
import com.ealva.toque.service.controller.MediaSessionEvent.Rewind
import com.ealva.toque.service.controller.MediaSessionEvent.SetRating
import com.ealva.toque.service.controller.MediaSessionEvent.Shuffle
import com.ealva.toque.service.controller.MediaSessionEvent.SkipToNext
import com.ealva.toque.service.controller.MediaSessionEvent.SkipToPrevious
import com.ealva.toque.service.controller.MediaSessionEvent.SkipToQueueItem
import com.ealva.toque.service.controller.MediaSessionEvent.Stop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

typealias MediaButtonHandler = (KeyEvent, Int, Intent, MediaSessionCompat.Callback) -> Boolean

private val LOG by lazyLogger(MediaSession::class)

/**
 * Wraps a [MediaSessionCompat] and provides an interface with the types we want to see and handles
 * some housekeeping such as lifecycle management, handling [MediaSessionCompat.Callback] and
 * producing a flow
 */
interface MediaSession {
  /**
   * Calls from transport controls, media buttons, and commands from controllers and the system,
   * flow through here as [MediaSessionEvent]s
   */
  val eventFlow: Flow<MediaSessionEvent>

  /**
   * Token object that can be used by apps to create a MediaControllerCompat for interacting with
   * this session. The owner of the session is responsible for deciding how to distribute these
   * tokens. Used internally for Notification
   */
  val token: MediaSessionCompat.Token

  /**
   * If true this session is active and ready to receive commands.  If set to false your session's
   * controller may not be discoverable.
   */
  var isActive: Boolean

  /**
   * This must be called when an app has finished performing playback. If playback is expected to
   * start again shortly the session can be left open, but it must be released if your activity or
   * service is being destroyed.
   */
  fun release()

  /**
   * Set a handler to receive the [KeyEvent], keycode, Intent, and Callback for processing other
   * than the default provided. It's expected the [MediaButtonHandler] will use the
   * [MediaSessionCompat.Callback] for further processing. If [MediaButtonHandler] hasn't been set
   * or returns false, default processing will handle the button.
   */
  fun onMediaButton(handler: MediaButtonHandler)

  /** Set an intent for launching UI for this session */
  fun setSessionActivity(pi: PendingIntent)

  /** Update the current playback state  */
  fun setState(state: PlaybackStateCompat)

  /** Updates the current metadata. New metadata can be created using MediaMetadataCompat.Builder */
  fun setMetadata(metadata: MediaMetadata)

  /** Send queue info to controllers for display */
  fun setQueue(queue: List<MediaSessionCompat.QueueItem>)

  /** Sets the title of the play queue, eg. "Now Playing" */
  fun setQueueTitle(title: String)

  /** 😉 */
  fun setShuffle(shuffleMode: ShuffleMode)

  /** 😉 */
  fun setRepeat(repeatMode: RepeatMode)

  val browser: MediaSessionBrowser

  companion object {
    operator fun invoke(
      context: Context,
      lifecycleOwner: LifecycleOwner,
      active: Boolean = true,
      dispatcher: CoroutineDispatcher? = null
    ): MediaSession = MediaSessionImpl(makeMediaSessionCompat(context, active), dispatcher).apply {
      lifecycleOwner.lifecycle.addObserver(this)
    }

    private fun makeMediaSessionCompat(context: Context, active: Boolean) = MediaSessionCompat(
      context,
      context.mediaSessionTag,
      ComponentName(context, MediaButtonReceiver::class.java),
      null
    ).apply {
      isActive = active
      setRatingType(RatingCompat.RATING_5_STARS)
      setFlags(
        FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS or FLAG_HANDLES_QUEUE_COMMANDS
      )
    }
  }
}

private val Context.mediaSessionTag: String
  get() = "${getString(R.string.app_name)}MediaSession"

private class MediaSessionImpl(
  private val session: MediaSessionCompat,
  dispatcher: CoroutineDispatcher?
) : MediaSession, LifecycleObserver, RecentMediaProvider {
  private val scope = CoroutineScope(SupervisorJob() + (dispatcher ?: Dispatchers.Main))
  private var mediaButtonHandler: MediaButtonHandler? = null
  override val eventFlow by lazy { establishCallbackFlow() }

  private fun emit(event: MediaSessionEvent) {
    scope.launch { eventFlow.emit(event) }
  }

  // Wait to set the session callback until the flow is requested
  private fun establishCallbackFlow(): MutableSharedFlow<MediaSessionEvent> =
    MutableSharedFlow<MediaSessionEvent>().also {
      LOG._i { it("establishCallbackFlow") }
      session.setCallback(makeCallback())
    }

  override val token: MediaSessionCompat.Token = session.sessionToken

  override var isActive: Boolean
    get() = session.isActive
    set(value) {
      session.isActive = value
    }

  override fun release() {
    session.release()
  }

  override fun onMediaButton(handler: MediaButtonHandler) {
    mediaButtonHandler = handler
  }

  override fun setSessionActivity(pi: PendingIntent) = session.setSessionActivity(pi)
  private var lastPlaybackState: PlaybackStateCompat = EMPTY_PLAYBACK_STATE
  override fun setState(state: PlaybackStateCompat) {
    lastPlaybackState = state
    session.setPlaybackState(state)
  }

  private var lastMetadata: MediaMetadata = NOTHING_PLAYING
  override fun setMetadata(metadata: MediaMetadata) {
    lastMetadata = metadata
    session.setMetadata(lastMetadata.metadataCompat)
  }

  override fun setQueue(queue: List<MediaSessionCompat.QueueItem>) = session.setQueue(queue)
  override fun setQueueTitle(title: String) = session.setQueueTitle(title)
  override fun setShuffle(shuffleMode: ShuffleMode) = session.setShuffleMode(shuffleMode.asCompat)
  override fun setRepeat(repeatMode: RepeatMode) = session.setRepeatMode(repeatMode.asCompat)

  override fun getRecentMedia(): MediaDescriptionCompat? =
    if (lastMetadata === NOTHING_PLAYING) null else lastMetadata.metadataCompat.description

  private val _browser: MediaSessionBrowser by lazy { MediaSessionBrowser(this, scope, dispatcher) }
  override val browser: MediaSessionBrowser
    get() = _browser

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  fun onDestroy() {
    session.setCallback(null)
    scope.cancel()
    session.release()
  }

  private fun makeCallback(): MediaSessionCompat.Callback {
    return object : MediaSessionCompat.Callback() {
      private var inMediaButtonEvent = false
      override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        check(!inMediaButtonEvent) { "Don't call onMediaButtonEvent from MediaButtonHandler" }
        inMediaButtonEvent = true
        try {
          val event: KeyEvent = mediaButtonEvent.keyEvent ?: return false
          val code: Int = event.keyCode
          val handled = mediaButtonHandler?.invoke(event, code, mediaButtonEvent, this) ?: false
          return handled || super.onMediaButtonEvent(mediaButtonEvent)
        } finally {
          inMediaButtonEvent = false
        }
      }

      override fun onPrepare() = emit(Prepare)
      override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) =
        mediaId?.let { emit(PrepareFromId(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPrepareFromMediaId null mediaId") }

      override fun onPrepareFromSearch(query: String?, extras: Bundle?) =
        query?.let { emit(PrepareFromSearch(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPrepareFromSearch null query") }

      override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) =
        uri?.let { emit(PrepareFromUri(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPrepareFromUri null uri") }

      override fun onPlay() = emit(Play)
      override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) =
        mediaId?.let { emit(PlayFromId(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPlayFromMediaId null mediaId") }

      override fun onPlayFromSearch(query: String?, extras: Bundle?) =
        query?.let { emit(PlayFromSearch(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPrepareFromSearch null query") }

      override fun onPlayFromUri(uri: Uri?, extras: Bundle?) =
        uri?.let { emit(PlayFromUri(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onPrepareFromUri null uri") }

      override fun onSkipToQueueItem(id: Long) = emit(SkipToQueueItem(id))
      override fun onPause() = emit(Pause)
      override fun onSkipToNext() = emit(SkipToNext)
      override fun onSkipToPrevious() = emit(SkipToPrevious)
      override fun onFastForward() = emit(FastForward)
      override fun onRewind() = emit(Rewind)
      override fun onStop() = emit(Stop)
      override fun onSeekTo(pos: Long) = emit(MediaSessionEvent.SeekTo(pos.toMillis()))
      override fun onSetRating(rating: RatingCompat?) =
        rating?.let { emit(SetRating(it.starRating.toStarRating(), Bundle.EMPTY)) }
          ?: LOG.e { it("onSetRating null rating") }

      override fun onSetRating(rating: RatingCompat?, extras: Bundle?) =
        rating?.let { emit(SetRating(it.starRating.toStarRating(), extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onSetRating with extras, null rating") }

      override fun onSetCaptioningEnabled(enabled: Boolean) = emit(EnableCaption(enabled))
      override fun onSetRepeatMode(repeatMode: Int) = emit(Repeat(repeatMode.compatToRepeatMode()))
      override fun onSetShuffleMode(shuffleMode: Int) =
        emit(Shuffle(shuffleMode.compatToShuffleMode()))

      override fun onCustomAction(action: String?, extras: Bundle?) =
        action?.let { emit(MediaSessionEvent.CustomAction(it, extras ?: Bundle.EMPTY)) }
          ?: LOG.e { it("onCustomAction null action") }

      override fun onAddQueueItem(description: MediaDescriptionCompat?) =
        description?.let { emit(AddItemAt(it)) }
          ?: LOG.e { it("onAddQueueItem null description") }

      override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) =
        description?.let { emit(AddItemAt(it, index)) }
          ?: LOG.e { it("onAddQueueItem at index null description") }

      override fun onRemoveQueueItem(description: MediaDescriptionCompat?) =
        description?.let { emit(RemoveItem(it)) }
          ?: LOG.e { it("onRemoveQueueItem null description") }
    }
  }

  companion object {
    const val MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS = "playback_start_position_ms"
  }
}

private val Intent.keyEvent: KeyEvent?
  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)
