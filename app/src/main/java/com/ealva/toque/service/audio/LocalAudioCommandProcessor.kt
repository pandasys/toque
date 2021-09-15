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

package com.ealva.toque.service.audio

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioDaoEvent
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.SongListType
import com.ealva.toque.log._e
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.scanner.MediaScannerService
import com.ealva.toque.service.controller.MediaSessionEvent
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.player.PlayerTransitionPair
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.MediaSessionBrowser
import com.ealva.toque.service.session.MediaSessionControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val LOG by lazyLogger(LocalAudioCommandProcessor::class)
private const val ADD_NEW_MEDIA_COUNT = 10

class LocalAudioCommandProcessor(
  private val realQueue: LocalAudioQueue,
  private val appPrefs: AppPrefs,
  private val sessionControl: MediaSessionControl
) : LocalAudioQueue by realQueue, KoinComponent {
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private val commandFlow = MutableSharedFlow<LocalAudioCommand>(replay = 4)
  private val audioMediaDao: AudioMediaDao by inject()
  private val uiModeManager: UiModeManager by inject()

  override suspend fun activate(resume: Boolean, playNow: PlayNow) {
    realQueue.activate(resume, playNow)
//    sessionControl.onMediaButton(::handleMediaButton)
    collectCommandFlow()
    scope.launch {
      realQueue.isActive
        .collect { isActive ->
          if (isActive) {
            collectMediaSessionEventFlow(sessionControl)
            if (appPrefs.firstRun()) collectAudioDaoEventsAndStartMediaScanner()
          }
        }
    }
  }

  override fun deactivate() {
    scope.cancel()
    realQueue.deactivate()
  }

  override suspend fun goToQueueItem(instanceId: Long) =
    commandFlow.emit(LocalAudioCommand.GoToQueueItem(instanceId))

  override suspend fun seekTo(position: Millis) =
    commandFlow.emit(LocalAudioCommand.SeekTo(position))

  private suspend fun prepareFromId(id: String, extras: Bundle) = try {
    MediaSessionBrowser.handleMedia(id, extras, PrepareMediaFromId(this, audioMediaDao))
  } catch (e: Exception) {
    LOG.e(e) { it("Error playFromId %s", id) }
  }

  private suspend fun playFromId(id: String, extras: Bundle) = try {
    MediaSessionBrowser.handleMedia(id, extras, PlayMediaFromId(this, audioMediaDao))
  } catch (e: Exception) {
    LOG.e(e) { it("Error playFromId %s", id) }
  }

  override suspend fun play(immediate: Boolean) =
    commandFlow.emit(LocalAudioCommand.Play(immediate))

  override suspend fun pause(immediate: Boolean) =
    commandFlow.emit(LocalAudioCommand.Pause(immediate))

  override suspend fun stop() {
    commandFlow.emit(LocalAudioCommand.Stop)
  }

  override suspend fun next() = commandFlow.emit(LocalAudioCommand.Next)

  override suspend fun previous() = commandFlow.emit(LocalAudioCommand.Previous)

  override suspend fun fastForward() = commandFlow.emit(LocalAudioCommand.FastForward)

  override suspend fun rewind() = commandFlow.emit(LocalAudioCommand.Rewind)

  override suspend fun setRepeatMode(mode: RepeatMode) =
    commandFlow.emit(LocalAudioCommand.SetRepeatMode(mode))

  override suspend fun setShuffleMode(mode: ShuffleMode) =
    commandFlow.emit(LocalAudioCommand.SetShuffleMode(mode))

  override suspend fun setRating(rating: StarRating) =
    commandFlow.emit(LocalAudioCommand.SetRating(rating))

  override suspend fun addToUpNext(audioIdList: AudioIdList) =
    commandFlow.emit(LocalAudioCommand.AddToUpNext(audioIdList))

  override suspend fun playNext(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transition: PlayerTransitionPair
  ) = commandFlow.emit(LocalAudioCommand.PlayNext(audioIdList, clearUpNext, playNow, transition))

  override suspend fun duck() {
    commandFlow.emit(LocalAudioCommand.Duck)
  }

  override suspend fun endDuck() {
    commandFlow.emit(LocalAudioCommand.EndDuck)
  }

  private fun collectMediaSessionEventFlow(mediaSession: MediaSessionControl) {
    scope.launch {
      mediaSession.eventFlow
        .onEach { mediaSessionEvent -> handleSessionEvent(mediaSessionEvent) }
        .catch { cause -> LOG.e(cause) { it("Error processing SessionEvent") } }
        .onCompletion { cause -> LOG.i(cause) { it("SessionEvent end") } }
        .collect()
    }
  }

  private suspend fun collectAudioDaoEventsAndStartMediaScanner() {
    scope.launch {
      var addToQueueCount = ADD_NEW_MEDIA_COUNT
      audioMediaDao
        .audioDaoEvents
        .onStart { startMediaScannerModifiedSinceLastScan() }
        .takeWhile { addToQueueCount > 0 }
        .onEach { event ->
          LOG._e { it("addToQueueCount=%d", addToQueueCount) }
          addToQueueCount = handleScannerEvent(addToQueueCount, event)
        }
        .onCompletion {
          appPrefs.edit { it[firstRun] = false }
          LOG._e { it("audioDaoEvents flow completed addToQueueCount=%d", addToQueueCount) }
        }
        .collect()
    }
  }

  private fun startMediaScannerModifiedSinceLastScan() = MediaScannerService.startScanner(
    Toque.appContext,
    "FirstRun",
    MediaScannerService.RescanType.ModifiedSinceLast
  )

  private suspend fun handleSessionEvent(event: MediaSessionEvent) {
    LOG._e { it("-->handleMediaSessionEvent %s", event) }
    when (event) {
      is MediaSessionEvent.AddItemAt -> addItemAt(event.item, event.pos, event.addToEnd)
      is MediaSessionEvent.CustomAction -> customAction(event.action, event.extras)
      is MediaSessionEvent.EnableCaption -> enableCaption(event.enable)
      is MediaSessionEvent.FastForward -> fastForward()
      is MediaSessionEvent.Pause -> pause()
      is MediaSessionEvent.Play -> play()
      is MediaSessionEvent.PlayFromId -> playFromId(event.mediaId, event.extras)
      is MediaSessionEvent.PlayFromSearch -> playFromSearch(event.query, event.extras)
      is MediaSessionEvent.PlayFromUri -> playFromUri(event.uri, event.extras)
      is MediaSessionEvent.Prepare -> prepare()
      is MediaSessionEvent.PrepareFromId -> prepareFromId(event.mediaId, event.extras)
      is MediaSessionEvent.PrepareFromSearch -> prepareFromSearch(event.query, event.extras)
      is MediaSessionEvent.PrepareFromUri -> prepareFromUri(event.uri, event.extras)
      is MediaSessionEvent.RemoveItem -> removeItem(event.item)
      is MediaSessionEvent.Repeat -> setRepeatMode(event.repeatMode)
      is MediaSessionEvent.Rewind -> rewind()
      is MediaSessionEvent.SeekTo -> seekTo(event.position)
      is MediaSessionEvent.SetRating -> setRating(event.rating)
      is MediaSessionEvent.Shuffle -> setShuffleMode(event.shuffleMode)
      is MediaSessionEvent.SkipToNext -> next()
      is MediaSessionEvent.SkipToPrevious -> previous()
      is MediaSessionEvent.SkipToQueueItem -> goToQueueItem(event.instanceId)
      is MediaSessionEvent.Stop -> stop()
      MediaSessionEvent.Duck -> duck()
      MediaSessionEvent.EndDuck -> endDuck()
    }
    LOG._e { it("<--handleMediaSessionEvent %s", event) }
  }

  private suspend fun addItemAt(item: MediaDescriptionCompat, pos: Int, addToEnd: Boolean) {
    LOG._e { it("addItemAt item=%s, pos=%d, addToEnd=%s", item, pos, addToEnd) }
  }

  private suspend fun customAction(action: String, extras: Bundle) {
    LOG._e { it("customAction action=%s, extras=%s", action, extras) }
  }

  private suspend fun enableCaption(enable: Boolean) {
    LOG._e { it("enableCaption enable=%s", enable) }
  }

  private suspend fun playFromUri(uri: Uri, extras: Bundle) {
    LOG._e { it("playFromUri uri=%s extras=%s", uri, extras) }
  }

  private fun prepare() {
    LOG._e { it("prepare") }
  }

  private suspend fun prepareFromSearch(query: String, extras: Bundle) {
    LOG._e { it("prepareFromSearch query=%s extras=%s", query, extras) }
  }

  private suspend fun prepareFromUri(uri: Uri, extras: Bundle) {
    LOG._e { it("prepareFromUri uri=%s extras=%s", uri, extras) }
  }

  private suspend fun playFromSearch(query: String, extras: Bundle) {
    LOG._e { it("playFromSearch query=%s extras=%s", query, extras) }
  }

  private suspend fun removeItem(item: MediaDescriptionCompat) {
    LOG._e { it("removeItem item=%s", item) }
  }

  private suspend fun handleScannerEvent(
    addToQueueCount: Int,
    event: AudioDaoEvent
  ): Int = if (addToQueueCount > 0 && event is AudioDaoEvent.MediaCreated) {
    addToUpNext(AudioIdList(event.mediaIds, SongListType.All, ""))
    addToQueueCount - event.mediaIds.size
  } else 0

  private fun collectCommandFlow() {
    scope.launch {
      commandFlow
        .onEach { command -> processCommand(command) }
        .catch { cause -> LOG.e(cause) { it("Error processing command") } }
        .onCompletion { cause -> LOG.i(cause) { it("Command processing end") } }
        .collect()
    }
  }

  private suspend fun processCommand(command: LocalAudioCommand) = command.process(realQueue)

  private fun handleMediaButton(
    keyEvent: KeyEvent,
    keyCode: Int,
    intent: Intent,
    callback: MediaSessionCompat.Callback
  ): Boolean = if (isCarHardKey(keyEvent) && keyCode.isPreviousOrNextMedia()) {
    when (keyEvent.action) {
      KeyEvent.ACTION_DOWN -> handleDownAction(keyEvent.isLongPress, keyCode)
      KeyEvent.ACTION_UP -> handleUpAction(keyCode)
      else -> false
    }
  } else false

  private fun handleUpAction(keyCode: Int): Boolean {
    var handled = false
    if (!seeking) {
      when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
          if (enabledActions.hasSkipToNext) {
            scope.launch { next() }
            handled = true
          }
        }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
          if (enabledActions.hasSkipToPrevious) {
            scope.launch { previous() }
            handled = true
          }
        }
      }
    }
    return handled
  }

  private fun handleDownAction(isLongPress: Boolean, keyCode: Int): Boolean {
    var handled = false
    if (isSeekable && isLongPress) {
      when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
          scope.launch { fastForward() }
          handled = true
        }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
          scope.launch { rewind() }
          handled = true
        }
      }
    }
    return handled
  }

  private fun isCarHardKey(event: KeyEvent): Boolean = uiModeManager.inCarMode &&
    event.deviceId == 0 && (event.flags and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0)
}

private sealed interface LocalAudioCommand {
  suspend fun process(localAudioQueue: LocalAudioQueue)

  data class Play(val immediate: Boolean = false) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.play(immediate)
  }

  data class Pause(val immediate: Boolean = false) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.pause(immediate)
  }

  data class SeekTo(val position: Millis) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.seekTo(position)
  }

  data class GoToQueueItem(val instanceId: Long) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.goToQueueItem(instanceId)
  }

  object Stop : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) {
      localAudioQueue.stop()
    }

    override fun toString(): String = "Stop"
  }

  object Next : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = localAudioQueue.next()
    override fun toString(): String = "Next"
  }

  object Previous : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = localAudioQueue.previous()
    override fun toString(): String = "Previous"
  }

  object FastForward : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = localAudioQueue.fastForward()
    override fun toString(): String = "FastForward"
  }

  object Rewind : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = localAudioQueue.rewind()
    override fun toString(): String = "Rewind"
  }

  data class AddToUpNext(val audioIdList: AudioIdList) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.addToUpNext(audioIdList)
  }

  data class PlayNext(
    val audioIdList: AudioIdList,
    val clearUpNext: ClearQueue,
    val playNow: PlayNow,
    val transition: PlayerTransitionPair
  ) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.playNext(audioIdList, clearUpNext, playNow, transition)
  }

  data class SetRepeatMode(private val mode: RepeatMode) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.setRepeatMode(mode)
  }

  data class SetShuffleMode(private val mode: ShuffleMode) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.setShuffleMode(mode)
  }

  data class SetRating(private val rating: StarRating) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.setRating(rating)
  }

  object Duck : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.duck()
  }

  object EndDuck : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) =
      localAudioQueue.endDuck()
  }
}

private val Intent.keyEvent: KeyEvent?
  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)

private val KeyEvent.isDownAction: Boolean
  get() = action == KeyEvent.ACTION_DOWN

private fun Int.isPlayOrToggle() =
  (this == KeyEvent.KEYCODE_MEDIA_PLAY || this == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

private fun Int.isPreviousOrNextMedia() =
  (this == KeyEvent.KEYCODE_MEDIA_PREVIOUS || this == KeyEvent.KEYCODE_MEDIA_NEXT)

val UiModeManager.inCarMode: Boolean
  get() = currentModeType == Configuration.UI_MODE_TYPE_CAR
