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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media.MediaBrowserServiceCompat
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioDaoEvent
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.scanner.MediaScannerJobIntentService
import com.ealva.toque.scanner.MediaScannerJobIntentService.RescanType
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.session.BrowserResult
import com.ealva.toque.service.session.EMPTY_PLAYBACK_STATE
import com.ealva.toque.service.session.MediaSession
import com.ealva.toque.service.session.NOTHING_PLAYING
import com.ealva.toque.service.session.NullMediaSession
import com.ealva.toque.service.session.PlaybackActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(MediaPlayerService::class)

private const val ADD_NEW_MEDIA_COUNT = 10

private val servicePrefsSingleton: PlayerServicePrefsSingleton = PlayerServicePrefsSingleton(
  PlayerServicePrefs.Companion::make,
  Toque.appContext,
  "PlayerServicePrefs"
)

class MediaPlayerService : MediaBrowserServiceCompat(), LifecycleOwner, ToqueMediaController {
  // Because we inherit from MediaBrowserServiceCompat we need to maintain our own lifecycle
  private val dispatcher = ServiceLifecycleDispatcher(this)
  override fun getLifecycle(): Lifecycle = dispatcher.lifecycle
  private inline val scope: LifecycleCoroutineScope get() = lifecycleScope
  private val audioMediaDao: AudioMediaDao by inject()

  override val isActive = MutableStateFlow(false)

  private lateinit var servicePrefs: PlayerServicePrefs
  private val queueFactory = PlayableQueueFactory()
  private var currentQueue: PlayableMediaQueue<*> = NullPlayableMediaQueue

  private var _mediaSession: MediaSession = NullMediaSession
  private val mediaSession: MediaSession
    @SuppressLint("UnspecifiedImmutableFlag")
    get() {
      if (_mediaSession === NullMediaSession) {
        LOG._i { it("create MediaSession") }
        _mediaSession = MediaSession(context = this, lifecycleOwner = this, active = true)
        packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
          _mediaSession.setSessionActivity(PendingIntent.getActivity(this, 0, sessionIntent, 0))
        }
        sessionToken = _mediaSession.token
        _mediaSession.setState(EMPTY_PLAYBACK_STATE)
        _mediaSession.setMetadata(NOTHING_PLAYING)
        scope.launch {
          _mediaSession.eventFlow
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .collect { event ->
              LOG._i { it("event=%s", event.javaClass.simpleName) }
            }
        }
      }
      return _mediaSession
    }

  override fun onCreate() {
    dispatcher.onServicePreSuperOnCreate()
    super.onCreate()
    val session = mediaSession
    scope.launchWhenCreated {
      servicePrefs = servicePrefsSingleton.instance()
      currentQueue = queueFactory.make(servicePrefs.currentQueueType(), servicePrefs)
    }
  }

  inner class MediaServiceBinder : Binder() {
    val controller: ToqueMediaController
      get() = this@MediaPlayerService
  }

  private val binder = MediaServiceBinder()
  override fun onBind(intent: Intent): IBinder? {
    dispatcher.onServicePreSuperOnBind()
    LOG._i { it("onBind action=%s", intent.action ?: "null") }
    mediaSession
    return when (intent.action) {
      SERVICE_INTERFACE -> super.onBind(intent)
      else -> binder
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    dispatcher.onServicePreSuperOnStart()
    LOG._i { it("onStartCommand action=%s", intent?.action ?: "none") }
    return Service.START_NOT_STICKY
  }

  override fun onDestroy() {
    dispatcher.onServicePreSuperOnDestroy()
    super.onDestroy()
    mediaSession.release()
  }

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle?
  ): BrowserRoot? =
    mediaSession.browser.onGetRoot(clientPackageName, clientUid, rootHints ?: Bundle.EMPTY)

  override fun onLoadChildren(parentId: String, result: BrowserResult) =
    mediaSession.browser.onLoadChildren(parentId, result)

  override fun onSearch(query: String, extras: Bundle?, result: BrowserResult) =
    mediaSession.browser.onSearch(query, extras ?: Bundle.EMPTY, result)

  override val mediaIsLoaded: Boolean
    get() = false

  override fun setCurrentQueue(type: QueueType) {
    TODO("Not yet implemented")
  }

  override fun play(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun stop() {
    TODO("Not yet implemented")
  }

  override fun pause(immediate: Boolean) {
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

  override fun loadUri(uri: Uri) {
    TODO("Not yet implemented")
  }

  override fun startMediaScannerFirstRun() {
    LOG._i { it("startMediaScannerFirstRun") }
    scope.launch {
      var addToQueueCount = ADD_NEW_MEDIA_COUNT
      audioMediaDao
        .audioDaoEvents
        .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
        .collect { event ->
          LOG._e { it("audioDaoEvent=%s", event) }
          if (event is AudioDaoEvent.MediaCreated) {
            LOG._e { it("MediaCreated:%d", event.mediaIds.size) }
          }
        }
    }
    MediaScannerJobIntentService.startRescan(this, "FirstRun", RescanType.ModifiedSinceLast)
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
        playerService.play(false)
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
