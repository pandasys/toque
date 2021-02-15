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

package com.ealva.toque.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media.MediaBrowserServiceCompat
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.log._e
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.session.BrowserResult
import com.ealva.toque.service.session.EMPTY_PLAYBACK_STATE
import com.ealva.toque.service.session.MediaSession
import com.ealva.toque.service.session.NOTHING_PLAYING
import com.ealva.toque.service.session.NullMediaSession
import com.ealva.toque.service.session.PlaybackActions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val LOG by lazyLogger(MediaPlayerService::class)

class MediaPlayerService : MediaBrowserServiceCompat(), LifecycleOwner, ToqueMediaController {
  // Because we inherit from MediaBrowserServiceCompat we need to maintain our own lifecycle
  private val dispatcher = ServiceLifecycleDispatcher(this)
  override fun getLifecycle(): Lifecycle = dispatcher.lifecycle

  override val isActive: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
    value = false
  }

  private var session: MediaSession = NullMediaSession

  @SuppressLint("UnspecifiedImmutableFlag")
  private fun ensureSession(): MediaSession {
    if (session === NullMediaSession) {
      session = MediaSession(context = this, lifecycleOwner = this, active = true)
      packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
        session.setSessionActivity(PendingIntent.getActivity(this, 0, sessionIntent, 0))
      }
      sessionToken = session.token
      session.setState(EMPTY_PLAYBACK_STATE)
      session.setMetadata(NOTHING_PLAYING)
      lifecycleScope.launch {
        LOG._e { it("start collecting") }
        session.eventFlow.collect { event ->
          LOG._e { it("event=%s", event.javaClass.simpleName) }
        }
      }
    }
    return session
  }

  override fun onCreate() {
    dispatcher.onServicePreSuperOnCreate()
    super.onCreate()
    ensureSession()
  }

  inner class MediaServiceBinder : Binder() {
    val controller: ToqueMediaController
      get() = this@MediaPlayerService
  }

  private val binder = MediaServiceBinder()
  override fun onBind(intent: Intent): IBinder? {
    dispatcher.onServicePreSuperOnBind()
    LOG._e { it("onBind action=%s", intent.action ?: "null") }
    ensureSession()
    return when (intent.action) {
      SERVICE_INTERFACE -> super.onBind(intent)
      else -> binder
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    dispatcher.onServicePreSuperOnStart()
    LOG._e { it("onStartCommand") }
    return Service.START_NOT_STICKY
  }

  override fun onDestroy() {
    dispatcher.onServicePreSuperOnDestroy()
    super.onDestroy()
    LOG._e { it("onDestroy") }
    ensureSession().release()
  }

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle?
  ): BrowserRoot? =
    ensureSession().browser.onGetRoot(clientPackageName, clientUid, rootHints ?: Bundle.EMPTY)

  override fun onLoadChildren(parentId: String, result: BrowserResult) =
    ensureSession().browser.onLoadChildren(parentId, result)

  override fun onSearch(query: String, extras: Bundle?, result: BrowserResult) =
    ensureSession().browser.onSearch(query, extras ?: Bundle.EMPTY, result)

  override val mediaIsLoaded: Boolean
    get() = false

  override fun setCurrentQueue(type: QueueType) {
    TODO("Not yet implemented")
  }

  override fun play(transition: TransitionSelector) {
    TODO("Not yet implemented")
  }

  override fun stop() {
    TODO("Not yet implemented")
  }

  override fun pause(transition: TransitionSelector) {
    TODO("Not yet implemented")
  }

  override val isSeekable: Boolean
    get() = TODO("Not yet implemented")

  override fun seekTo(position: Millis) {
    TODO("Not yet implemented")
  }

  override fun nextShuffleMode(): ShuffleMode {
    TODO("Not yet implemented")
  }

  override fun nextRepeatMode(): RepeatMode {
    TODO("Not yet implemented")
  }

  override val position: Millis
    get() = TODO("Not yet implemented")

  override val duration: Millis
    get() = TODO("Not yet implemented")

  override fun togglePlayPause() {
    TODO("Not yet implemented")
  }

  override fun next() {
    TODO("Not yet implemented")
  }

  override fun previous() {
    TODO("Not yet implemented")
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    TODO("Not yet implemented")
  }

  override fun loadUri(uri: Uri?) {
    TODO("Not yet implemented")
  }

  override var enabledActions: PlaybackActions = PlaybackActions()

  enum class Action {
    None {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {}
    },
    PreviousSong {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.previousSong()
      }
    },
    Play {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.play(TransitionSelector.Current)
      }
    },
    TogglePlayPause {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.togglePlayPause()
      }
    },
    NextSong {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.nextSong()
      }
    },
    RemoveNotification {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.removeNotificationAndStopForeground()
      }
    },
    UpdateWidgets {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
//        if (appWidgetIds != null) {
//          playerService.tempForegroundNotificationIfRequired()
//          playerService.updateWidgets(
//            appWidgetIds,
//            playerService.currentItem,
//            playerService.currentItem.isPlaying
//          )
//        }
      }
    },
    NextRepeat {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.nextRepeatMode()
      }
    },
    ToggleShuffle {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.toggleShuffle()
      }
    },
    ExternalIntent {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.handleExternalIntent(intent)
      }
    },
    UpdateNotificationChannelSettings {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
//        playerService.updateNotificationChannelSettings()
      }
    };

    abstract fun execute(playerService: MediaPlayerService, intent: Intent)
  }

  companion object {
    fun getComponentName(context: Context): ComponentName =
      ComponentName(context, MediaPlayerService::class.java)

    fun makeStartIntent(context: Context, action: Action, externalIntent: Intent?): Intent {
      val intent = Intent(action.name, null, context, MediaPlayerService::class.java)
      if (externalIntent != null) {
        intent.putExtra(EXTRA_ORIGINAL_INTENT, externalIntent)
      }
      return intent
    }
  }
}

private const val PLAYBACK_BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
  PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
  PlaybackStateCompat.ACTION_PLAY_FROM_URI or
  PlaybackStateCompat.ACTION_PLAY_PAUSE

private const val EXTRA_ORIGINAL_INTENT = "OriginalIntentExtrasKey"
