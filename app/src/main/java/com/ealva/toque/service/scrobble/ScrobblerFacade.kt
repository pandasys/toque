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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.service.queue.AudioQueueItem
import com.ealva.toque.service.queue.NullAudioQueueItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

private val LOG by lazyLogger(ScrobblerFacade::class)

/**
 * A ScrobblerFacade is a Scrobbler but also handles reacting to a user preference change, making
 * the correct scrobbler, and offloads calling the real scrobbler on an IO dispatcher.
 */
interface ScrobblerFacade : Scrobbler {
  companion object {
    operator fun invoke(
      prefs: AppPreferences,
      scrobblerFactory: ScrobblerFactory,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Scrobbler {
      return ScrobblerFacadeImpl(prefs, scrobblerFactory, dispatcher)
    }
  }
}

/**
 * Wraps the real scrobbler as it may change based on user selection.
 */
private class ScrobblerFacadeImpl(
  private val prefs: AppPreferences,
  private val scrobblerFactory: ScrobblerFactory,
  private val dispatcher: CoroutineDispatcher
) : Scrobbler {
  private var lastSelectedScrobbler = prefs.scrobbler()
  private var realScrobbler = scrobblerFactory.make(lastSelectedScrobbler)
  private var lastActiveItem: AudioQueueItem = NullAudioQueueItem
  private var lastAction = ScrobblerAction.Shutdown
  private val scope: CoroutineScope = MainScope().apply {
    prefs.scrobblerFlow()
      .onEach { selectedScrobbler ->
        if (selectedScrobbler != lastSelectedScrobbler) {
          lastSelectedScrobbler = selectedScrobbler
          realScrobbler = makeNewShutdownOld(
            selectedScrobbler,
            realScrobbler,
            lastAction,
            lastActiveItem
          )
        }
      }
      .launchIn(this)
  }

  private fun makeNewShutdownOld(
    selectedScrobbler: ScrobblerPackage,
    oldScrobbler: Scrobbler,
    action: ScrobblerAction,
    item: AudioQueueItem
  ) = scrobblerFactory.make(selectedScrobbler).also { newScrobbler ->
    doScrobble {
      ScrobblerAction.Shutdown.scrobble(oldScrobbler, NullAudioQueueItem)
      if (action.isStartOrResume && item.isValid) action.scrobble(newScrobbler, item)
    }
  }

  override fun start(item: AudioQueueItem) {
    scrobble(ScrobblerAction.Start, item)
  }

  override fun resume(item: AudioQueueItem) {
    scrobble(ScrobblerAction.Resume, item)
  }

  override fun pause(item: AudioQueueItem) {
    scrobble(ScrobblerAction.Pause, item)
  }

  override fun complete(item: AudioQueueItem) {
    if (lastActiveItem == item) {
      scrobble(ScrobblerAction.Complete, item)
    }
  }

  override fun shutdown() {
    scope.cancel()
    realScrobbler.shutdown()
    realScrobbler = NullScrobbler
    lastActiveItem = NullAudioQueueItem
    lastAction = ScrobblerAction.Shutdown
  }

  /**
   * I'm doing this in the background because of possible lengthy execution times. Getting any
   * processing away from any UI or media player interactions. I've seen the resultant intent
   * broadcast take 100s of milliseconds to fire.
   */
  private fun scrobble(action: ScrobblerAction, item: AudioQueueItem) {
    lastAction = action
    lastActiveItem = item
    if (realScrobbler !== NullScrobbler) { // if this won't result in a scrobble, don't bother
      doScrobble {
        action.scrobble(realScrobbler, item)
      }
    }
  }

  private val scrobbleFlow = MutableSharedFlow<Runnable>(extraBufferCapacity = 10).apply {
    onEach { runnable -> runnable.run() }
      .onCompletion { LOG.i { it("Scrobble flow completed") } }
      .catch { cause -> LOG.e(cause) { it("Scrobble flow exception") } }
      .launchIn(scope + dispatcher)
  }

  private fun doScrobble(event: Runnable) {
    scrobbleFlow.tryEmit(event)
  }

  private enum class ScrobblerAction {
    Start {
      override fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem) = scrobbler.start(item)
    },
    Pause {
      override fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem) = scrobbler.pause(item)
    },
    Resume {
      override fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem) = scrobbler.resume(item)
    },
    Complete {
      override fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem) = scrobbler.complete(item)
    },
    Shutdown {
      override fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem) = scrobbler.shutdown()
    };

    abstract fun scrobble(scrobbler: Scrobbler, item: AudioQueueItem)

    val isStartOrResume: Boolean
      get() = when (this) {
        Start -> true
        Resume -> true
        else -> false
      }
  }
}
