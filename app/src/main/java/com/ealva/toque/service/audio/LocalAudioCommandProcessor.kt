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

package com.ealva.toque.service.audio

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.LoggingCoroutineExceptionHandler
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioDaoEvent
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.scanner.MediaScannerService
import com.ealva.toque.service.audio.LocalAudioCommand.DeferredResult
import com.ealva.toque.service.controller.SessionControlEvent
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.ForceTransition
import com.ealva.toque.service.queue.ForceTransition.AllowFade
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.server.MediaSessionBrowser
import com.ealva.toque.service.session.server.MediaSessionControl
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val LOG by lazyLogger(LocalAudioCommandProcessor::class)
private const val ADD_NEW_MEDIA_COUNT = 10
private val LOGGING_HANDLER = LoggingCoroutineExceptionHandler(LOG)

class LocalAudioCommandProcessor(
  private val realQueue: LocalAudioQueue,
  private val appPrefs: AppPrefs,
  private val sessionControl: MediaSessionControl
) : LocalAudioQueue by realQueue, KoinComponent {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + LOGGING_HANDLER)

  /** Replay a small number in case we get an initial burst at startup before completely active */
  private val commandFlow = MutableSharedFlow<LocalAudioCommand>(replay = 4)
  private val audioMediaDao: AudioMediaDao by inject()
  //private val uiModeManager: UiModeManager by inject()

  override suspend fun activate(resume: Boolean, playNow: PlayNow) {
//    sessionControl.onMediaButton(::handleMediaButton)
    realQueue.isActive
      .onStart { realQueue.activate(resume, playNow) }
      .onEach { isActive -> handleIsActive(isActive) }
      .catch { cause -> LOG.e(cause) { it("LocalAudioQueue isActive flow error") } }
      .onCompletion { LOG._i { it("LocalAudioQueue isActive flow complete") } }
      .launchIn(scope)
  }

  private suspend fun handleIsActive(isActive: Boolean) {
    if (isActive) {
      if (appPrefs.firstRun()) collectAudioDaoEventsAndStartMediaScanner()
      collectCommandFlow()
      collectMediaSessionEventFlow(sessionControl)
    }
  }

  override fun deactivate() {
    scope.cancel()
    realQueue.deactivate()
  }

  override fun goToQueueItem(instanceId: InstanceId) = asyncCommand { goToQueueItem(instanceId) }
  override fun seekTo(position: Millis) = asyncCommand { seekTo(position) }
  override fun play(forceTransition: ForceTransition) = asyncCommand { play(forceTransition) }
  override fun pause(forceTransition: ForceTransition) = asyncCommand { pause(forceTransition) }
  override fun stop() = asyncCommand { stop() }
  override fun togglePlayPause() = asyncCommand { togglePlayPause() }
  override fun next() = asyncCommand { next() }
  override fun previous() = asyncCommand { previous() }
  override fun nextList() = asyncCommand { nextList() }
  override fun previousList() = asyncCommand { previousList() }
  override fun fastForward() = asyncCommand { fastForward() }
  override fun rewind() = asyncCommand { rewind() }
  override fun goToIndexMaybePlay(index: Int) = asyncCommand { goToIndexMaybePlay(index) }
  override fun nextRepeatMode() = asyncCommand { nextRepeatMode() }
  override fun setRepeatMode(mode: RepeatMode) = asyncCommand { setRepeatMode(mode) }
  override fun nextShuffleMode() = asyncCommand { nextShuffleMode() }
  override fun setShuffleMode(mode: ShuffleMode) = asyncCommand { setShuffleMode(mode) }

  override suspend fun addToUpNext(
    categoryMediaList: CategoryMediaList
  ): Result<QueueSize, QueueMessage> = deferredResult { addToUpNext(categoryMediaList) }

  private suspend fun prepareFromId(id: String, extras: Bundle) =
    command { MediaSessionBrowser.handleMedia(id, extras, PrepareMediaFromId(this, audioMediaDao)) }

  private suspend fun playFromId(id: String, extras: Bundle) =
    command { MediaSessionBrowser.handleMedia(id, extras, PlayMediaFromId(this, audioMediaDao)) }

  override fun toggleEqMode() = asyncCommand { toggleEqMode() }

  override fun setRating(rating: StarRating, allowFileUpdate: Boolean) =
    asyncCommand { setRating(rating, allowFileUpdate) }

  override suspend fun playNext(
    categoryMediaList: CategoryMediaList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transitionType: TransitionType
  ): Result<QueueSize, QueueMessage> =
    deferredResult { playNext(categoryMediaList, clearUpNext, playNow, transitionType) }

  override suspend fun prepareNext(categoryMediaList: CategoryMediaList) =
    command { prepareNext(categoryMediaList) }

  override suspend fun removeFromQueue(index: Int, item: AudioItem) =
    command { removeFromQueue(index, item) }

  override fun moveQueueItem(from: Int, to: Int) = asyncCommand { moveQueueItem(from, to) }

  private suspend fun <T : Any> deferredResult(block: suspend LocalAudioQueue.() -> T): T {
    val deferred = CompletableDeferred<T>()
    commandFlow.emit(DeferredResult(deferred, block))
    return deferred.await()
  }

  private fun command(block: suspend LocalAudioQueue.() -> Unit) {
    scope.launch { commandFlow.emit(LocalAudioCommand.Command(block)) }
  }

  private fun asyncCommand(block: LocalAudioQueue.() -> Unit) {
    scope.launch { commandFlow.emit(LocalAudioCommand.AsyncCommand(block)) }
  }

  private fun collectMediaSessionEventFlow(mediaSession: MediaSessionControl) {
    mediaSession.eventFlow
      .onStart { LOG._i { it("Start collection SessionControlEvent") } }
      .onEach { mediaSessionEvent -> handleSessionEvent(mediaSessionEvent) }
      .catch { cause -> LOG.e(cause) { it("Error collecting SessionControlEvent") } }
      .onCompletion { cause -> LOG.i(cause) { it("Collect SessionControlEvent complete") } }
      .launchIn(scope)
  }

  private suspend fun collectAudioDaoEventsAndStartMediaScanner() {
    var addToQueueCount = ADD_NEW_MEDIA_COUNT
    audioMediaDao
      .audioDaoEvents
      .onStart { startMediaScannerModifiedSinceLastScan() }
      .takeWhile { addToQueueCount > 0 }
      .onEach { event -> addToQueueCount = handleScannerEvent(addToQueueCount, event) }
      .onCompletion { appPrefs.edit { it[firstRun] = false } }
      .launchIn(scope)
  }

  private fun startMediaScannerModifiedSinceLastScan() = MediaScannerService.startScanner(
    Toque.appContext,
    "FirstRun",
    MediaScannerService.RescanType.ModifiedSinceLast
  )

  private suspend fun handleSessionEvent(event: SessionControlEvent) {
    //LOG._e { it("handleSessionEvent %s", event) }
    when (event) {
      is SessionControlEvent.AddItemAt -> addItemAt(event.item, event.pos, event.addToEnd)
      is SessionControlEvent.CustomAction -> customAction(event.action, event.extras)
      is SessionControlEvent.EnableCaption -> enableCaption(event.enable)
      is SessionControlEvent.FastForward -> fastForward()
      is SessionControlEvent.Pause -> pause(AllowFade)
      is SessionControlEvent.Play -> play(AllowFade)
      is SessionControlEvent.PlayFromId -> playFromId(event.mediaId, event.extras)
      is SessionControlEvent.PlayFromSearch -> playFromSearch(event.query, event.extras)
      is SessionControlEvent.PlayFromUri -> playFromUri(event.uri, event.extras)
      is SessionControlEvent.Prepare -> prepare()
      is SessionControlEvent.PrepareFromId -> prepareFromId(event.mediaId, event.extras)
      is SessionControlEvent.PrepareFromSearch -> prepareFromSearch(event.query, event.extras)
      is SessionControlEvent.PrepareFromUri -> prepareFromUri(event.uri, event.extras)
      is SessionControlEvent.RemoveItem -> removeItem(event.item)
      is SessionControlEvent.Repeat -> setRepeatMode(event.repeatMode)
      is SessionControlEvent.Rewind -> rewind()
      is SessionControlEvent.SeekTo -> seekTo(event.position)
      is SessionControlEvent.SetRating -> setRating(event.rating, allowFileUpdate = false)
      is SessionControlEvent.Shuffle -> setShuffleMode(event.shuffleMode)
      is SessionControlEvent.SkipToNext -> next()
      is SessionControlEvent.SkipToPrevious -> previous()
      is SessionControlEvent.SkipToQueueItem -> goToQueueItem(event.instanceId)
      is SessionControlEvent.Stop -> stop()
    }
  }

  private fun addItemAt(item: MediaDescriptionCompat, pos: Int, addToEnd: Boolean) {
    LOG._e { it("addItemAt item=%s, pos=%d, addToEnd=%s", item, pos, addToEnd) }
  }

  private fun customAction(action: String, extras: Bundle) {
    LOG._e { it("customAction action=%s, extras=%s", action, extras) }
  }

  private fun enableCaption(enable: Boolean) {
    LOG._e { it("enableCaption enable=%s", enable) }
  }

  private fun playFromUri(uri: Uri, extras: Bundle) {
    LOG._e { it("playFromUri uri=%s extras=%s", uri, extras) }
  }

  private fun prepare() {
    LOG._e { it("prepare") }
  }

  private fun prepareFromSearch(query: String, extras: Bundle) {
    LOG._e { it("prepareFromSearch query=%s extras=%s", query, extras) }
  }

  private fun prepareFromUri(uri: Uri, extras: Bundle) {
    LOG._e { it("prepareFromUri uri=%s extras=%s", uri, extras) }
  }

  private fun playFromSearch(query: String, extras: Bundle) {
    LOG._e { it("playFromSearch query=%s extras=%s", query, extras) }
  }

  private fun removeItem(item: MediaDescriptionCompat) {
    LOG._e { it("removeItem item=%s", item) }
  }

  private fun handleScannerEvent(addToQueueCount: Int, event: AudioDaoEvent): Int =
    if (addToQueueCount > 0 && event is AudioDaoEvent.MediaCreated) {
      scope.launch { addToUpNext(CategoryMediaList.invoke(event.id, CategoryToken.All)) }
      addToQueueCount - 1
    } else 0

  private fun collectCommandFlow() {
    commandFlow
      .onStart { LOG._i { it("Start collecting LocalAudioCommand") } }
      .onEach { command -> processCommand(command) }
      .catch { cause -> LOG.e(cause) { it("Error collecting LocalAudioCommand") } }
      .onCompletion { LOG._i { it("Collecting LocalAudioCommand complete") } }
      .launchIn(scope)
  }

  private suspend fun processCommand(command: LocalAudioCommand) = command.process(realQueue)

//private fun handleMediaButton(
//  keyEvent: KeyEvent,
//  keyCode: Int,
//  intent: Intent,
//  callback: MediaSessionCompat.Callback
//): Boolean = if (isCarHardKey(keyEvent) && keyCode.isPreviousOrNextMedia()) {
//  when (keyEvent.action) {
//    KeyEvent.ACTION_DOWN -> handleDownAction(keyEvent.isLongPress, keyCode)
//    KeyEvent.ACTION_UP -> handleUpAction(keyCode)
//    else -> false
//  }
//} else false
//
//private fun handleUpAction(keyCode: Int): Boolean {
//  var handled = false
//  if (!seeking) {
//    when (keyCode) {
//      KeyEvent.KEYCODE_MEDIA_NEXT -> {
//        if (enabledActions.hasSkipToNext) {
//          scope.launch { next() }
//          handled = true
//        }
//      }
//      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
//        if (enabledActions.hasSkipToPrevious) {
//          scope.launch { previous() }
//          handled = true
//        }
//      }
//    }
//  }
//  return handled
//}
//
//private fun handleDownAction(isLongPress: Boolean, keyCode: Int): Boolean {
//  var handled = false
//  if (isSeekable && isLongPress) {
//    when (keyCode) {
//      KeyEvent.KEYCODE_MEDIA_NEXT -> {
//        scope.launch { fastForward() }
//        handled = true
//      }
//      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
//        scope.launch { rewind() }
//        handled = true
//      }
//    }
//  }
//  return handled
//}
//
//private fun isCarHardKey(event: KeyEvent): Boolean = uiModeManager.inCarMode &&
//  event.deviceId == 0 && (event.flags and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0)
}

private sealed interface LocalAudioCommand {
  suspend fun process(localAudioQueue: LocalAudioQueue)

  class Command(private val block: suspend LocalAudioQueue.() -> Unit) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = block(localAudioQueue)
  }

  class AsyncCommand(private val block: LocalAudioQueue.() -> Unit) : LocalAudioCommand {
    override suspend fun process(localAudioQueue: LocalAudioQueue) = block(localAudioQueue)
  }

  class DeferredResult<T>(
    private val deferred: CompletableDeferred<T>,
    private val block: suspend LocalAudioQueue.() -> T
  ) : LocalAudioCommand {

    override suspend fun process(localAudioQueue: LocalAudioQueue) {
      try {
        deferred.complete(block(localAudioQueue))
      } catch (e: Throwable) {
        deferred.completeExceptionally(e)
      }
    }
  }
}

//private val Intent.keyEvent: KeyEvent?
//  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)
//
//private val KeyEvent.isDownAction: Boolean
//  get() = action == KeyEvent.ACTION_DOWN
//
//private fun Int.isPlayOrToggle() =
//  (this == KeyEvent.KEYCODE_MEDIA_PLAY || this == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
//
//private fun Int.isPreviousOrNextMedia() =
//  (this == KeyEvent.KEYCODE_MEDIA_PREVIOUS || this == KeyEvent.KEYCODE_MEDIA_NEXT)
//
//val UiModeManager.inCarMode: Boolean
//  get() = currentModeType == Configuration.UI_MODE_TYPE_CAR
