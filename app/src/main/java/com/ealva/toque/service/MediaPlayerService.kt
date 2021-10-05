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

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MEDIA_BUTTON
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
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
import com.ealva.toque.android.content.doNotHaveReadPermission
import com.ealva.toque.android.content.orNullObject
import com.ealva.toque.app.Toque
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.log.logExecTime
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.session.server.BrowserResult
import com.ealva.toque.service.session.server.MediaSession
import com.ealva.toque.service.session.server.MediaSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(MediaPlayerService::class)

private val servicePrefsSingleton: PlayerServicePrefsSingleton = PlayerServicePrefsSingleton(
  PlayerServicePrefs.Companion::make,
  Toque.appContext,
  "PlayerServicePrefs"
)

class MediaPlayerService :
  MediaBrowserServiceCompat(),
  ToqueMediaController,
  LifecycleOwner {
  // Because we inherit from MediaBrowserServiceCompat we need to maintain our own lifecycle
  private val dispatcher = ServiceLifecycleDispatcher(this)
  private inline val scope: LifecycleCoroutineScope get() = lifecycleScope
  private lateinit var servicePrefs: PlayerServicePrefs
  private val queueFactory: PlayableQueueFactory by inject()
  private val audioMediaDao: AudioMediaDao by inject()
  private var inForeground = false
  private var isStarted = false
  private lateinit var mediaSession: MediaSession

  override fun getLifecycle(): Lifecycle = dispatcher.lifecycle
  override val currentQueue = MutableStateFlow<PlayableMediaQueue<*>>(NullPlayableMediaQueue)

  override fun onCreate() {
    dispatcher.onServicePreSuperOnCreate()
    super.onCreate()
    mediaSession = MediaSession(
      context = this,
      audioMediaDao = audioMediaDao,
      notificationListener = NotificationListener(),
      lifecycleOwner = this,
      active = true
    )
    packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
      mediaSession.setSessionActivity(
        PendingIntent.getActivity(
          this,
          0,
          sessionIntent,
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) FLAG_IMMUTABLE else 0
        )
      )
    }
    sessionToken = mediaSession.token
    scope.launch {
      logExecTime({ time -> LOG.e { it("setCurrentQueue %d", time) } }) {
        servicePrefs = servicePrefsSingleton.instance()
        setCurrentQueue(servicePrefs.currentQueueType(), false)
        mediaSession.isActive = true
      }
    }
  }

  inner class MediaServiceBinder : Binder() {
    val controller: ToqueMediaController
      get() = this@MediaPlayerService
  }

  private val binder = MediaServiceBinder()
  override fun onBind(intent: Intent): IBinder? {
    return if (doNotHaveReadPermission()) {
      LOG.e { it("Don't have read external permission. Bind disallowed.") }
      null
    } else {
      dispatcher.onServicePreSuperOnBind()
      LOG._e { it("onBind %s", intent.action ?: "null") }
      when (intent.action) {
        SERVICE_INTERFACE -> super.onBind(intent)
        else -> binder
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    dispatcher.onServicePreSuperOnStart()

    val startIntent = intent.orNullObject()
    val action = startIntent.action.orEmpty()

    if (action.isNotEmpty()) {
      isStarted = true
      val intentIsPlay = intentIsPlay(startIntent)
      LOG._i { it("started=%s intentIsPlay=%s", isStarted, intentIsPlay) }
      handleStartIntent(startIntent, action)
    }
    return Service.START_STICKY
  }

  private fun intentIsPlay(intent: Intent): Boolean =
    if (ACTION_MEDIA_BUTTON == intent.action.orEmpty() && intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
      when (intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)?.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
        else -> false
      }
    } else {
      false
//      @Suppress("NON_EXHAUSTIVE_WHEN")
//      return when (actionFromName(intent.action)) {
//        Action.Play, Action.TogglePlayPause -> true
//        else -> false
//      }
    }

  private fun handleStartIntent(intent: Intent, action: String) {
    LOG._e { it("handleStartIntent action=%s", action) }
    if (action.isNotEmpty()) {
      if (ACTION_MEDIA_BUTTON == action) {
        LOG._e { it("is media button") }
        mediaSession.handleMediaButtonIntent(intent)?.let {
          // if this is a recognized key event, prevent redelivery on service restart
          val emptyIntent = Intent(intent)
          emptyIntent.action = ""
          startService(emptyIntent)
        }
      } else {
        // assume it's one of our actions. If not, None is returned
//        actionFromName(action).execute(this, intent)
      }
    }
  }

  override fun onDestroy() {
    dispatcher.onServicePreSuperOnDestroy()
    super.onDestroy()
    LOG._e { it("onDestroy") }
    isStarted = false
    mediaSession.isActive = false
    mediaSession.release()
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

  override val mediaIsLoaded: Boolean
    get() = false

  override suspend fun setCurrentQueue(type: QueueType, resume: Boolean) {
    val current = currentQueue.value
    if (current.queueType != type) {
      if (current !== NullPlayableMediaQueue) {
        current.deactivate()
        mediaSession.resetSessionEventReplayCache()
      }
      val newQueue = queueFactory.make(type, mediaSession, mediaSession)
      newQueue.activate(resume, PlayNow(false))
      newQueue.isActive.collect { isActive ->if (isActive) currentQueue.value = newQueue }
    }
  }

  private fun putInForeground(notificationId: Int, notification: Notification) {
    startForegroundService(this, Action.None)
    startForeground(notificationId, notification)
    inForeground = true
  }

  private fun removeFromForegroundAndStopSelf() {
    stopForeground(false)
    inForeground = false
    stopSelf()
  }

  private inner class NotificationListener : MediaSessionState.NotificationListener {
    override fun onPosted(notificationId: Int, notification: Notification) {
      if (!inForeground) putInForeground(notificationId, notification)
    }

    override fun onCanceled() = removeFromForegroundAndStopSelf()
  }

  enum class Action {
    None {
      override fun execute(playerService: MediaPlayerService, intent: Intent) {}
    };

    abstract fun execute(playerService: MediaPlayerService, intent: Intent)
  }

  companion object {
    fun startForegroundService(
      context: Context,
      action: Action
    ) = with(context.applicationContext) {
      ContextCompat.startForegroundService(this, makeStartIntent(this, action, null))
    }

    fun makeStartIntent(context: Context, action: Action, externalIntent: Intent?): Intent {
      val intent = Intent(action.name, null, context, MediaPlayerService::class.java)
      if (externalIntent != null) {
        intent.putExtra(EXTRA_ORIGINAL_INTENT, externalIntent)
      }
      return intent
    }
  }
}

private const val EXTRA_ORIGINAL_INTENT = "OriginalIntentExtrasKey"
