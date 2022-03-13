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

package com.ealva.toque.service.vlc

import android.net.Uri
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.debug
import com.ealva.toque.flow.CountDownFlow
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.isValid
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.MediaFileStore
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.PlayableItemEvent
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.audio.SharedPlayerState
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayerEvent
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.NullAvPlayer
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.WakeLock
import com.ealva.toque.service.player.WakeLockFactory
import com.ealva.toque.service.queue.MayFade
import com.ealva.toque.service.queue.MayFade.AllowFade
import com.ealva.toque.service.queue.MayFade.NoFade
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.common.Metadata
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val LOG by lazyLogger(VlcAudioItem::class)
private val nextId = AtomicLong(1)

private const val WAKE_LOCK_TIMEOUT_MINUTES = 10L
private val WAKE_LOCK_TIMEOUT = Millis(TimeUnit.MINUTES.toMillis(WAKE_LOCK_TIMEOUT_MINUTES))
private const val LOCK_TAG_PREFIX = "toque:VlcAudioItem"

/**
 * When user selects previous, if position is > than this value, seek to 0, else go to previous song
 */
private const val SEEK_TO_ZERO_MIN_POSITION = 5000

/** If position is within this range when checked for skip, item should be marked skipped */
private val SKIP_RANGE: ClosedRange<Millis> = Millis.THREE_SECONDS..Millis.TEN_SECONDS

class VlcAudioItem private constructor(
  override var metadata: Metadata,
  private val displayName: String,
  override val albumId: AlbumId,
  private val artistSet: Set<ArtistName>,
  private val libVlcSingleton: LibVlcSingleton,
  private val mediaFileStore: MediaFileStore,
  private val sharedPlayerState: SharedPlayerState,
  private val appPrefs: AppPrefs,
  private val libVlcPrefs: LibVlcPrefs,
  private val wakeLockFactory: WakeLockFactory,
  private val requestFocus: AvPlayer.FocusRequest,
  private val dispatcher: CoroutineDispatcher
) : PlayableAudioItem {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private var countDownJob: Job? = null
  private var remainingToMarkPlayed =
    (metadata.duration * appPrefs.markPlayedPercentage())
      .coerceAtMost(4.toDuration(DurationUnit.MINUTES))
  private var hasBeenMarkedPlayed = false

  private var avPlayer: AvPlayer = NullAvPlayer
  private var isShutdown = false
  private var isStopped = false
  private var isPreparing = false
  private var startOnPrepared = false
  private val wakeLock: WakeLock =
    wakeLockFactory.makeWakeLock(WAKE_LOCK_TIMEOUT, "$LOCK_TAG_PREFIX:${title()}")
  override val eventFlow = MutableSharedFlow<PlayableItemEvent>(extraBufferCapacity = 10)

  override val isValid: Boolean
    get() = id.isValid

  override val isPlaying: Boolean
    get() = (isPreparing && startOnPrepared) || (avPlayer.isValid && avPlayer.isPlaying)

  override val isPausable: Boolean
    get() = avPlayer.isPausable

  override val supportsFade: Boolean = true

  override var position: Millis = Millis(0)

  override val duration: Duration
    get() = metadata.duration

  private fun shouldBeReset(): Boolean = isStopped || avPlayer == NullAvPlayer

  override fun play(mayFade: MayFade) {
    if (shouldBeReset())
      scope.launch { reset(PlayNow(true)) }
    else
      avPlayer.play(mayFade)
  }

  override fun stop() {
    if (!isStopped) avPlayer.stop()
  }

  override fun pause(mayFade: MayFade) {
    if (!isStopped) avPlayer.pause(mayFade)
  }

  /**
   * If stopped or paused, play, else pause. Call this object's play or pause method to keep
   * the AvPlayer reset/play and pause logic in one place.
   *
   * Note: use [shouldBeReset] instead of just [isStopped]
   */
  override fun togglePlayPause() {
    if (shouldBeReset() || avPlayer.isPaused) play(AllowFade) else pause(AllowFade)
  }

  override fun seekTo(position: Millis) {
    if (position in metadata.playbackRange) {
      if (shouldBeReset())
        scope.launch { reset(PlayNow(false)) }
      else
        avPlayer.seek(position)
    } else {
      val id = metadata.id()
      val title = metadata.title()
      val pos = position()
      val range = metadata.playbackRange
      LOG.e { it("%d:%s attempt to seek to %d which is outside %s", id, title, pos, range) }
      debug {
        throw IllegalArgumentException("$id:$title attempt to seek to $pos which is outside $range")
      }
      avPlayer.seek(position.coerceIn(metadata.playbackRange)) // fix position
    }
  }

  /**
   * We could get blasted with resets if there was a "stop" and then we get prepare/play requests
   * originating from MediaSession/user/... Check [isPreparing] to prevent killing a player we're
   * trying to start.
   */
  override fun reset(playNow: PlayNow) {
    if (!isPreparing) {
      val shouldPlayNow = PlayNow(playNow.value || isPlaying)
      val currentTime = position
      avPlayer.shutdown()
      avPlayer = NullAvPlayer
      prepareSeekMaybePlay(
        currentTime,
        if (playNow.value) PlayImmediateTransition() else NoOpPlayerTransition,
        shouldPlayNow
      )
    }
  }

  override fun updateArtwork(
    albumId: AlbumId,
    albumArt: Uri,
    localAlbumArt: Uri
  ) {
    if (this.albumId == albumId) {
      metadata = metadata.copy(albumArt = albumArt, localAlbumArt = localAlbumArt)
    }
  }

  override var playbackRate: PlaybackRate
    get() = avPlayer.playbackRate
    set(rate) {
      avPlayer.playbackRate = rate
    }

  override fun shutdown() {
    if (!isShutdown) {
      avPlayer.stop()
      isShutdown = true
      avPlayer = NullAvPlayer
      avPlayer.shutdown()
    }
  }

  override fun duck() = avPlayer.duck()

  override fun endDuck() = avPlayer.endDuck()

  override fun shutdown(shutdownTransition: PlayerTransition) =
    avPlayer.transitionTo(shutdownTransition)

  /**
   * If [startPosition] is past the point where the media would be marked as "played", go ahead
   * and set the internal flag [hasBeenMarkedPlayed] to true so that shutting down and restarting
   * doesn't give a duplicate "played"
   *
   * It is possible to reenter this function while media is still being played. eg. if the user goes
   * to the next media and a fade transition starts, then they return to this media, the player will
   * still be playing the fade out. This function handles that scenario. When the new VlcPlayer is
   * created, it will add itself to an internal fifo queue which only allows 2 players active at one
   * time. This means we can just abandon the current [avPlayer] and overwrite it with a new player.
   */
  override fun prepareSeekMaybePlay(
    startPosition: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused
  ) {
    if (!isPreparing) {
      isPreparing = true
      scope.launch {
        // what I'm calling "position" the underlying VLC player calls "time".
        // For the VLC player, position is a percentage
        position = startPosition
        remainingToMarkPlayed -= position.toDuration()
        hasBeenMarkedPlayed = remainingToMarkPlayed.isNegative() || remainingToMarkPlayed.isZero
        startOnPrepared = playNow.value
        isShutdown = false
        isStopped = false
        avPlayer = libVlcSingleton.withInstance { libVlc ->
          libVlc
            .makeAudioMedia(fileUri, startPosition, startPaused, libVlcPrefs)
            .use { media ->
              VlcPlayer(
                id,
                albumId,
                media,
                title,
                duration,
                sharedPlayerState,
                onPreparedTransition,
                appPrefs,
                requestFocus,
                wakeLock,
                dispatcher
              )
            }
        }.also { player ->
          player.eventFlow
            .onSubscription {
              if (startPaused.value) player.playStartPaused() else isPreparing = false
            }
            .onEach { event -> handleAvPlayerEvent(event) }
            .catch { cause -> LOG.e(cause) { it("Error processing MediaPlayerEvent") } }
            .onCompletion { LOG.i { it("MediaPlayer event flow completed") } }
            .launchIn(scope)
        }
      }
    }
  }

  private suspend fun handleAvPlayerEvent(event: AvPlayerEvent) {
    when (event) {
      is AvPlayerEvent.Prepared -> onPrepared(event)
      is AvPlayerEvent.Start -> onStart(event)
      is AvPlayerEvent.PositionUpdate -> onPositionUpdate(event)
      is AvPlayerEvent.Paused -> onPaused(event)
      is AvPlayerEvent.Stopped -> onStopped()
      is AvPlayerEvent.PlaybackComplete -> onPlaybackComplete()
      is AvPlayerEvent.Error -> onError()
      is AvPlayerEvent.None -> {
      }
    }
  }

  private suspend fun onPrepared(event: AvPlayerEvent.Prepared) {
    // Can get a 0 before we start actual playback, but we set this during prepareSeekMaybePlay
//    if (position != Millis(0)) position = event.position
    isPreparing = false
    if (event.duration > 0 && duration != event.duration.toDuration()) {
      metadata = metadata.copy(duration = event.duration.toDuration())
      mediaFileStore.updateDurationAsync(id, duration)
    }
    eventFlow.emit(PlayableItemEvent.Prepared(this, position, event.duration))
    if (startOnPrepared) play(AllowFade)
  }

  private suspend fun onStart(event: AvPlayerEvent.Start) {
    isPreparing = false
    startMarkPlayedCountDown()
    eventFlow.emit(PlayableItemEvent.Start(this, event.firstStart, position))
  }

  private fun startMarkPlayedCountDown() {
    if (!hasBeenMarkedPlayed) {
      countDownJob = CountDownFlow(remainingToMarkPlayed, 1.seconds)
        .onEach { remaining -> remainingToMarkPlayed = remaining }
        .catch { cause -> LOG.e(cause) { it("Error in mark played count down") } }
        .onCompletion { cause -> if (cause == null) markPlayed() }
        .launchIn(scope)
    }
  }

  private fun cancelMarkPlayedCountDown() {
    countDownJob?.cancel()
  }

  private fun markPlayed() {
    hasBeenMarkedPlayed = true
    mediaFileStore.incrementPlayedCountAsync(id)
  }

  /**
   * If the total play time of media exceeds what the user specifies as the minimum percentage
   * required, or total time exceeds 4 minutes, the media is marked played.
   */
  private suspend fun onPositionUpdate(event: AvPlayerEvent.PositionUpdate) {
    position = event.position
    eventFlow.emit(PlayableItemEvent.PositionUpdate(this, event.position, event.duration))
  }

  private suspend fun onPaused(event: AvPlayerEvent.Paused) {
    cancelMarkPlayedCountDown()
    isPreparing = false
    position = event.position
    eventFlow.emit(PlayableItemEvent.Paused(this, event.position))
  }

  private suspend fun onStopped() {
    cancelMarkPlayedCountDown()
    isPreparing = false
    isStopped = true
    eventFlow.emit(PlayableItemEvent.Stopped(this))
  }

  private suspend fun onPlaybackComplete() {
    cancelMarkPlayedCountDown()
    isPreparing = false
    isShutdown = true
    avPlayer = NullAvPlayer
    eventFlow.emit(PlayableItemEvent.PlaybackComplete(this))
  }

  private suspend fun onError() {
    cancelMarkPlayedCountDown()
    isPreparing = false
    eventFlow.emit(PlayableItemEvent.Error(this))
  }

  override fun cloneItem(): PlayableAudioItem = VlcAudioItem(
    metadata,
    displayName,
    albumId,
    artistSet,
    libVlcSingleton,
    mediaFileStore,
    sharedPlayerState,
    appPrefs,
    libVlcPrefs,
    wakeLockFactory,
    requestFocus,
    dispatcher
  )

  override fun checkMarkSkipped() {
    if (position in SKIP_RANGE) mediaFileStore.incrementSkippedCountAsync(id)
  }

  override fun setRating(newRating: Rating, allowFileUpdate: Boolean) {
    val wasPlaying = isPlaying
    val mediaId = id
    val fileLocation = metadata.location
    val fileExt = displayName.substringAfterLast('.', "")
    scope.launch {
      mediaFileStore.setRating(
        id = mediaId,
        fileLocation = fileLocation,
        fileExt = fileExt,
        newRating = newRating,
        writeToFile = allowFileUpdate && appPrefs.saveRatingToFile(),
        beforeFileWrite = { if (wasPlaying) pause(NoFade) }
      ) {
        if (wasPlaying) play(NoFade)
      }.onFailure { cause -> LOG.e(cause) { it("Error setting rating") } }
        .onSuccess { rating -> metadata = metadata.copy(rating = rating) }
    }
  }

  override fun previousShouldRewind(): Boolean =
    appPrefs.rewindThenPrevious() && position >= SEEK_TO_ZERO_MIN_POSITION

  override val instanceId = InstanceId(nextId.getAndIncrement())

  override fun toString(): String = """VlcAudioItem[isActive=${scope.isActive},
    |isShutdown=${isShutdown},
    |title=${metadata.title},
    |avPlayer=${avPlayer}
    |]""".trimMargin()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VlcAudioItem) return false

    if (metadata != other.metadata) return false
    if (instanceId != other.instanceId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = metadata.hashCode()
    result = 31 * result + instanceId.hashCode()
    return result
  }

  companion object {
    operator fun invoke(
      metadata: Metadata,
      displayName: String,
      albumId: AlbumId,
      artistSet: Set<ArtistName>,
      libVlcSingleton: LibVlcSingleton,
      mediaFileStore: MediaFileStore,
      sharedPlayerState: SharedPlayerState,
      appPrefs: AppPrefs,
      libVlcPrefs: LibVlcPrefs,
      wakeLockFactory: WakeLockFactory,
      requestFocus: AvPlayer.FocusRequest,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): PlayableAudioItem =
      VlcAudioItem(
        metadata,
        displayName,
        albumId,
        artistSet,
        libVlcSingleton,
        mediaFileStore,
        sharedPlayerState,
        appPrefs,
        libVlcPrefs,
        wakeLockFactory,
        requestFocus,
        dispatcher
      )
  }
}

inline fun <T : IMedia, R> T.use(block: (T) -> R): R {
  try {
    return block(this)
  } finally {
    release()
  }
}

private inline val Duration.isZero: Boolean get() = this == Duration.ZERO
