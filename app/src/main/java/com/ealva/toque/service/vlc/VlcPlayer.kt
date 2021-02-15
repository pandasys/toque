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

import android.os.PowerManager
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.common.toMillis
import com.ealva.toque.common.toPlaybackRate
import com.ealva.toque.common.toVolume
import com.ealva.toque.log._w
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.PlayerTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.vlc.VlcPlayer.Companion.VOLUME_RANGE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.Event
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(VlcPlayer::class)

interface VlcPlayer {
  /** True if the player is prepared and not shutdown. */
  val isValid: Boolean

  /** Has the media been buffered enough to begin playback */
  val isPrepared: Boolean

  /**
   * True if the player [isValid] and in a playable state (typically meaning initial buffering is
   * sufficient to play)
   */
  val isPlayable: Boolean

  val isPlaying: Boolean
  val isPaused: Boolean
  val isShutdown: Boolean

  /**
   * Begin/resume playback if currently paused using the transition selected via [selector]. If
   * selector is [TransitionSelector.Immediate], no fade in is applied regardless of user
   * setting.
   */
  fun play(selector: TransitionSelector)

  /**
   * Pause playback if currently playing using the transition selected via [selector]. If
   * selector is [TransitionSelector.Immediate], no fade out is applied regardless of user
   * setting.
   */
  fun pause(selector: TransitionSelector)

  /** Is the media prepared and seekable */
  val isSeekable: Boolean

  /** Go to playback [position] in the media */
  fun seek(position: Millis)

  /**
   * Apply a transition to the player if it is valid from the current state and the player is not
   * [shutdown]
   */
  fun transitionTo(transition: PlayerTransition)

  /** Stops playing and releases some audio output resources that must be reset if played again */
  fun stop()

  /**
   * Cancel any current playback or transition, remove event listeners, and release native
   * resources.
   */
  fun shutdown()

  /** Media length */
  val duration: Millis

  /** The playback position in the media. Vlc uses the term "time", so we'll use it here too */
  val time: Millis

  /** Current preset applied to the player. Can be [VlcEqPreset.NONE] */
  var preset: VlcEqPreset

  /** Playback volume in the range [VOLUME_RANGE] */
  var volume: Volume

  /** Range of valid volumes, same as [VOLUME_RANGE] */
  val volumeRange: VolumeRange

  /**
   * The playback rate, which defaults to [PlaybackRate.NORMAL]. Setting the rate clamps the value
   * to [PlaybackRate.PLAYBACK_RATE_RANGE].
   */
  var rate: PlaybackRate

//  fun reset()
//  val vlcVout: IVLCVout
//  val audioTracks: List<TrackInfo>
//  fun setAudioTrack(id: Int)
//  val spuTracks: List<TrackInfo>
//  fun setSpuTrack(id: Int)
//  var audioSync: Long
//  var subtitleSync: Long

  companion object {
    val VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED

    fun make(
      libVlc: LibVlc,
      vlcMedia: VlcMedia,
      duration: Millis,
      listener: VlcPlayerListener,
      vlcEqPreset: VlcEqPreset,
      onPreparedTransition: PlayerTransition,
      prefs: AppPreferences,
      powerManager: PowerManager,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): VlcPlayer {
      return VlcPlayerImpl(
        libVlc.makeMediaPlayer(vlcMedia.media),
        duration,
        listener,
        vlcEqPreset,
        onPreparedTransition,
        prefs,
        powerManager,
        dispatcher
      )
    }
  }
}

private const val BUFFERING_PERCENT_TRIGGER_PREPARED = 90.0
private val DURATION_OFFSET_CONSIDERED_END = Millis.TWO_HUNDRED

@Suppress("MagicNumber")
private val WAKE_LOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(25).toMillis()

private class VlcPlayerImpl(
  private val player: MediaPlayer,
  override var duration: Millis,
  private val updateListener: VlcPlayerListener,
  private var vlcEqPreset: VlcEqPreset,
  private val onPreparedTransition: PlayerTransition,
  private val prefs: AppPreferences,
  private val powerManager: PowerManager,
  dispatcher: CoroutineDispatcher
) : VlcPlayer {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val wakeLock: PowerManager.WakeLock = makeWakeLock()
  private var firstStart = false
  private var realVolume: Volume = 0.toVolume()
  private var prepared = false
  override var isShutdown = false
  private var pausable = false
  private var seekable = false
  private var transition: PlayerTransition = NoOpPlayerTransition
  private val transitionPlayer = makeTransitionPlayer()

  init {
    addToQueue(this)
    onPreparedTransition.setPlayer(transitionPlayer)
    vlcEqPreset.applyToPlayer(player)
    player.setEventListener(makeEventListener())
  }

  override val isValid: Boolean
    get() = prepared && !isShutdown

  override val isPlayable: Boolean
    get() = isValid && pausable

  override var volume: Volume
    get() = realVolume
    set(value) {
      realVolume = value.coerceIn(VOLUME_RANGE)
      if (isValid) {
        player.volume = realVolume.value
      }
    }
  override val volumeRange: VolumeRange = VOLUME_RANGE

  override fun shutdown() {
    if (!isShutdown) {
      transition.setCancelled()
      isShutdown = true
      secondStageShutdown()
    }
  }

  override var rate: PlaybackRate
    get() = player.rate.toPlaybackRate()
    set(value) {
      player.rate = value.coerceIn(PlaybackRate.PLAYBACK_RATE_RANGE).rate
    }
  override var preset: VlcEqPreset
    get() = vlcEqPreset
    set(value) {
      value.applyToPlayer(player)
      vlcEqPreset = value
    }
  override val isSeekable: Boolean
    get() = seekable

  override fun seek(position: Millis) {
    if (isValid && player.isSeekable) {
      player.time = position.value
      if (!player.isPlaying) {
        scope.launch { notifyTimeChangedImmediately(position, duration) }
      }
    }
  }

  override val time: Millis
    get() = player.time.toMillis()

  override val isPlaying: Boolean
    get() = if (isPrepared) {
      transition.isPlaying
    } else {
      onPreparedTransition.isPlaying
    }
  override val isPaused: Boolean
    get() = if (isPrepared) {
      transition.isPaused
    } else {
      onPreparedTransition.isPaused
    }

  private fun secondStageShutdown() {
    removeFromQueue(this)
    release()
  }

  fun release() {
    player.setEventListener(null)
    scope.cancel()
    if (!player.isReleased) player.release()
    if (wakeLock.isHeld) wakeLock.release()
  }

  override fun pause(selector: TransitionSelector) {
    if (isValid) startTransition(selector.selectPause(::getPauseTransition))
  }

  override fun play(selector: TransitionSelector) {
    if (isValid) startTransition(selector.selectPlay(::getPlayTransition))
  }

  override fun transitionTo(transition: PlayerTransition) {
    startTransition(transition)
  }

  override fun stop() {
    if (isValid) player.stop()
  }

  override val isPrepared: Boolean
    get() = prepared

  private fun makeWakeLock(): PowerManager.WakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    javaClass.name
  ).apply { setReferenceCounted(false) }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
    when (event.type) {
      Event.Opening -> {
        player.volume = realVolume.value
      }
      Event.Buffering -> if (!prepared && event.buffering > BUFFERING_PERCENT_TRIGGER_PREPARED) {
        prepared = true
        startTransition(onPreparedTransition, notifyPrepared = true)
      }
      Event.Playing -> if (prepared) {
        if (!wakeLock.isHeld) {
          wakeLock.acquire(WAKE_LOCK_TIMEOUT.value)
        }
      }
      Event.Paused -> if (prepared) {
        wakeLock.release()
      }
      Event.Stopped -> {
        wakeLock.release()
        updateListener.onStopped()
      }
      Event.EndReached -> {
        shutdown()
        updateListener.onPlaybackComplete()
      }
      Event.EncounteredError -> {
        prepared = false
        shutdown()
        updateListener.onError()
      }
      Event.TimeChanged -> {
        notifyTimeChangedImmediately(event.timeChanged.toMillis(), duration)
      }
      Event.PausableChanged -> {
        pausable = event.pausable
        val reportedLength = player.length
        if (reportedLength > 0) {
          duration = reportedLength.toMillis()
        }
      }
      Event.SeekableChanged -> seekable = event.seekable
    }
  }

  private fun notifyTimeChangedImmediately(time: Millis, duration: Millis) {
    updateListener.onPositionUpdate(time, duration)
  }

  private fun startTransition(newTransition: PlayerTransition, notifyPrepared: Boolean = false) {
    if (!isShutdown && transition.accept(newTransition)) {
      transition.setCancelled()
      newTransition.setPlayer(transitionPlayer)
      transition = newTransition
      val pos = player.time.toMillis()
      val dur = duration
      scope.launch {
        if (notifyPrepared) {
          updateListener.onPrepared(pos, dur)
        }
        newTransition.execute()
      }
    }
  }

  private fun getPauseTransition(): PlayerTransition =
    if (prefs.fadeOnPlayPause()) PauseFadeOutTransition(prefs.playPauseFadeLength())
    else PauseImmediateTransition()

  private fun getPlayTransition(): PlayerTransition =
    if (prefs.fadeOnPlayPause()) FadeInTransition(prefs.playPauseFadeLength())
    else PlayImmediateTransition()

  private fun makeTransitionPlayer() = object : TransitionPlayer {
    override val isPaused: Boolean
      get() = isValid && !player.isPlaying

    override val isPlaying: Boolean
      get() = isValid && player.isPlaying

    override val isShutdown: Boolean
      get() = this@VlcPlayerImpl.isShutdown

    override fun play() {
      if (isValid) player.play()
    }

    override fun notifyPlaying() {
      val first = firstStart
      firstStart = false
      scope.launch { updateListener.onStart(first) }
    }

    override fun pause() {
      if (isValid) player.pause()
    }

    override fun notifyPaused() {
      val pos = player.time
      scope.launch { updateListener.onPaused(pos.toMillis()) }
    }

    override var volume: Volume
      get() = this@VlcPlayerImpl.volume
      set(value) {
        this@VlcPlayerImpl.volume = value
      }
    override val volumeRange: VolumeRange
      get() = VOLUME_RANGE

    override val remainingTime: Millis
      get() = if (isValid) (duration - player.time.toMillis()) else Millis.ZERO

    override fun shutdown() = this@VlcPlayerImpl.shutdown()

    override fun shouldContinue(): Boolean {
      return !this@VlcPlayerImpl.isShutdown &&
        duration > Millis.ZERO &&
        (duration - player.time.toMillis()) > DURATION_OFFSET_CONSIDERED_END
    }
  }

  companion object {
    private val fifoPlayerQueue = ArrayDeque<VlcPlayerImpl>(3)
    private val queueLock = ReentrantLock(true)

    private fun addToQueue(player: VlcPlayerImpl) {
      queueLock.withLock {
        // We can have at most 2 active players in the queue.
        while (fifoPlayerQueue.size > 1) {
          fifoPlayerQueue.removeLast().shutdown()
        }
        fifoPlayerQueue.addFirst(player)
      }
    }

    private fun removeFromQueue(player: VlcPlayerImpl) {
      queueLock.withLock {
        fifoPlayerQueue.remove(player)
        while (fifoPlayerQueue.size > 1) {
          fifoPlayerQueue.removeLast().shutdown()
          LOG._w { it("removeFromQueue queue size > 1") }
        }
      }
    }
  }
}

object NullVlcPlayer : VlcPlayer {
  override val isValid: Boolean = false
  override val isPlayable: Boolean = false
  override var volume: Volume
    get() = 0.toVolume()
    set(@Suppress("UNUSED_PARAMETER") value) {}
  override val volumeRange: VolumeRange = VOLUME_RANGE
  override fun pause(selector: TransitionSelector) = Unit
  override fun play(selector: TransitionSelector) = Unit
  override fun transitionTo(transition: PlayerTransition) = Unit
  override fun shutdown() = Unit
  override var rate: PlaybackRate
    get() = PlaybackRate.NORMAL
    set(@Suppress("UNUSED_PARAMETER") value) {}
  override var preset: VlcEqPreset
    get() = VlcEqPreset.NONE
    set(@Suppress("UNUSED_PARAMETER") value) {}
  override val duration: Millis = Millis.ZERO
  override val isSeekable: Boolean = false
  override fun seek(position: Millis) = Unit
  override val time: Millis = Millis.ZERO
  override val isPlaying: Boolean = false
  override val isPaused: Boolean = false
  override val isShutdown: Boolean = false
  override fun stop() = Unit
  override val isPrepared: Boolean = false
}
