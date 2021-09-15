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
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.log._e
import com.ealva.toque.log._w
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.MediaPlayerEvent
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.player.WakeLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
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
      title: Title,
      duration: Millis,
      eqPreset: VlcEqPreset,
      onPreparedTransition: PlayerTransition,
      prefs: AppPrefs,
      wakeLock: WakeLock,
      dispatcher: CoroutineDispatcher
    ): VlcPlayer = VlcPlayerImpl(
      media,
      title,
      eqPreset,
      duration,
      onPreparedTransition,
      prefs,
      wakeLock,
      dispatcher
    ).apply {
      VlcPlayerImpl.addToQueue(this)
    }
  }
}

private class VlcPlayerImpl(
  vlcMedia: IMedia,
  private val title: Title,
  private var eqPreset: VlcEqPreset,
  override var duration: Millis,
  onPreparedTransition: PlayerTransition,
  private val prefs: AppPrefs,
  private val wakeLock: WakeLock,
  dispatcher: CoroutineDispatcher
) : VlcPlayer {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val transitionPlayer = TheTransitionPlayer()
  private var transition: PlayerTransition = NoOpPlayerTransition
  private val onPrepared: PlayerTransition = getOnPrepared(onPreparedTransition)
  private var shutdownOnPrepared = false
  private var unmutedVolume = Volume.NONE
  private var firstStart: Boolean = true
  private val mediaPlayer: MediaPlayer = vlcMedia.makePlayer()

  override val eventFlow = MutableSharedFlow<MediaPlayerEvent>(extraBufferCapacity = 30)

  private var seekable = false
  override val isSeekable: Boolean
    get() = seekable

  private var pausable = false
  override val isPausable: Boolean
    get() = pausable

  private var prepared = false
  override val isPrepared: Boolean
    get() = prepared

  override val isValid: Boolean
    get() = prepared && !isShutdown

  override val equalizer: EqPreset
    get() = eqPreset
  override val time: Millis
    get() = Millis(mediaPlayer.time)

  override val isPlaying: Boolean
    get() = if (isPrepared) transition.isPlaying else onPrepared.isPlaying

  override val isPaused: Boolean
    get() = if (isPrepared) transition.isPaused else onPrepared.isPaused

  override var isShutdown = false

  private var realVolume = Volume.NONE
  override var volume: Volume
    get() = realVolume
    set(value) {
      realVolume = value.coerceIn(AvPlayer.DEFAULT_VOLUME_RANGE)
      muted = false
      unmutedVolume = realVolume // MediaPlayer get volume not working
      if (isValid) {
        mediaPlayer.volume = realVolume()
      }
    }

  private var muted = false
  override var isMuted: Boolean
    get() = muted
    set(mute) {
      if (isValid) {
        muted = mute
        if (mute) mediaPlayer.volume = 0 else mediaPlayer.volume = unmutedVolume()
      }
    }

  override var playbackRate: PlaybackRate
    get() = PlaybackRate(mediaPlayer.rate).coerceIn(PlaybackRate.RANGE)
    set(rate) {
      mediaPlayer.rate = rate.coerceIn(PlaybackRate.RANGE).value
    }

  override var audioDelay: Micros
    get() = Micros(mediaPlayer.audioDelay)
    set(delay) {
      mediaPlayer.audioDelay = delay()
    }
  override var spuDelay: Micros
    get() = Micros(mediaPlayer.spuDelay)
    set(delay) {
      mediaPlayer.spuDelay = delay()
    }

  override val isVideoPlayer: Boolean
    get() = isValid && mediaPlayer.videoTracksCount > 0
  override val isAudioPlayer: Boolean
    get() = isValid && !isVideoPlayer && mediaPlayer.audioTracksCount > 0

  private val pauseTransition: PlayerTransition
    get() = if (prefs.playPauseFade()) {
      PauseFadeOutTransition(prefs.playPauseFadeLength())
    } else PauseImmediateTransition()

  private val playTransition: PlayerTransition
    get() = if (prefs.playPauseFade()) {
      FadeInTransition(prefs.playPauseFadeLength())
    } else PlayImmediateTransition()

  override fun playStartPaused() {
    mediaPlayer.play()
  }

  override fun play(immediate: Boolean) {
    LOG._e { it("play isValid=%s", isValid) }
    if (isValid) startTransition(getPlayTransition { immediate }, false)
  }

  override fun pause(immediate: Boolean) {
    LOG._e { it("pause isValid=%s", isValid) }
    if (isValid) startTransition(getPauseTransition { immediate }, false)
  }

  override fun seek(position: Millis) {
    if (isValid && mediaPlayer.isSeekable) {
      mediaPlayer.time = position()
      // when the player is stopped it doesn't send position notifications
      if (!mediaPlayer.isPlaying) emit(MediaPlayerEvent.PositionUpdate(position, duration, false))
    }
  }

  override fun stop() {
    if (isValid) mediaPlayer.stop()
  }

  override fun shutdown() {
    if (!isShutdown) {
      transition.setCancelled()
      seekable = false
      isShutdown = true
      if (prepared) {
        secondStageShutdown()
      } else {
        shutdownOnPrepared = true
      }
    }
  }

  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {
    if (newEq is VlcEqPreset) {
      if (isValid && (applyEdits || eqPreset != newEq)) {
        setPlayerPreset(newEq)
      }
    } else LOG.e { it("Preset %s is not a VlcEqPreset", newEq) }
  }

  override fun transitionTo(transition: PlayerTransition) {
    startTransition(transition, false)
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

  private inline fun getPauseTransition(immediate: () -> Boolean) =
    if (immediate()) PauseImmediateTransition() else pauseTransition

  private fun getPlayTransition(immediate: () -> Boolean) =
    if (immediate()) PlayImmediateTransition() else playTransition

  private fun secondStageShutdown() {
    removeFromQueue(this)
    if (!mediaPlayer.isReleased) {
      mediaPlayer.release()
    }
    wakeLock.release()
  }

  private fun emit(event: MediaPlayerEvent) {
    scope.launch { eventFlow.emit(event) }
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
//    LOG._e { it("%s %s", title(), event.asString()) }
    when (event.type) {
      MediaPlayer.Event.Opening -> mediaPlayer.volume = realVolume()
      MediaPlayer.Event.Buffering -> if (!prepared && event.bufferedEnoughForPrepare()) {
        prepared = true
        startTransition(onPrepared, notifyPrepared = true)
      }
      MediaPlayer.Event.Playing -> if (prepared) wakeLock.acquire()
      MediaPlayer.Event.Paused -> if (prepared) wakeLock.release()
      MediaPlayer.Event.Stopped -> {
        wakeLock.release()
        seekable = false
        emit(MediaPlayerEvent.Stopped(time))
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
        emit(MediaPlayerEvent.PositionUpdate(Millis(event.timeChanged), duration, true))
      }
      MediaPlayer.Event.PositionChanged -> {
        event.positionChanged
      }
      MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
      MediaPlayer.Event.PausableChanged -> {
        pausable = event.pausable
        val reportedLength = mediaPlayer.length
        if (reportedLength > 0) {
          duration = Millis(reportedLength)
        }
      }
      MediaPlayer.Event.LengthChanged -> {

      }
    }
  }

  private fun MediaPlayer.Event.bufferedEnoughForPrepare() =
    buffering > BUFFERING_PERCENT_TRIGGER_PREPARED

  private fun getOnPrepared(transition: PlayerTransition) = transition.apply {
    setPlayer(transitionPlayer)
  }

  private fun IMedia.makePlayer(): MediaPlayer = MediaPlayer(this).apply {
    setEventListener(makeEventListener())
  }

  private fun startTransition(newTransition: PlayerTransition, notifyPrepared: Boolean = false) {
    if (isSeekable && transition.accept(newTransition)) {
      transition.setCancelled()
      newTransition.setPlayer(transitionPlayer)
      transition = newTransition
      if (notifyPrepared) emit(MediaPlayerEvent.Prepared(Millis(mediaPlayer.time), duration))
      scope.launch { newTransition.execute() }
    }
  }

  private inner class TheTransitionPlayer : TransitionPlayer {
    override val isPaused: Boolean
      get() = isValid && !mediaPlayer.isPlaying

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
      duration - mediaPlayer.time > AvPlayer.OFFSET_CONSIDERED_END
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

private fun MediaPlayer.Event.asString(): String {
  return when (type) {
    MediaPlayer.Event.MediaChanged -> "Event.MediaChanged"
    MediaPlayer.Event.Opening -> "Event.Opening"
    MediaPlayer.Event.Buffering -> "Event.Buffering $buffering"
    MediaPlayer.Event.Playing -> "Event.Playing"
    MediaPlayer.Event.Paused -> "Event.Paused"
    MediaPlayer.Event.Stopped -> "Event.Stopped"
    MediaPlayer.Event.EndReached -> "Event.EndReached"
    MediaPlayer.Event.EncounteredError -> "Event.EncounteredError"
    MediaPlayer.Event.TimeChanged -> "Event.TimeChanged $timeChanged"
    MediaPlayer.Event.PositionChanged -> "Event.PositionChanged $positionChanged"
    MediaPlayer.Event.SeekableChanged -> "Event.SeekableChanged $seekable"
    MediaPlayer.Event.PausableChanged -> "Event.PausableChanged $pausable"
    MediaPlayer.Event.LengthChanged -> "Event.LengthChanged $lengthChanged"
    MediaPlayer.Event.Vout -> "Event.Vout $voutCount"
    MediaPlayer.Event.ESAdded -> "Event.ESAdded"
    MediaPlayer.Event.ESDeleted -> "Event.ESDeleted"
    MediaPlayer.Event.ESSelected -> "Event.ESSelected"
    MediaPlayer.Event.RecordChanged -> "Event.RecordChanged"
    else -> "Event.UNKNOWN"
  }
}
