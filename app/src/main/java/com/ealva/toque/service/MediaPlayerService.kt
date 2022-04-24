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

package com.ealva.toque.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MEDIA_BUTTON
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media.MediaBrowserServiceCompat
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.R
import com.ealva.toque.android.content.doNotHaveReadPermission
import com.ealva.toque.android.content.intentFilterOf
import com.ealva.toque.android.content.onBroadcast
import com.ealva.toque.android.content.orNullObject
import com.ealva.toque.app.Toque
import com.ealva.toque.art.ArtworkUpdateListener
import com.ealva.toque.audioout.AudioOutputState
import com.ealva.toque.audioout.handleAudioOutputStateBroadcasts
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.ScreenAction
import com.ealva.toque.service.session.server.BrowserResult
import com.ealva.toque.service.session.server.MediaSession
import com.ealva.toque.service.session.server.MediaSessionState
import com.ealva.toque.service.widget.ActionToPendingIntent
import com.ealva.toque.service.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.seconds
import androidx.core.app.NotificationCompat.Builder as NotificationBuilder

private val LOG by lazyLogger(MediaPlayerService::class)

private val servicePrefsSingleton: PlayerServicePrefsSingleton = PlayerServicePrefsSingleton(
  PlayerServicePrefs.Companion::make,
  Toque.appContext,
  "PlayerServicePrefs"
)

private const val UPDATING_NOTIFICATION_ID = 1010
private const val UPDATING_CHANNEL_ID = "com.ealva.toque.UpdatingChannel"

private val TEMP_NOTIFICATION_MAX_DURATION = 5.seconds

class MediaPlayerService : MediaBrowserServiceCompat(), ToqueMediaController, LifecycleOwner {
  // Because we inherit from MediaBrowserServiceCompat we need to maintain our own lifecycle
  private val dispatcher = ServiceLifecycleDispatcher(this)
  private inline val scope: LifecycleCoroutineScope get() = lifecycleScope
  private val queueFactory: PlayableQueueFactory by inject()
  private val audioMediaDao: AudioMediaDao by inject()
  private val artworkUpdateListener: ArtworkUpdateListener by inject()
  private val notificationManager: NotificationManager by inject()
  private var inForeground = false
  private var inTempNotification = false
  private var isStarted = false
  private lateinit var mediaSession: MediaSession
  private val audioOutputState: AudioOutputState by inject()
  private val keyguardManager: KeyguardManager by inject()
  private val widgetUpdater: WidgetUpdater by inject()

  /**
   * Replay is 10 as we will emit actions before collecting. Collection will start when a
   * queue has become active. Until then a user could be repeatedly clicking a widget button or
   * something similar
   */
  private val incomingActions = MutableSharedFlow<ActionWithIntent>(replay = 10)
  private var incomingActionsJob: Job? = null
  private var tempNotificationJob: Job? = null

  override fun getLifecycle(): Lifecycle = dispatcher.lifecycle
  override val currentQueueFlow = MutableStateFlow<PlayableMediaQueue<*>>(NullPlayableMediaQueue)
  private inline var currentQueue: PlayableMediaQueue<*>
    get() = currentQueueFlow.value
    set(value) {
      currentQueueFlow.value = value
    }

  private suspend fun servicePrefs(): PlayerServicePrefs = servicePrefsSingleton.instance()

  override fun onCreate() {
    dispatcher.onServicePreSuperOnCreate()
    super.onCreate()
    mediaSession = MediaSession.make(
      context = this,
      audioMediaDao = audioMediaDao,
      notificationListener = NotificationListener(),
      lifecycleOwner = this,
      active = true // will set active after setting the current queue for the first time
    )
    widgetUpdater.setMediaSession(mediaSession, Companion)
    packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
      mediaSession.sessionActivity =
        PendingIntent.getActivity(this, 0, sessionIntent, FLAG_IMMUTABLE)
    }
    sessionToken = mediaSession.token
    scope.launch {
      setCurrentQueue(servicePrefs().currentQueueType(), false)
      mediaSession.isActive = true
    }
    listenToBroadcasts()
    artworkUpdateListener.start()
    listenToCurrentQueue()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(UPDATING_CHANNEL_ID)
    }
  }

  private fun listenToCurrentQueue() {
    currentQueueFlow
      .onEach { queue -> handleQueue(queue) }
      .launchIn(scope)
  }

  private fun handleQueue(queue: PlayableMediaQueue<*>) {
    incomingActionsJob?.cancel()
    incomingActionsJob = if (queue !== NullPlayableMediaQueue) {
      incomingActions
        .onEach { action -> executeAction(action) }
        .catch { cause -> LOG.e(cause) { it("Incoming action flow error") } }
        .launchIn(scope)
    } else {
      null
    }
  }

  private fun executeAction(action: ActionWithIntent) {
    action.execute(this)
  }

  inner class MediaServiceBinder : Binder() {
    val controller: ToqueMediaController
      get() = this@MediaPlayerService
  }

  private val binder = MediaServiceBinder()
  override fun onBind(intent: Intent): IBinder? {
//    LOG._e { it("onBind %s", intent.action ?: "null") }
    dispatcher.onServicePreSuperOnBind()
    return if (doNotHaveReadPermission()) {
      LOG.e { it("Don't have read external permission. Bind disallowed.") }
      null
    } else {
      when (intent.action) {
        SERVICE_INTERFACE -> super.onBind(intent)
        else -> binder
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//    LOG._e { it("onStartCommand %s", intent?.action ?: "null") }
    dispatcher.onServicePreSuperOnStart()

    val startIntent = intent.orNullObject()
    val action = startIntent.action.orEmpty()

    if (action.isNotEmpty()) {
      isStarted = true
      handleStartIntent(startIntent, action)
    }
    return Service.START_STICKY
  }

  private fun handleStartIntent(intent: Intent, action: String) {
//    LOG._e { it("handleStartIntent action:%s", action) }
    if (action.isNotEmpty()) {
      if (ACTION_MEDIA_BUTTON == action) {
        mediaSession.handleMediaButtonIntent(intent)?.let {
          // if this is a recognized key event, prevent redelivery on service restart
          val emptyIntent = Intent(intent)
          emptyIntent.action = ""
          startService(emptyIntent)
        }
      } else {
        tempForegroundNotificationIfRequired()
        scope.launch {
          incomingActions.emit(ActionWithIntent(actionFromName(action), intent))
        }
      }
    }
  }

  private fun actionFromName(actionString: String?): Action {
    return if (actionString.isNullOrEmpty()) {
      Action.None
    } else {
      try {
        Action.valueOf(actionString)
      } catch (e: IllegalArgumentException) {
        LOG.e(e) { +it("Unrecognized action exception '%s'", actionString) }
        Action.None
      }
    }
  }

  override fun onDestroy() {
    dispatcher.onServicePreSuperOnDestroy()
    super.onDestroy()
    isStarted = false
    mediaSession.isActive = false
    mediaSession.release()
    artworkUpdateListener.stop()
    LOG._e { it("onDestroy") }
  }

  override fun onGetRoot(
    packageName: String,
    clientUid: Int,
    rootHints: Bundle?
  ): BrowserRoot? = mediaSession.browser.onGetRoot(
    packageName,
    clientUid,
    rootHints ?: Bundle.EMPTY
  )

  override fun onLoadChildren(parentId: String, result: BrowserResult) =
    mediaSession.browser.onLoadChildren(parentId, result)

  override fun onSearch(query: String, extras: Bundle?, result: BrowserResult) =
    mediaSession.browser.onSearch(query, extras ?: Bundle.EMPTY, result)

  override suspend fun setCurrentQueue(type: QueueType, resume: Boolean) {
    val current = currentQueue
    if (current.queueType != type) {
      if (current !== NullPlayableMediaQueue) {
        current.deactivate()
        mediaSession.resetSessionEventReplayCache()
      }
      val newQueue: PlayableMediaQueue<*> = queueFactory.make(type, mediaSession, mediaSession)
      newQueue.activate(resume, PlayNow(false))
      newQueue.isActive
        .onEach { isActive -> if (isActive) currentQueue = newQueue }
        .takeWhile { isActive -> !isActive }
        .launchIn(scope)
    }
  }

  private fun putInForeground(notificationId: Int, notification: Notification) {
    startForegroundService(this, Action.None)
    startForeground(notificationId, notification)
    inForeground = true
    maybeRemoveTempForeground()
  }

  private fun tempForegroundNotificationIfRequired() {
    if (!inForeground) {
      inTempNotification = true
      startForeground(UPDATING_NOTIFICATION_ID, makeTempNotification())
      tempNotificationJob?.cancel()
      tempNotificationJob = scope.launch {
        delay(TEMP_NOTIFICATION_MAX_DURATION)
        maybeRemoveTempForeground()
      }
    }
  }

  private fun makeTempNotification(): Notification {
    return NotificationBuilder(this, UPDATING_CHANNEL_ID).apply {
      setSmallIcon(R.drawable.ic_toque)
      setContentTitle(fetch(R.string.UpdatingQueue))
      setContentIntent(mediaSession.sessionActivity)
      setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      setShowWhen(false)
      setCategory(NotificationCompat.CATEGORY_SERVICE)
      setAutoCancel(false)
      priority = NotificationCompat.PRIORITY_LOW
      color = NotificationCompat.COLOR_DEFAULT
      setOngoing(true)
    }.build()
  }

  private fun maybeRemoveTempForeground() {
    if (inTempNotification) {
      inTempNotification = false
      tempNotificationJob?.cancel()
      tempNotificationJob = null
      notificationManager.cancel(UPDATING_NOTIFICATION_ID)
      if (!inForeground) stopForeground(true)
    }
  }

  private inner class NotificationListener : MediaSessionState.NotificationListener {
    override fun onPosted(notificationId: Int, notification: Notification) {
      if (!inForeground) putInForeground(notificationId, notification)
    }

    override fun stopForeground() = removeFromForegroundAndStopSelf()
  }

  private fun removeFromForegroundAndStopSelf() {
    stopForeground(false)
    inForeground = false
    stopSelf()
  }

  private fun listenToBroadcasts() {
    // This creates a lifecycle aware object, no unregister needed
    handleAudioOutputStateBroadcasts(audioOutputState)
    onBroadcast(intentFilterOf(ACTION_SCREEN_ON, ACTION_SCREEN_OFF)) { intent ->
      handleScreenAction(ScreenAction.screenAction(intent), keyguardManager.isKeyguardLocked)
    }
  }

  private fun handleScreenAction(action: ScreenAction?, keyguardLocked: Boolean) {
    if (action != null) currentQueue.handleScreenAction(action, keyguardLocked)
  }

  private data class ActionWithIntent(val action: Action, val intent: Intent) {
    fun execute(playerService: MediaPlayerService) = action.execute(playerService, intent)
  }

  enum class Action {
    None {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {}
    },
    TogglePlayPause {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.togglePlayPause()
      }
    },
    Previous {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.previous()
      }
    },
    Next {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.next()
      }
    },
    NextRepeat {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        playerService.nextRepeatMode()
      }
    },
    NextShuffle {
      override fun execute(playerService: MediaPlayerService, intent: Intent) =
        playerService.nextShuffleMode()
    },
    UpdateWidgets {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {
        intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.let { appWidgetIds ->
          playerService.updateWidgets(appWidgetIds)
        }
      }
    };

    abstract fun execute(playerService: MediaPlayerService, intent: Intent)
  }

  private fun togglePlayPause() {
    currentQueue.togglePlayPause()
  }

  private fun previous() {
    currentQueue.previous()
  }

  private fun next() {
    currentQueue.next()
  }

  private fun nextRepeatMode() {
    currentQueue.nextRepeatMode()
  }

  private fun nextShuffleMode() {
    currentQueue.nextShuffleMode()
  }

  private fun updateWidgets(@Suppress("UNUSED_PARAMETER") appWidgetIds: IntArray) {
    currentQueue.ifActiveRefreshMediaState()
  }

  companion object : ActionToPendingIntent {
    private fun NotificationManager.createNotificationChannel(id: String) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createNotificationChannel(
          NotificationChannel(id, fetch(R.string.UpdateQueueChannelName), IMPORTANCE_LOW).apply {
            description = fetch(R.string.UpdatingChannelDescription)
          }
        )
      }
    }

    fun startForegroundService(
      context: Context,
      action: Action
    ) = with(context) {
      ContextCompat.startForegroundService(this, makeStartIntent(this, action, null))
    }

    fun makeStartIntent(context: Context, action: Action, externalIntent: Intent?): Intent {
      val intent = Intent(action.name, null, context, MediaPlayerService::class.java)
      if (externalIntent != null) {
        intent.putExtra(EXTRA_ORIGINAL_INTENT, externalIntent)
      }
      return intent
    }

    fun startForWidgetsUpdate(
      context: Context,
      appWidgetIds: IntArray
    ) = with(context) {
      ContextCompat.startForegroundService(
        this,
        makeStartIntent(this, Action.UpdateWidgets, null)
          .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
      )
    }

    private fun makeStartPendingIntent(
      context: Context,
      action: Action,
      id: Int
    ): PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(context, id, makeStartIntent(context, action, null), 0)
    } else {
      PendingIntent.getService(context, id, makeStartIntent(context, action, null), FLAG_IMMUTABLE)
    }

    override fun makeIntent(ctx: Context, action: Action, requestId: Int): PendingIntent =
      makeStartPendingIntent(ctx, action, requestId)
  }
}

private const val EXTRA_ORIGINAL_INTENT = "OriginalIntentExtrasKey"
