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
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.EqPresetSelector
import com.ealva.toque.service.audio.MediaFileStore
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItemEvent
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.MediaPlayerEvent
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.NullAvPlayer
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.WakeLock
import com.ealva.toque.service.player.WakeLockFactory
import com.ealva.toque.service.queue.PlayNow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val LOG by lazyLogger(VlcAudioItem::class)
private val nextId = AtomicLong(1)

private const val WAKE_LOCK_TIMEOUT_MINUTES = 10L
private val WAKE_LOCK_TIMEOUT = Millis(TimeUnit.MINUTES.toMillis(WAKE_LOCK_TIMEOUT_MINUTES))
private const val LOCK_TAG_PREFIX = "toque:VlcAudioItem"
private const val MAX_PERCENTAGE = 100.0

/** If position is within this range when checked for skip, item should be marked skipped */
@Suppress("MagicNumber")
private val SKIP_RANGE = Millis(3)..Millis(10)

class VlcAudioItem(
  private val libVlc: LibVlc,
  override val id: MediaId,
  override val location: Uri,
  override val title: Title,
  override val albumTitle: AlbumTitle,
  override val albumId: AlbumId,
  override val albumArtist: ArtistName,
  override val artist: ArtistName,
  override val artistSet: Set<ArtistName>,
  override var rating: Rating,
  override var duration: Millis,
  override val trackNumber: Int,
  override val localAlbumArt: Uri,
  override val albumArt: Uri,
  private val mediaFileStore: MediaFileStore,
  private val eqPresetSelector: EqPresetSelector,
  private val appPrefs: AppPrefs,
  private val libVlcPrefs: LibVlcPrefs,
  private val wakeLockFactory: WakeLockFactory,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : PlayableAudioItem {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var hasBeenMarkedPlayed = false
  private var mediaPlayer: AvPlayer = NullAvPlayer
  private var isShutdown = false
  private var isStopped = false
  private var isPreparing = false
  private var startOnPrepared = false
  private var startPosition = Millis.ZERO
  private var percentagePlayed = 0
  private val wakeLock: WakeLock =
    wakeLockFactory.makeWakeLock(WAKE_LOCK_TIMEOUT, "$LOCK_TAG_PREFIX:${title()}")
  override val eventFlow = MutableSharedFlow<PlayableAudioItemEvent>(extraBufferCapacity = 10)

  override val isValid: Boolean
    get() = id.value > 0

  override val isPlaying: Boolean
    get() = (isPreparing && startOnPrepared) || (mediaPlayer.isValid && mediaPlayer.isPlaying)

  override val isPausable: Boolean
    get() = mediaPlayer.isPausable

  override val supportsFade: Boolean = true

  override suspend fun play(immediate: Boolean) {
    if (isStopped) reset(
      eqPresetSelector,
      PlayNow(immediate),
      position
    ) else mediaPlayer.play(immediate)
  }

  override fun stop() {
    if (!isStopped) mediaPlayer.stop()
  }

  override fun pause(immediate: Boolean) {
    if (!isStopped && mediaPlayer.isPrepared) mediaPlayer.pause(immediate)
  }

  override val isSeekable: Boolean
    get() = mediaPlayer.isValid && mediaPlayer.isSeekable

  override suspend fun seekTo(position: Millis) {
    if (isStopped) reset(
      eqPresetSelector,
      PlayNow(isPlaying),
      position
    ) else mediaPlayer.seek(position)
  }

  private var _position = Millis.ZERO
  override val position: Millis
    get() = _position

  override var volume: Volume
    get() = mediaPlayer.volume
    set(volume) {
      mediaPlayer.volume = volume
    }

  override var isMuted: Boolean
    get() = mediaPlayer.isMuted
    set(mute) {
      mediaPlayer.isMuted = mute
    }

  override var equalizer: EqPreset
    get() = mediaPlayer.equalizer
    set(eqPreset) {
      mediaPlayer.setEqualizer(eqPreset, true)
    }

  override var playbackRate: PlaybackRate
    get() = mediaPlayer.playbackRate
    set(rate) {
      mediaPlayer.playbackRate = rate
    }

  override fun shutdown() {
    if (!isShutdown) {
      mediaPlayer.stop()
      isShutdown = true
      mediaPlayer = NullAvPlayer
      mediaPlayer.shutdown()
      hasBeenMarkedPlayed = false
    }
  }

  override fun shutdown(shutdownTransition: PlayerTransition) {
    mediaPlayer.transitionTo(shutdownTransition)
  }

  override suspend fun reset(presetSelector: EqPresetSelector, playNow: PlayNow, position: Millis) {
    val shouldPlayNow = PlayNow(playNow() || isPlaying)
    val currentTime = position
    mediaPlayer.shutdown()
    mediaPlayer = NullAvPlayer
    prepareSeekMaybePlay(
      currentTime,
      if (shouldPlayNow()) PlayImmediateTransition() else NoOpPlayerTransition,
      shouldPlayNow,
    )
  }

  override suspend fun prepareSeekMaybePlay(
    position: Millis,
    onPreparedTransition: PlayerTransition,
    playNow: PlayNow,
    startPaused: StartPaused
  ) {
    isPreparing = true
    // what I'm calling "position" the underlying VLC player calls "time".
    // For the VLC player, position is a percentage
    startPosition = position
    _position = position
    startOnPrepared = playNow()
    isShutdown = false
    isStopped = false
    val media = libVlc.makeAudioMedia(location, position, startPaused, libVlcPrefs)
    mediaPlayer = VlcPlayer(
      media,
      title,
      duration,
      eqPresetSelector.getPreferredEqPreset(id, albumId) as VlcEqPreset,
      onPreparedTransition,
      appPrefs,
      wakeLock,
      dispatcher
    )
    media.release()
    scope.launch {
      mediaPlayer.eventFlow
        .onSubscription { if (startPaused()) mediaPlayer.playStartPaused() }
        .onEach { event -> handleMediaPlayerEvent(event) }
        .catch { cause -> LOG.e(cause) { it("Error processing MediaPlayerEvent") } }
        .onCompletion { LOG.i { it("MediaPlayer event flow completed") } }
        .collect()
    }
  }

  private suspend fun handleMediaPlayerEvent(event: MediaPlayerEvent) {
    when (event) {
      is MediaPlayerEvent.Prepared -> onPrepared(event.currentPosition, event.duration)
      is MediaPlayerEvent.Start -> onStart(event)
      is MediaPlayerEvent.PositionUpdate -> onPositionUpdate(event)
      is MediaPlayerEvent.Paused -> onPaused(event)
      is MediaPlayerEvent.Stopped -> onStopped(event)
      is MediaPlayerEvent.PlaybackComplete -> onPlaybackComplete()
      is MediaPlayerEvent.Error -> onError()
      is MediaPlayerEvent.None -> {
      }
    }
  }

  private suspend fun onPrepared(currentPosition: Millis, reportedDuration: Millis) {
    _position = currentPosition
    isPreparing = false
    if (reportedDuration > 0 && duration != reportedDuration) {
      duration = reportedDuration
      mediaFileStore.updateDurationAsync(id, duration)
    }
    eventFlow.emit(PlayableAudioItemEvent.Prepared(this, currentPosition, reportedDuration))
    if (startOnPrepared) play()
  }

  private suspend fun onStart(event: MediaPlayerEvent.Start) {
    isPreparing = false
    eventFlow.emit(PlayableAudioItemEvent.Start(this, event.firstStart, position))
  }

  private suspend fun onPositionUpdate(event: MediaPlayerEvent.PositionUpdate) {
//    LOG._e { it("positionUpdate isPlaying=%s", event.isPlaying) }
    val curPos = event.currentPosition
    _position = curPos
    val millisPlayed = curPos - startPosition
    // TODO percentage played is incorrect
    percentagePlayed = (millisPlayed.toDouble() / duration.toDouble() * MAX_PERCENTAGE).toInt()
    if (!hasBeenMarkedPlayed && percentagePlayed > appPrefs.markPlayedPercentage()) {
      hasBeenMarkedPlayed = true
      mediaFileStore.incrementPlayedCountAsync(id)
    }
    eventFlow.emit(
      PlayableAudioItemEvent.PositionUpdate(
        this,
        event.currentPosition,
        event.duration
      )
    )
  }

  private suspend fun onPaused(event: MediaPlayerEvent.Paused) {
    isPreparing = false
    _position = event.position
    eventFlow.emit(PlayableAudioItemEvent.Paused(this, event.position))
  }

  private suspend fun onStopped(event: MediaPlayerEvent.Stopped) {
    isPreparing = false
    isStopped = true
    eventFlow.emit(PlayableAudioItemEvent.Stopped(this, event.position))
  }

  private suspend fun onPlaybackComplete() {
    isPreparing = false
    isShutdown = true
    mediaPlayer = NullAvPlayer
    eventFlow.emit(PlayableAudioItemEvent.PlaybackComplete(this))
  }

  private suspend fun onError() {
    eventFlow.emit(PlayableAudioItemEvent.Error(this))
  }

  override fun cloneItem(): PlayableAudioItem = VlcAudioItem(
    libVlc,
    id,
    location,
    title,
    albumTitle,
    albumId,
    albumArtist,
    artist,
    artistSet,
    rating,
    duration,
    trackNumber,
    localAlbumArt,
    albumArt,
    mediaFileStore,
    eqPresetSelector,
    appPrefs,
    libVlcPrefs,
    wakeLockFactory,
    dispatcher
  )

  override suspend fun applyEqualization(eqPresetSelector: EqPresetSelector, applyEdits: Boolean) {
    val preset = eqPresetSelector.getPreferredEqPreset(id, albumId)
    mediaPlayer.setEqualizer(preset, applyEdits)
  }

  override fun checkMarkSkipped() {
    if (position in SKIP_RANGE) {
      mediaFileStore.incrementSkippedCountAsync(id)
    }
  }

  override suspend fun setRating(newRating: Rating) {
    rating = mediaFileStore.setRating(id, newRating)
  }

//  override fun getArtist(preferAlbumArtist: Boolean): ArtistName {
//    return if (preferAlbumArtist) {
//      fallbackIfEmptyOrUnknown(albumArtist) { joinToArtistName() }
//    } else {
//      fallbackIfEmptyOrUnknown(joinToArtistName()) { albumArtist }
//    }
//  }

//  private fun joinToArtistName(): ArtistName = ArtistName(artistSet.joinToString { it.value })
//
//  private fun fallbackIfEmptyOrUnknown(artist: ArtistName, fallback: () -> ArtistName): ArtistName =
//    if (artist.value.isNotEmpty() && artist != ArtistName.UNKNOWN) artist else fallback()

  override val instanceId: Long = nextId.getAndIncrement()
}
