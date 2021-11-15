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

package com.ealva.toque.service.session.server

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.compatToRepeatMode
import com.ealva.toque.common.compatToShuffleMode
import com.ealva.toque.log._e
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.service.controller.SessionControlEvent
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.service.session.server.AudioFocusManager.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val LOG by lazyLogger(SessionCallback::class)

internal class SessionCallback(
  private val scope: CoroutineScope,
  private val flow: MutableSharedFlow<SessionControlEvent>,
  var contentType: ContentType = ContentType.Audio,
  var mediaButtonHandler: MediaButtonHandler? = null
) : MediaSessionCompat.Callback() {
  private var inMediaButtonEvent = false

  fun emit(event: SessionControlEvent) {
    scope.launch { flow.emit(event) }
  }

  override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
    check(!inMediaButtonEvent) { "Don't call onMediaButtonEvent from MediaButtonHandler" }
    inMediaButtonEvent = true
    try {
      val event: KeyEvent = mediaButtonEvent.keyEvent ?: return false
      val code: Int = event.keyCode
      var handled = mediaButtonHandler?.invoke(event, code, mediaButtonEvent, this) ?: false
      if (!handled) {
        handled = super.onMediaButtonEvent(mediaButtonEvent)
      }
      return handled
    } finally {
      inMediaButtonEvent = false
    }
  }

  override fun onPrepare() {
    LOG._e { it("onPrepare") }
    emit(SessionControlEvent.Prepare)
  }

  override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
    mediaId?.let { emit(SessionControlEvent.PrepareFromId(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPrepareFromMediaId null mediaId") }
  }

  override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
    query?.let { emit(SessionControlEvent.PrepareFromSearch(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPrepareFromSearch null query") }
  }

  override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) {
    uri?.let { emit(SessionControlEvent.PrepareFromUri(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPrepareFromUri null uri") }
  }

  override fun onPlay() {
    LOG._e { it("onPlay") }
    emit(SessionControlEvent.Play)
  }

  override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
    mediaId?.let { emit(SessionControlEvent.PlayFromId(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPlayFromMediaId null mediaId") }
  }

  override fun onPlayFromSearch(query: String?, extras: Bundle?) {
    query?.let { emit(SessionControlEvent.PlayFromSearch(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPrepareFromSearch null query") }
  }

  override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
    uri?.let { emit(SessionControlEvent.PlayFromUri(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onPrepareFromUri null uri") }
  }

  override fun onSkipToQueueItem(id: Long) =
    emit(SessionControlEvent.SkipToQueueItem(InstanceId(id)))

  override fun onPause() = emit(SessionControlEvent.Pause)
  override fun onSkipToNext() = emit(SessionControlEvent.SkipToNext)
  override fun onSkipToPrevious() = emit(SessionControlEvent.SkipToPrevious)
  override fun onFastForward() = emit(SessionControlEvent.FastForward)
  override fun onRewind() = emit(SessionControlEvent.Rewind)
  override fun onStop() {
    emit(SessionControlEvent.Stop)
  }

  override fun onSeekTo(pos: Long) = emit(SessionControlEvent.SeekTo(Millis(pos)))
  override fun onSetRating(rating: RatingCompat?) =
    rating?.let { emit(SessionControlEvent.SetRating(it.starRating.toStarRating(), Bundle.EMPTY)) }
      ?: LOG.e { it("onSetRating null rating") }

  override fun onSetRating(rating: RatingCompat?, extras: Bundle?) =
    rating?.let {
      emit(
        SessionControlEvent.SetRating(
          it.starRating.toStarRating(),
          extras ?: Bundle.EMPTY
        )
      )
    }
      ?: LOG.e { it("onSetRating with extras, null rating") }

  override fun onSetCaptioningEnabled(enabled: Boolean) =
    emit(SessionControlEvent.EnableCaption(enabled))

  override fun onSetRepeatMode(repeatMode: Int) =
    emit(SessionControlEvent.Repeat(repeatMode.compatToRepeatMode()))

  override fun onSetShuffleMode(shuffleMode: Int) =
    emit(SessionControlEvent.Shuffle(shuffleMode.compatToShuffleMode()))

  override fun onCustomAction(action: String?, extras: Bundle?) =
    action?.let { emit(SessionControlEvent.CustomAction(it, extras ?: Bundle.EMPTY)) }
      ?: LOG.e { it("onCustomAction null action") }

  override fun onAddQueueItem(description: MediaDescriptionCompat?) =
    description?.let { emit(SessionControlEvent.AddItemAt(it)) }
      ?: LOG.e { it("onAddQueueItem null description") }

  override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) =
    description?.let { emit(SessionControlEvent.AddItemAt(it, index)) }
      ?: LOG.e { it("onAddQueueItem at index null description") }

  override fun onRemoveQueueItem(description: MediaDescriptionCompat?) =
    description?.let { emit(SessionControlEvent.RemoveItem(it)) }
      ?: LOG.e { it("onRemoveQueueItem null description") }
}

private val Intent.keyEvent: KeyEvent?
  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)
