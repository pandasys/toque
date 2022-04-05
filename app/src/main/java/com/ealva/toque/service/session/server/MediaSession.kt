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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media.session.MediaButtonReceiver
import androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.prefstore.store.BasePreferenceStore
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.R
import com.ealva.toque.android.content.requireSystemService
import com.ealva.toque.app.Toque
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.asCompat
import com.ealva.toque.common.compatToRepeatMode
import com.ealva.toque.common.compatToShuffleMode
import com.ealva.toque.common.fetch
import com.ealva.toque.common.toStarRating
import com.ealva.toque.common.windowOf15
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.service.controller.SessionControlEvent
import com.ealva.toque.service.image.loadAsBitmap
import com.ealva.toque.service.session.common.Metadata
import com.ealva.toque.service.session.common.Metadata.Companion.NullMetadata
import com.ealva.toque.service.session.common.PlaybackActions
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.common.PlaybackState.Companion.NullPlaybackState
import com.ealva.toque.service.session.common.toCompat
import com.ealva.toque.service.session.common.toCompatMediaId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat.Builder as NotificationBuilder

typealias MediaButtonHandler = (KeyEvent, Int, Intent, MediaSessionCompat.Callback) -> Boolean

private val LOG by lazyLogger(MediaSession::class)

/**
 * This interface represents an implementation of the server side of client communication. Client
 * requests flow through here.
 */
interface MediaSessionControl {
  /**
   * Calls from transport controls, media buttons, and commands from controllers and the system,
   * flow through here as [SessionControlEvent]s
   */
  val eventFlow: Flow<SessionControlEvent>

  var sessionActivity: PendingIntent?

  /**
   * We want replay at startup so we don't lose anything during initial activation, but subsequently
   * switching to different queue should not replay anything meant for the previous queue
   */
  fun resetSessionEventReplayCache()

  /**
   * Set a handler to receive the [KeyEvent], keycode, Intent, and Callback for processing other
   * than the default provided. It's expected the [MediaButtonHandler] will use the
   * [MediaSessionCompat.Callback] for further processing. If [MediaButtonHandler] hasn't been set
   * or returns false, default processing will handle the button.
   */
  fun onMediaButton(handler: MediaButtonHandler?)

  fun handleMediaButtonIntent(intent: Intent): KeyEvent?

  fun makeMediaButtonPendingIntent(action: PlaybackActions.Action): PendingIntent
}

/**
 * This interface is used by the server to communicate state to the client
 */
interface MediaSessionState {
  /**
   * If true this session is active and ready to receive commands.  If set to false your session's
   * controller may not be discoverable.
   */
  var isActive: Boolean
  var contentType: AudioFocusManager.ContentType

  val playbackStateFlow: StateFlow<PlaybackState>
  fun setPlaybackState(state: PlaybackState)

  val metadataFlow: StateFlow<Metadata>
  fun setMetadata(metadata: Metadata)

  val shuffleModeFlow: StateFlow<ShuffleMode>
  fun setShuffleMode(mode: ShuffleMode)

  val repeatModeFlow: StateFlow<RepeatMode>
  fun setRepeatMode(mode: RepeatMode)

  fun getIconBitmap(): Bitmap?

  interface NotificationListener {
    fun onPosted(notificationId: Int, notification: Notification)
    fun stopForeground()
  }

  /**
   * Send [queue] info to clients for display. Because we may need to limit the actual amount of
   * data sent to the session include the [currentItemIndex] which allows a moving window to be
   * created.
   *
   * If [indexChange] is true it indicates this call is being made because the current item in the
   * queue changed and not because the queue contents changes. This is checked so that if the entire
   * queue is being sent, and not just a window, we don't need to send the queue again
   */
  fun setQueue(queue: List<AudioItem>, currentItemIndex: Int, indexChange: Boolean = false)

  /** Sets the title of the play queue, eg. "Now Playing" */
  fun setQueueTitle(title: String)
}

private const val NOW_PLAYING_NOTIFICATION_ID = 1010
private const val NOW_PLAYING_CHANNEL_ID = "com.ealva.toque.NowPlayingChannel"

private val sessionPrefsSingleton: MediaSessionPrefsSingleton = MediaSessionPrefsSingleton(
  ::MediaSessionPrefs,
  Toque.appContext,
  "MediaSessionPrefs"
)

private val notificationPrefsSingleton: NotificationPrefsSingleton = NotificationPrefsSingleton(
  NotificationPrefs.Companion::make,
  Toque.appContext,
  "NotificationPrefs"
)

/**
 * Wraps a [MediaSessionCompat] and provides an interface with the types we want to see and handles
 * some housekeeping such as lifecycle management, handling [MediaSessionCompat.Callback] and
 * producing a flow
 */
interface MediaSession : MediaSessionControl, MediaSessionState, RecentMediaProvider {

  /**
   * Token object that can be used by apps to create a MediaControllerCompat for interacting with
   * this session. The owner of the session is responsible for deciding how to distribute these
   * tokens. Used internally for Notification
   */
  val token: MediaSessionCompat.Token

  val browser: MediaSessionBrowser

  /**
   * This must be called when an app has finished performing playback. If playback is expected to
   * start again shortly the session can be left open, but it must be released if your activity or
   * service is being destroyed.
   */
  fun release()

//  /** Set an intent for launching UI for this session */
//  fun setSessionActivity(pi: PendingIntent)

  companion object {
    fun make(
      context: Context,
      audioMediaDao: AudioMediaDao,
      notificationListener: MediaSessionState.NotificationListener,
      lifecycleOwner: LifecycleOwner,
      active: Boolean,
      dispatcher: CoroutineDispatcher = Dispatchers.Main,
      ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MediaSession {
      return MediaSessionImpl(
        context,
        makeMediaSessionCompat(context, active),
        audioMediaDao,
        context.requireSystemService<NotificationManager>().apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(NOW_PLAYING_CHANNEL_ID)
          }
          // Cancel all notifications to handle the case where the Service was killed and
          // restarted by the system.
          cancelAll()
        },
        notificationListener,
        NOW_PLAYING_CHANNEL_ID,
        dispatcher,
        ioDispatcher
      ).apply {
        lifecycleOwner.lifecycle.addObserver(this)
      }
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

    private fun NotificationManager.createNotificationChannel(id: String) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createNotificationChannel(
          NotificationChannel(id, fetch(R.string.NowPlayingChannelName), IMPORTANCE_LOW).apply {
            description = fetch(R.string.NowPlayingChannelDescription)
          }
        )
      }
    }
  }
}

private const val MSG_START_OR_UPDATE_NOTIFICATION = 0

private val Context.mediaSessionTag: String get() = "${getString(R.string.app_name)}MediaSession"

private class MediaSessionImpl(
  private val ctx: Context,
  private val session: MediaSessionCompat,
  audioMediaDao: AudioMediaDao,
  private val notificationManager: NotificationManager,
  private val notificationListener: MediaSessionState.NotificationListener,
  private val channelId: String,
  dispatcher: CoroutineDispatcher,
  private val ioDispatcher: CoroutineDispatcher
) : MediaSession, DefaultLifecycleObserver {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var allowNotifications = false

  /** [msgHandler] for async notification update and bitmap retrieval */
  private var msgHandler = makeHandler()
  private var isNotificationStarted = false

  override val playbackStateFlow = MutableStateFlow(NullPlaybackState)
  override val metadataFlow = MutableStateFlow(NullMetadata)
  override val shuffleModeFlow =
    MutableStateFlow(session.controller.shuffleMode.compatToShuffleMode())
  override val repeatModeFlow =
    MutableStateFlow(session.controller.repeatMode.compatToRepeatMode())

  /** Replay a small number in case we get an initial burst at startup before completely active */
  override val eventFlow: MutableSharedFlow<SessionControlEvent> = MutableSharedFlow(replay = 4)
  private val sessionCallback = SessionCallback(scope, eventFlow)

  override val token: MediaSessionCompat.Token = session.sessionToken

  private suspend fun notificationPrefs(): NotificationPrefs =
    notificationPrefsSingleton.instance()

  override var isActive: Boolean
    get() = session.isActive
    set(value) {
      session.isActive = value
    }

  override var contentType: AudioFocusManager.ContentType
    get() = sessionCallback.contentType
    set(value) {
      sessionCallback.contentType = value
    }

  override fun getIconBitmap(): Bitmap? =
    session.controller?.metadata?.description?.iconBitmap

  init {
    LOG._e { it("session.setCallback") }
    session.setCallback(sessionCallback)
    playbackStateFlow
      .onStart { LOG.i { it("PlaybackStateFlow started") } }
      .onEach { state -> handlePlaybackState(state) }
      .catch { cause -> LOG.e(cause) { it("Error processing PlaybackStateFlow") } }
      .onCompletion { cause -> LOG.i(cause) { it("PlaybackStateFlow completed") } }
      .launchIn(scope)

    metadataFlow
      .onEach { metadata -> handleMetadata(metadata) }
      .launchIn(scope)

    isActive = true
  }

  override fun release() {
    session.release()
    scope.cancel()
  }

  override fun onMediaButton(handler: MediaButtonHandler?) {
    sessionCallback.mediaButtonHandler = handler
  }

  override fun handleMediaButtonIntent(intent: Intent): KeyEvent? =
    MediaButtonReceiver.handleIntent(session, intent)

  override fun makeMediaButtonPendingIntent(action: PlaybackActions.Action): PendingIntent {
    return buildMediaButtonPendingIntent(ctx, action.value)
  }

  override var sessionActivity: PendingIntent?
    get() = session.controller.sessionActivity
    set(value) {
      session.setSessionActivity(value)
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun resetSessionEventReplayCache() {
    eventFlow.resetReplayCache()
  }

  override fun setPlaybackState(state: PlaybackState) {
    playbackStateFlow.value = state
  }

  private fun handlePlaybackState(state: PlaybackState) {
    if (state !== NullPlaybackState) {
      if (state.playState.isPlaying) {
        allowNotifications = true
      }
      val newState = PlaybackStateBuilder(state).build()
      session.setPlaybackState(newState)
      postStartOrUpdateNotification()
    }
  }

  override fun setMetadata(metadata: Metadata) {
    scope.launch(ioDispatcher) {
      session.setMetadata(metadata.toCompat(::resolveUriAsBitmap))
      metadataFlow.value = metadata
    }
  }

  private fun handleMetadata(metadata: Metadata) {
    scope.launch(ioDispatcher) {
      sessionPrefsSingleton.instance().lastMetadata.set(metadata) // save for getRecentMedia
      postStartOrUpdateNotification()
    }
  }

  private var lastQueueWindow: List<AudioItem> = emptyList()
  override fun setQueue(queue: List<AudioItem>, currentItemIndex: Int, indexChange: Boolean) {
    if (ctx.isCarMode()) {
      // Don't set same queue again. Not really an optimization because this is very fast, but
      // let's not unnecessarily give the client new lists
      val windowOf15 = queue.windowOf15(currentItemIndex)
      if (windowOf15 != lastQueueWindow) {
        lastQueueWindow = windowOf15
        session.setQueue(windowOf15.toCompat())
      }
    } else if (!indexChange) {
      // we currently don't limit the queue size so it might be very large. Let's copy it and
      // launch the building of the compat version. Likely not called in very fast succession
      val queueCopy = queue.toList()
      scope.launch {
        session.setQueue(queueCopy.toCompat())
      }
    }
  }

  override fun setQueueTitle(title: String) {
    session.setQueueTitle(title)
  }

  override fun setShuffleMode(mode: ShuffleMode) {
    session.setShuffleMode(mode.asCompat)
    shuffleModeFlow.value = mode
  }

  override fun setRepeatMode(mode: RepeatMode) {
    session.setRepeatMode(mode.asCompat)
    repeatModeFlow.value = mode
  }

  override suspend fun getRecentMedia(): MediaDescriptionCompat? {
    var metadata = metadataFlow.value
    if (metadata === NullMetadata) {
      metadata = sessionPrefsSingleton.instance().lastMetadata()
      metadataFlow.value = metadata
    }
    return if (metadata === NullMetadata) null else metadata.toCompat().description
  }

  override var browser: MediaSessionBrowser = MediaSessionBrowser(this, audioMediaDao, scope)

  override fun onDestroy(lifecycleOwner: LifecycleOwner) {
    session.setCallback(null)
    session.release()
    scope.cancel()
  }

  private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? = uri.loadAsBitmap()

  private fun NotificationBuilder.addAction(
    action: NotificationCompat.Action,
    index: Int,
    showInCompactView: Boolean,
    visibleActionsInCompactView: MutableList<Int>,
  ): Int {
    addAction(action)
    if (showInCompactView) visibleActionsInCompactView.add(index)
    return index + 1
  }

  private fun makePreviousAction() = NotificationCompat.Action(
    R.drawable.ic_previous,
    fetch(R.string.Previous),
    buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
  )

  private fun makePlayPauseAction(isPlaying: Boolean): NotificationCompat.Action {
    return if (isPlaying) {
      NotificationCompat.Action(
        R.drawable.ic_play_pause_playing_button,
        fetch(R.string.Play),
        buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_PAUSE)
      )
    } else {
      NotificationCompat.Action(
        R.drawable.ic_play_pause_paused_button,
        fetch(R.string.Pause),
        buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_PLAY)
      )
    }
  }

  private fun makeNextAction() = NotificationCompat.Action(
    R.drawable.ic_baseline_skip_next_24,
    fetch(R.string.Next),
    buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
  )

  private fun makeCloseAction() = NotificationCompat.Action(
    R.drawable.ic_outline_close_24,
    fetch(R.string.Stop),
    buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_STOP)
  )

  private fun NotificationBuilder.setMediaStyle(actions: List<Int>) {
    setStyle(
      androidx.media.app.NotificationCompat.MediaStyle()
        .setShowActionsInCompactView(*actions.toIntArray())
        .setMediaSession(session.sessionToken)
    )
  }

  private fun postStartOrUpdateNotification() {
    if (allowNotifications) {
      if (!msgHandler.hasMessages(MSG_START_OR_UPDATE_NOTIFICATION)) {
        msgHandler.sendEmptyMessage(MSG_START_OR_UPDATE_NOTIFICATION)
      }
    }
  }

  private fun makeHandler() = Handler(Looper.getMainLooper()) { msg -> handleMessage(msg) }

  private fun handleMessage(msg: Message): Boolean {
    return if (msg.what == MSG_START_OR_UPDATE_NOTIFICATION) {
      startOrUpdateNotification()
      true
    } else false
  }

  /**
   * Trouble canceling notification unless doing a [STATE_PAUSED] before [STATE_STOPPED]. This is
   * likely related to MediaPlayerService being considered a "foreground service'. Either we have to
   * pause before stopping foreground and canceling notification, or it's a timing issue.
   */
  private fun startOrUpdateNotification() {
    scope.launch {
      val controller: MediaControllerCompat = session.controller
      val playbackState: PlaybackStateCompat? = controller.playbackState
      val description: MediaDescriptionCompat? = controller.metadata?.description
      val sessionActivity = controller.sessionActivity
      if (playbackState?.state == STATE_STOPPED) {
        doStartOrUpdateNotification(STATE_PAUSED, description, sessionActivity)
      }
      doStartOrUpdateNotification(playbackState?.state, description, sessionActivity)
    }
  }

  private suspend fun doStartOrUpdateNotification(
    playbackState: Int?,
    description: MediaDescriptionCompat?,
    sessionActivity: PendingIntent
  ) {
    makeBuilder(playbackState, description, sessionActivity)?.let { builder ->
      val notification = builder.build()
      notificationManager.notify(NOW_PLAYING_NOTIFICATION_ID, notification)
      if (!isNotificationStarted) isNotificationStarted = true
      notificationListener.onPosted(NOW_PLAYING_NOTIFICATION_ID, notification)
    } ?: stopNotificationIfStarted()
  }

  private fun stopNotificationIfStarted() {
    if (isNotificationStarted) {
      isNotificationStarted = false
      msgHandler.removeMessages(MSG_START_OR_UPDATE_NOTIFICATION)
      cancelNotification()
    }
  }

  private fun cancelNotification() {
    notificationListener.stopForeground()
    notificationManager.cancel(NOW_PLAYING_NOTIFICATION_ID)
  }

  private suspend fun makeBuilder(
    state: Int?,
    description: MediaDescriptionCompat?,
    sessionActivity: PendingIntent
  ): NotificationBuilder? {
    if (state == null || state == STATE_STOPPED || description == null) {
      return null
    }

    return (makeNewBuilder(sessionActivity)).apply {
      val isPlaying = state == STATE_PLAYING
      setOngoing(isPlaying)

      setLargeIcon(description.iconBitmap)

      setDeleteIntent(buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_STOP))
      setContentTitle(description.title)
      setContentText(description.subtitle)
      setSubText(description.description)

      val visibleInCompactView = mutableListOf<Int>()
      var index = 0
      val prefs = notificationPrefs()
      index = addAction(
        makePreviousAction(),
        index,
        prefs.showPrevInNotification(),
        visibleInCompactView
      )
      index = addAction(makePlayPauseAction(isPlaying), index, true, visibleInCompactView)
      index = addAction(
        makeNextAction(),
        index,
        prefs.showNextInNotification(),
        visibleInCompactView
      )
      addAction(
        makeCloseAction(),
        index,
        prefs.showCloseInNotification(),
        visibleInCompactView
      )
      setMediaStyle(visibleInCompactView)
    }
  }

  private fun makeNewBuilder(intent: PendingIntent?) = NotificationBuilder(ctx, channelId).apply {
    setSmallIcon(R.drawable.ic_toque)
    setContentIntent(intent)
    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    setShowWhen(false)
    setCategory(NotificationCompat.CATEGORY_TRANSPORT)
    setAutoCancel(false)
    priority = NotificationCompat.PRIORITY_LOW
    color = NotificationCompat.COLOR_DEFAULT
  }
}

fun Context.isCarMode(): Boolean {
  val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
  return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
}

suspend fun Metadata.toCompat(
  getBitmap: suspend (Uri) -> Bitmap? = { null }
): MediaMetadataCompat = if (this === NullMetadata) MediaMetadataCompat.Builder().build() else {
  MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id.toCompatMediaId())
    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistName.value)
    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, albumArtist.value)
    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumTitle.value)
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.value)
    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.inWholeMilliseconds)
    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong())
    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, location.toString())
    .apply {
      /*
      Preferred order of artwork URI is:
        METADATA_KEY_DISPLAY_ICON_URI,
        METADATA_KEY_ART_URI,
        METADATA_KEY_ALBUM_ART_URI
     */
      val artwork = if (localAlbumArt !== Uri.EMPTY) localAlbumArt else albumArt
      putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getBitmap(artwork))
      if (artwork !== Uri.EMPTY) {
        putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artwork.toString())
      }
      putRating(MediaMetadataCompat.METADATA_KEY_RATING, rating.toStarRating().toCompat())
    }
    .build()
}

private fun List<AudioItem>.toCompat(): List<MediaSessionCompat.QueueItem> =
  ArrayList<MediaSessionCompat.QueueItem>(size).apply {
    this@toCompat.forEach { audioItem ->
      add(MediaSessionCompat.QueueItem(audioItem.toDescriptionCompat(), audioItem.instanceId.value))
    }
  }

private fun AudioItem.toDescriptionCompat() = MediaDescriptionCompat.Builder()
  .setMediaId(id.toCompatMediaId())
  .setTitle(title.value)
  .setSubtitle(artist.value)
  .setDescription(albumTitle.value)
  .setIconUri(if (localAlbumArt !== Uri.EMPTY) localAlbumArt else albumArt)
  .setMediaUri(location)
  .setExtras(
    Bundle().apply {
      putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.inWholeMilliseconds)
      putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong())
      if (albumArt !== Uri.EMPTY)
        putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, albumArt.toString())
      putParcelable(MediaMetadataCompat.METADATA_KEY_RATING, rating.toStarRating().toCompat())
    }
  )
  .build()

private typealias MediaSessionPrefsSingleton = PreferenceStoreSingleton<MediaSessionPrefs>
private typealias MetadataPref = PreferenceStore.Preference<String, Metadata>

private class MediaSessionPrefs(
  storage: Storage
) : BasePreferenceStore<MediaSessionPrefs>(storage) {
  private fun metadataPref(
    default: Metadata,
    customName: String? = null,
    sanitize: ((Metadata) -> Metadata)? = null
  ): MetadataPref = asTypePref(
    default,
    Metadata.Companion::fromJsonString,
    Metadata::toJsonString,
    customName,
    sanitize
  )

  val lastMetadata: StorePref<String, Metadata> by metadataPref(NullMetadata)
}
