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

package com.ealva.toque.service.vlc

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.StartPaused
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.ActiveEqPreset
import com.ealva.toque.service.audio.DuckedState
import com.ealva.toque.service.audio.MediaFileStore
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItemEvent
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayerEvent
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.NullAvPlayer
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.WakeLock
import com.ealva.toque.service.player.WakeLockFactory
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.session.common.Metadata
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
private val SKIP_RANGE = Millis(3)..Millis(10)

class VlcAudioItem(
  override var metadata: Metadata,
  private val displayName: String,
  override val albumId: AlbumId,
  override val artistSet: Set<ArtistName>,
  private val libVlc: LibVlc,
  private val mediaFileStore: MediaFileStore,
  private val activeEqPreset: ActiveEqPreset,
  private val appPrefs: AppPrefs,
  private val libVlcPrefs: LibVlcPrefs,
  private val wakeLockFactory: WakeLockFactory,
  private val requestFocus: AvPlayer.FocusRequest,
  private val duckedState: DuckedState,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : PlayableAudioItem {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private var hasBeenMarkedPlayed = false
  private var countTimeFrom = Millis(0)
  private var previousTimePlayed = Millis(0)

  private var avPlayer: AvPlayer = NullAvPlayer
  private var isShutdown = false
  private var isStopped = false
  private var isPreparing = false
  private var startOnPrepared = false
  private val wakeLock: WakeLock =
    wakeLockFactory.makeWakeLock(WAKE_LOCK_TIMEOUT, "$LOCK_TAG_PREFIX:${title()}")
  override val eventFlow = MutableSharedFlow<PlayableAudioItemEvent>(extraBufferCapacity = 10)

  override val isValid: Boolean
    get() = id.value > 0

  override val isPlaying: Boolean
    get() = (isPreparing && startOnPrepared) || (avPlayer.isValid && avPlayer.isPlaying)

  override val isPausable: Boolean
    get() = avPlayer.isPausable

  override val supportsFade: Boolean = true

  override val duration: Millis
    get() = metadata.duration

  override fun play(immediate: Boolean) {
    LOG._e { it("play isStopped=%s", isStopped) }
    if (isStopped)
      scope.launch { reset(immediateTransition = immediate, playNow = PlayNow(true)) }
    else
      avPlayer.play(immediate)
  }

  override fun stop() {
    if (!isStopped) avPlayer.stop()
  }

  override fun pause(immediate: Boolean) {
    avPlayer.pause(immediate)
  }

  override val isSeekable: Boolean
    get() = avPlayer.isValid && avPlayer.isSeekable

  override fun seekTo(position: Millis) {
    if (position in metadata.playbackRange) {
      if (isStopped)
        scope.launch { reset(immediateTransition = false, playNow = PlayNow(false)) }
      else
        avPlayer.seek(position)
    } else {
      LOG.e {
        it(
          "%d:%s attempt to seek to %d which is outside %s",
          metadata.id(),
          metadata.title(),
          position(),
          metadata.playbackRange
        )
      }
    }
  }

  private fun reset(immediateTransition: Boolean, playNow: PlayNow) {
    val shouldPlayNow = PlayNow(playNow() || isPlaying)
    val currentTime = position
    avPlayer.shutdown()
    avPlayer = NullAvPlayer
    prepareSeekMaybePlay(
      currentTime,
      if (immediateTransition) PlayImmediateTransition() else NoOpPlayerTransition,
      shouldPlayNow,
      previousTimePlayed,
      countTimeFrom
    )
  }

  override var position: Millis = Millis(0)

  override var volume: Volume
    get() = avPlayer.volume
    set(volume) {
      avPlayer.volume = volume
    }

  override var isMuted: Boolean
    get() = avPlayer.isMuted
    set(mute) {
      avPlayer.isMuted = mute
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
      hasBeenMarkedPlayed = false
    }
  }

  override fun duck() = avPlayer.duck()

  override fun endDuck() = avPlayer.endDuck()

  override fun shutdown(shutdownTransition: PlayerTransition) =
    avPlayer.transitionTo(shutdownTransition)

  override fun prepareSeekMaybePlay(
    startPosition: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    timePlayed: Millis,
    countFrom: Millis,
    startPaused: StartPaused
  ) {
    if (!isPreparing) {
      isPreparing = true
      scope.launch {
        // what I'm calling "position" the underlying VLC player calls "time".
        // For the VLC player, position is a percentage
        position = startPosition
        startOnPrepared = playNow()
        isShutdown = false
        isStopped = false
        previousTimePlayed = timePlayed
        countTimeFrom = countFrom
        avPlayer =
          libVlc.makeAudioMedia(fileUri, startPosition, startPaused, libVlcPrefs).use { media ->
            VlcPlayer.make(
              id,
              albumId,
              media,
              title,
              duration,
              activeEqPreset,
              onPreparedTransition,
              appPrefs,
              requestFocus,
              duckedState,
              wakeLock,
              dispatcher
            )
          }
        avPlayer.eventFlow
          .onSubscription { if (startPaused()) avPlayer.playStartPaused() }
          .onEach { event -> handleAvPlayerEvent(event) }
          .catch { cause -> LOG.e(cause) { it("Error processing MediaPlayerEvent") } }
          .onCompletion { LOG.i { it("MediaPlayer event flow completed") } }
          .launchIn(scope)
      }
    }
  }

  private suspend fun handleAvPlayerEvent(event: AvPlayerEvent) = when (event) {
    is AvPlayerEvent.Prepared -> onPrepared(event)
    is AvPlayerEvent.Start -> onStart(event)
    is AvPlayerEvent.PositionUpdate -> onPositionUpdate(event)
    is AvPlayerEvent.Paused -> onPaused(event)
    is AvPlayerEvent.Stopped -> onStopped(event)
    is AvPlayerEvent.PlaybackComplete -> onPlaybackComplete()
    is AvPlayerEvent.Error -> onError()
    is AvPlayerEvent.None -> {
    }
  }

  private suspend fun onPrepared(event: AvPlayerEvent.Prepared) {
    // Can get a 0 before we start actual playback, but we set this during prepareSeekMaybePlay
    if (position == Millis(0)) position = event.position
    // establish where we will start counting percentage played if not already set
    if (countTimeFrom == Millis(0)) countTimeFrom = event.position
    isPreparing = false
    if (event.duration > 0 && duration != event.duration) {
      metadata = metadata.copy(duration = event.duration)
      mediaFileStore.updateDurationAsync(id, duration)
    }
    eventFlow.emit(PlayableAudioItemEvent.Prepared(this, event.position, event.duration))
    if (startOnPrepared) play()
  }

  private suspend fun onStart(event: AvPlayerEvent.Start) {
    isPreparing = false
    eventFlow.emit(PlayableAudioItemEvent.Start(this, event.firstStart, position))
  }

  /**
   * If the total play time of media exceeds what the user specifies as the minimum percentage
   * required, or total time exceeds 4 minutes, the media is marked played.
   */
  private suspend fun onPositionUpdate(event: AvPlayerEvent.PositionUpdate) {
    if (!hasBeenMarkedPlayed) {
      if (event.position > countTimeFrom) {
        if (event.isPlaying) {
          val currentTimePlayed = event.position - countTimeFrom
          val total = previousTimePlayed + currentTimePlayed
          val percentagePlayed = total.asPercentageOf(event.duration)
          if (percentagePlayed > appPrefs.markPlayedPercentage() || total > Millis.FOUR_MINUTES) {
            mediaFileStore.incrementPlayedCountAsync(id)
            hasBeenMarkedPlayed = true
          }
        } else {
          // This is a user seek so establish a new count start time and save the previous total
          previousTimePlayed += position - countTimeFrom
          countTimeFrom = event.position
        }
      } else {
        // user seek backward, crude calc just to set previousTimePlayed to max of position then
        // establish new count start time
        previousTimePlayed = previousTimePlayed.coerceAtMost(event.position)
        countTimeFrom = event.position
      }
    }
    position = event.position
    eventFlow.emit(
      PlayableAudioItemEvent.PositionUpdate(
        this,
        event.position,
        event.duration,
        previousTimePlayed,
        countTimeFrom
      )
    )
  }

  private suspend fun onPaused(event: AvPlayerEvent.Paused) {
    isPreparing = false
    position = event.position
    eventFlow.emit(PlayableAudioItemEvent.Paused(this, event.position))
  }

  private suspend fun onStopped(event: AvPlayerEvent.Stopped) {
    isPreparing = false
    isStopped = true
    eventFlow.emit(PlayableAudioItemEvent.Stopped(this, event.position))
  }

  private suspend fun onPlaybackComplete() {
    isPreparing = false
    isShutdown = true
    avPlayer = NullAvPlayer
    eventFlow.emit(PlayableAudioItemEvent.PlaybackComplete(this))
  }

  private suspend fun onError() {
    isPreparing = false
    eventFlow.emit(PlayableAudioItemEvent.Error(this))
  }

  override fun cloneItem(): PlayableAudioItem = VlcAudioItem(
    metadata,
    displayName,
    albumId,
    artistSet,
    libVlc,
    mediaFileStore,
    activeEqPreset,
    appPrefs,
    libVlcPrefs,
    wakeLockFactory,
    requestFocus,
    duckedState,
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
      when (
        val result = mediaFileStore.setRating(
          id = mediaId,
          fileLocation = fileLocation,
          fileExt = fileExt,
          newRating = newRating,
          writeToFile = allowFileUpdate && appPrefs.saveRatingToFile(),
          beforeFileWrite = { if (wasPlaying) pause(immediate = true) }
        ) { if (wasPlaying) play(immediate = true) }
      ) {
        is Ok -> metadata = metadata.copy(rating = result.value)
        is Err -> LOG.e { it("Error setting rating. %s", result.error) }
      }
    }
  }

  override fun previousShouldRewind(): Boolean =
    appPrefs.rewindThenPrevious() && position >= SEEK_TO_ZERO_MIN_POSITION

  override val id: MediaId
    get() = metadata.id
  override val title: Title
    get() = metadata.title
  override val albumTitle: AlbumTitle
    get() = metadata.albumTitle
  override val albumArtist: ArtistName
    get() = metadata.albumArtist
  override val artist: ArtistName
    get() = metadata.artistName
  override val trackNumber: Int
    get() = metadata.trackNumber
  override val localAlbumArt: Uri
    get() = metadata.localAlbumArt
  override val albumArt: Uri
    get() = metadata.albumArt
  override val rating: Rating
    get() = metadata.rating
  override val location: Uri
    get() = metadata.location
  override val fileUri: Uri
    get() = metadata.fileUri

//  override fun getArtist(preferAlbumArtist: Boolean): ArtistName {
//    return if (preferAlbumArtist) {
//      fallbackIfEmptyOrUnknown(albumArtist) { joinToArtistName() }
//    } else {
//      fallbackIfEmptyOrUnknown(joinToArtistName()) { albumArtist }
//    }
//  }

//  private fun joinToArtistName(): ArtistName = ArtistName(artistSet.joinToString { it.value })
//
//private fun fallbackIfEmptyOrUnknown(artist: ArtistName, fallback: () -> ArtistName): ArtistName =
//    if (artist.value.isNotEmpty() && artist != ArtistName.UNKNOWN) artist else fallback()

  override val instanceId: Long = nextId.getAndIncrement()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VlcAudioItem) return false

    if (id != other.id) return false
    if (instanceId != other.instanceId) return false

    return true
  }

  override fun hashCode(): Int {
    return instanceId.hashCode()
  }
}

inline fun <T : IMedia, R> T.use(block: (T) -> R): R {
  try {
    return block(this)
  } finally {
    release()
  }
}
