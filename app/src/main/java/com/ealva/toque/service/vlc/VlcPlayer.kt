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
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.common.toMillis
import com.ealva.toque.common.toVolume
import com.ealva.toque.log._w
import com.ealva.toque.service.player.PlayerTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.vlc.VlcPlayer.Companion.VOLUME_RANGE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.Event
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(VlcPlayer::class)

interface VlcPlayer {
  val isValid: Boolean

  val isPlayable: Boolean

  var volume: Volume

  val volumeRange: VolumeRange

  fun pause(immediate: Boolean = false)

  fun play(immediate: Boolean = false)

  fun shutdown()

  companion object {
    val VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED

    fun make(
      libVlc: LibVlc,
      vlcMedia: VlcMedia,
      duration: Millis,
      vlcEqPreset: VlcEqPreset,
      onPreparedTransition: PlayerTransition,
      powerManager: PowerManager,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): VlcPlayer {
      return VlcPlayerImpl(
        libVlc.makeMediaPlayer(vlcMedia.media),
        duration,
        vlcEqPreset,
        onPreparedTransition,
        powerManager,
        dispatcher
      ).apply {
        setPreset(vlcEqPreset)
      }
    }
    /*
      override var duration: Long,
private val updateListener: AvPlayerListener,
private var eqPreset: Equalizer?,
private val initialSeek: Long,
private val onPreparedTransition: PlayerTransition,
startPaused: Boolean,
private val powerManager: PowerManager,
private val prefs: AppPreferences

     */
  }
}

private const val BUFFERING_PERCENT_TRIGGER_PREPARED = 90.0
private val DURATION_OFFSET_CONSIDERED_END = Millis.TWO_HUNDRED

@Suppress("MagicNumber")
private val WAKE_LOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(25).toMillis()

private class VlcPlayerImpl(
  private val player: MediaPlayer,
  private var duration: Millis,
  private var eqPreset: VlcEqPreset,
  private val onPreparedTransition: PlayerTransition,
  private val powerManager: PowerManager,
  private val dispatcher: CoroutineDispatcher
) : VlcPlayer {
  private val scope: CoroutineScope = MainScope()
  private val wakeLock: PowerManager.WakeLock = makeWakeLock()
  private var realVolume: Volume = 0.toVolume()
  private var prepared = false
  private var isShutdown = false
  private var muted = false
  private var unmutedVolume: Volume = 0.toVolume()
  private var shutdownOnPrepared = false
  private var pausable = false
  private var seekable = false

  init {
    addToQueue(this)
    eqPreset.applyToPlayer(player)
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
      muted = false
      unmutedVolume = realVolume // MediaPlayer get volume not working
      if (isValid) {
        player.volume = realVolume.value
      }
    }
  override val volumeRange: VolumeRange = VOLUME_RANGE

  fun release() {
    player.setEventListener(null)
    scope.cancel()
    player.release()
  }

  override fun shutdown() {
    if (!isShutdown) {

//      transition.setCancelled()
      isShutdown = true
      secondStageShutdown()
    }
  }

  private fun secondStageShutdown() {
    removeFromQueue(this)
    if (!player.isReleased) player.release()
    if (wakeLock.isHeld) wakeLock.release()
  }

  override fun pause(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun play(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  fun setPreset(vlcEqPreset: VlcEqPreset) {
    eqPreset = vlcEqPreset
    vlcEqPreset.applyToPlayer(player)
  }

  private fun makeWakeLock(): PowerManager.WakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    javaClass.name
  ).apply {
    setReferenceCounted(false) // we don't need ref counting, just off or on
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
    when (event.type) {
      Event.Opening -> {
        // part of VLC HACK to quiet volume at start of fade in
        // when setting the volume, use "realVolume", don't assume starts at zero
        player.volume = realVolume.value
        // ----------------------------------------------------------------------
      }
      Event.Buffering -> if (!prepared && event.buffering > BUFFERING_PERCENT_TRIGGER_PREPARED) {
        prepared = true
//        startTransition(lock, onPreparedTransition, true)
      }
      Event.Playing -> if (prepared) {
        if (!wakeLock.isHeld) {
          wakeLock.acquire(WAKE_LOCK_TIMEOUT.value)
        }
        //                            notifyPlaying();
      }
      Event.Paused -> if (prepared) {
        wakeLock.release()
      }
      Event.Stopped -> {
        wakeLock.release()
//        updateListener.onStopped()
      }
      Event.EndReached -> {
        shutdown()
//        updateListener.onPlaybackComplete()
      }
      Event.EncounteredError -> {
        prepared = false
        shutdown()
//        updateListener.onError()
      }
      Event.TimeChanged -> {
//        notifyTimeChangedImmediately(event.timeChanged, duration)
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

  private val transitionPlayer = object : TransitionPlayer {
    override val isPaused: Boolean
      get() = isValid && player.isPlaying

    override val isPlaying: Boolean
      get() = isValid && player.isPlaying

    override val isShutdown: Boolean
      get() = this@VlcPlayerImpl.isShutdown

    override fun play() {
      if (isValid) player.play()
    }

    override fun notifyPlaying() {
      TODO("Emit playing event")
//      coroutineScope {
//        launch(Dispatchers.Main) { this@VlcPlayer.notifyPlaying() }
//      }
    }

    override fun pause() {
      if (isValid) player.pause()
    }

    override fun notifyPaused() {
      TODO("Emit paused event")
//      coroutineScope {
//        launch(Dispatchers.Main) { this@VlcPlayer.notifyPaused() }
//      }
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
  override fun pause(immediate: Boolean) = Unit
  override fun play(immediate: Boolean) = Unit
  override fun shutdown() = Unit
}
