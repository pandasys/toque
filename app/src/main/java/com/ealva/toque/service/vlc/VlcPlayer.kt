/*
 * Copyright 2020 eAlva.com
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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Micros
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.common.toMillis
import com.ealva.toque.log._w
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.MediaPlayerEvent
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.PlayerTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.player.WakeLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(VlcPlayer::class)

interface VlcPlayer : AvPlayer {

  companion object {
    operator fun invoke(
      media: IMedia,
      duration: Millis,
      eqPreset: VlcEqPreset,
      onPreparedTransition: PlayerTransition,
      prefs: AppPrefs,
      wakeLock: WakeLock,
      dispatcher: CoroutineDispatcher
    ): VlcPlayer = VlcPlayerImpl(
      media,
      duration,
      eqPreset,
      onPreparedTransition,
      prefs,
      wakeLock,
      dispatcher
    ).apply { VlcPlayerImpl.addToQueue(this) }
  }
}

private class VlcPlayerImpl(
  vlcMedia: IMedia,
  override var duration: Millis,
  private var eqPreset: VlcEqPreset,
  onPreparedTransition: PlayerTransition,
  private val prefs: AppPrefs,
  private val wakeLock: WakeLock,
  dispatcher: CoroutineDispatcher
) : VlcPlayer {
  private val scope = CoroutineScope(dispatcher + SupervisorJob())
  private val transitionPlayer = TheTransitionPlayer()
  private var transition: PlayerTransition = NoOpPlayerTransition
  private val onPrepared: PlayerTransition = getOnPrepared(onPreparedTransition)
  override var isShutdown = false
  private var shutdownOnPrepared = false
  private var prepared = false
  private var muted = false
  private var unmutedVolume = Volume.ZERO
  private var pausable = false
  private var seekable = false
  private var firstStart: Boolean = true
  private val mediaPlayer: MediaPlayer = vlcMedia.makePlayer()
  override val eventFlow = MutableSharedFlow<MediaPlayerEvent>()

  private val pauseTransition: PlayerTransition
    get() = if (prefs.playPauseFade()) {
      PauseFadeOutTransition(prefs.playPauseFadeLength())
    } else PauseImmediateTransition()

  private val playTransition: PlayerTransition
    get() = if (prefs.playPauseFade()) {
      FadeInTransition(prefs.playPauseFadeLength())
    } else PlayImmediateTransition()

  override val isValid: Boolean
    get() = prepared && !isShutdown

  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {
    if (newEq is VlcEqPreset) {
      if (isValid && (applyEdits || eqPreset != newEq)) {
        setPlayerPreset(newEq)
      }
    } else LOG.e { it("Preset %s is not a VlcEqPreset", newEq) }
  }

  private fun setPlayerPreset(preset: VlcEqPreset): Boolean {
    if (isShutdown) return false
    if (preset.isNullPreset) {
      mediaPlayer.setEqualizer(null)
    } else {
      preset.setPreset(mediaPlayer)
    }
    eqPreset = preset
    scope.launch {
//    notify new EqPreset active
    }
    return true
  }

  override val equalizer: EqPreset
    get() = eqPreset
  override val isPausable: Boolean
    get() = pausable

  override fun pause(immediate: Boolean) {
    if (isValid) startTransition(getPauseTransition { immediate }, false)
  }

  private inline fun getPauseTransition(immediate: () -> Boolean) =
    if (immediate()) PauseImmediateTransition() else pauseTransition

  override fun play(immediate: Boolean) {
    if (isValid) startTransition(getPlayTransition { immediate }, false)
  }

  private fun getPlayTransition(immediate: () -> Boolean) =
    if (immediate()) PlayImmediateTransition() else playTransition

  override fun shutdown() {
    if (!isShutdown) {
      transition.setCancelled()
      isShutdown = true
      if (prepared) {
        secondStageShutdown()
      } else {
        shutdownOnPrepared = true
      }
    }
  }

  private fun secondStageShutdown() {
    removeFromQueue(this)
    if (!mediaPlayer.isReleased) {
      mediaPlayer.release()
    }
    wakeLock.release()
  }

  override val isSeekable: Boolean
    get() = seekable

  override fun seek(position: Millis) {
    var notifyPosition = false
    if (isValid) {
      if (mediaPlayer.isSeekable) {
        mediaPlayer.time = position.value
        // when the player is stopped it doesn't send position notifications
        notifyPosition = !mediaPlayer.isPlaying
      }
    }
    if (notifyPosition) {
      notifyTimeChangedImmediately(position, duration)
    }
  }

  override val time: Millis
    get() = Millis(mediaPlayer.time)

  override val isPlaying: Boolean
    get() = if (isPrepared) transition.isPlaying else onPrepared.isPlaying

  override val isPaused: Boolean
    get() = if (isPrepared) transition.isPaused else onPrepared.isPaused

  private var realVolume = Volume.ZERO
  override var volume: Volume
    get() = realVolume
    set(value) {
      realVolume = value.coerceIn(AvPlayer.DEFAULT_VOLUME_RANGE)
      muted = false
      unmutedVolume = realVolume // MediaPlayer get volume not working
      if (isValid) {
        mediaPlayer.volume = realVolume.value
      }
    }

  override var isMuted: Boolean
    get() = muted
    set(mute) {
      if (isValid) {
        muted = mute
        if (mute) mediaPlayer.volume = 0 else mediaPlayer.volume = unmutedVolume.value
      }
    }

  override val isVideoPlayer: Boolean
    get() = isValid && mediaPlayer.videoTracksCount > 0
  override val isAudioPlayer: Boolean
    get() = isValid && !isVideoPlayer && mediaPlayer.audioTracksCount > 0

  override fun stop() {
    if (isValid) {
      mediaPlayer.stop()
    }
  }

  override val isPrepared: Boolean
    get() = prepared
  override var audioDelay: Micros
    get() = Micros(mediaPlayer.audioDelay)
    set(delay) {
      mediaPlayer.audioDelay = delay.value
    }
  override var spuDelay: Micros
    get() = Micros(mediaPlayer.spuDelay)
    set(delay) {
      mediaPlayer.spuDelay = delay.value
    }

  override fun transitionTo(transition: PlayerTransition) {
    startTransition(transition, false)
  }

  override var playbackRate: PlaybackRate
    get() = PlaybackRate(mediaPlayer.rate).coerceIn(PlaybackRate.RANGE)
    set(rate) {
      mediaPlayer.rate = rate.coerceIn(PlaybackRate.RANGE).value
    }

  private fun emit(event: MediaPlayerEvent) {
    scope.launch { eventFlow.emit(event) }
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
    when (event.type) {
      MediaPlayer.Event.Opening -> {
        mediaPlayer.volume = realVolume.value
      }
      MediaPlayer.Event.Buffering -> if (!prepared && event.bufferedEnoughForPrepare()) {
        prepared = true
        startTransition(onPrepared, notifyPrepared = true)
      }
      MediaPlayer.Event.Playing -> if (prepared) wakeLock.acquire()
      MediaPlayer.Event.Paused -> if (prepared) wakeLock.release()
      MediaPlayer.Event.Stopped -> {
        wakeLock.release()
        emit(MediaPlayerEvent.Stopped)
      }
      MediaPlayer.Event.EndReached -> {
        shutdown()
        emit(MediaPlayerEvent.PlaybackComplete)
      }
      MediaPlayer.Event.EncounteredError -> {
        prepared = false
        shutdown()
        emit(MediaPlayerEvent.Error)
      }
      MediaPlayer.Event.TimeChanged -> {
        notifyTimeChangedImmediately(event.timeChanged.toMillis(), duration)
      }
      MediaPlayer.Event.PausableChanged -> {
        pausable = event.pausable
        val reportedLength = mediaPlayer.length
        if (reportedLength > 0) {
          duration = reportedLength.toMillis()
        }
      }
      MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
    }
  }

  private fun MediaPlayer.Event.bufferedEnoughForPrepare() =
    buffering > BUFFERING_PERCENT_TRIGGER_PREPARED

  private fun notifyTimeChangedImmediately(time: Millis, duration: Millis) =
    emit(MediaPlayerEvent.PositionUpdate(time, duration))

  private fun getOnPrepared(transition: PlayerTransition) = transition.apply {
    setPlayer(transitionPlayer)
  }

  private fun IMedia.makePlayer() = MediaPlayer(this).apply {
    setEventListener(makeEventListener())
  }

  private fun startTransition(newTransition: PlayerTransition, notifyPrepared: Boolean = false) {
    if (!isShutdown && transition.accept(newTransition)) {
      transition.setCancelled()
      newTransition.setPlayer(transitionPlayer)
      transition = newTransition
      if (notifyPrepared) emit(MediaPlayerEvent.Prepared(mediaPlayer.time.toMillis(), duration))
      scope.launch { newTransition.execute() }
    }
  }

  private inner class TheTransitionPlayer : TransitionPlayer {
    override val isPaused: Boolean
      get() = isValid && mediaPlayer.isPlaying

    override val isPlaying: Boolean
      get() = isValid && mediaPlayer.isPlaying

    override val isShutdown: Boolean
      get() = this@VlcPlayerImpl.isShutdown

    override fun play() {
      if (isValid) {
        mediaPlayer.play()
      }
    }

    override fun pause() {
      if (isValid) {
        mediaPlayer.pause()
      }
    }

    override var volume: Volume
      get() = this@VlcPlayerImpl.volume
      set(value) {
        this@VlcPlayerImpl.volume = value
      }

    override val volumeRange: VolumeRange
      get() = AvPlayer.DEFAULT_VOLUME_RANGE

    override val remainingTime: Millis
      get() = if (isValid) duration - mediaPlayer.time else Millis.ZERO

    override fun notifyPlaying() {
      emit(MediaPlayerEvent.Start(firstStart))
      firstStart = false
    }

    override fun notifyPaused() = emit(MediaPlayerEvent.Paused(time))

    override fun shutdown() = this@VlcPlayerImpl.shutdown()

    override fun shouldContinue(): Boolean = !this@VlcPlayerImpl.isShutdown &&
      duration > 0 &&
      duration - mediaPlayer.time > AvPlayer.DURATION_OFFSET_CONSIDERED_END
  }

  companion object {
    private val fifoPlayerQueue = ArrayDeque<VlcPlayer>(3)
    private const val BUFFERING_PERCENT_TRIGGER_PREPARED = 90.0
    private val queueLock = ReentrantLock(true)

    fun addToQueue(player: VlcPlayer) = queueLock.withLock {
      // We can have at most 2 active players in the queue.
      while (fifoPlayerQueue.size > 1) {
        fifoPlayerQueue.removeLast().shutdown()
      }
      fifoPlayerQueue.addFirst(player)
    }

    private fun removeFromQueue(player: VlcPlayer) = queueLock.withLock {
      fifoPlayerQueue.remove(player)
      while (fifoPlayerQueue.size > 1) {
        fifoPlayerQueue.removeLast().shutdown()
        LOG._w { it("removeFromQueue queue size > 1") }
      }
    }
  }
}

object NullVlcPlayer : VlcPlayer {
  override val eventFlow: Flow<MediaPlayerEvent> = emptyFlow()
  override val isValid: Boolean = false
  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {}
  override val equalizer: EqPreset = EqPreset.NULL
  override val isPausable: Boolean = false
  override fun pause(immediate: Boolean) {}
  override fun play(immediate: Boolean) {}
  override fun shutdown() {}
  override val duration: Millis = Millis.ZERO
  override val isSeekable: Boolean = false
  override fun seek(position: Millis) {}
  override val time: Millis = Millis.ZERO
  override val isPlaying: Boolean = false
  override val isPaused: Boolean = false
  override val isShutdown: Boolean = true
  override var volume: Volume = Volume.ONE_HUNDRED
  override var isMuted: Boolean = false
  override val isVideoPlayer: Boolean = false
  override val isAudioPlayer: Boolean = true
  override fun stop() {}
  override val isPrepared: Boolean = false
  override var audioDelay: Micros = Micros(0)
  override var spuDelay: Micros = Micros(0)
  override fun transitionTo(transition: PlayerTransition) {}
  override var playbackRate: PlaybackRate = PlaybackRate.NORMAL
}
