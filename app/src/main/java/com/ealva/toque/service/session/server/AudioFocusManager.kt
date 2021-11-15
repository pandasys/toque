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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.toque.service.session.server.AudioFocusManager.ContentType
import com.ealva.toque.service.session.server.AudioFocusManager.FocusReaction
import com.ealva.toque.service.session.server.AudioFocusManager.PlayState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(AudioFocusManager::class)

interface AudioFocusManager {
  enum class FocusReaction {
    None,
    Play,
    Pause,
    StopForeground,
    Duck,
    EndDuck
  }

  val reactionFlow: Flow<FocusReaction>

  enum class ContentType {
    Audio,
    Video;
  }

  fun interface PlayState {
    fun isPlaying(): Boolean
  }

  fun requestFocus(contentType: ContentType, playState: PlayState): Boolean

  fun abandonFocus(contentType: ContentType)

  /** Releases resources and removes broadcast receivers. Not usable after release */
  fun release()

  companion object {
    operator fun invoke(
      context: Context,
      audioManager: AudioManager,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AudioFocusManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioFocusManager28(context, audioManager, dispatcher)
    } else {
      AudioFocusManagerPre28(context, audioManager, dispatcher)
    }
  }
}

class AudioFocusManagerPre28(
  context: Context,
  audioManager: AudioManager,
  dispatcher: CoroutineDispatcher
) : BaseAudioFocusManager(context, audioManager, dispatcher) {
  @Suppress("DEPRECATION")
  override fun doRequestFocus(
    contentType: ContentType
  ): Int = audioManager.requestAudioFocus(
    this,
    AudioManager.STREAM_MUSIC,
    AudioManager.AUDIOFOCUS_GAIN
  )

  @Suppress("DEPRECATION")
  override fun doAbandonFocus(contentType: ContentType): Int {
    return audioManager.abandonAudioFocus(this)
  }
}

@RequiresApi(Build.VERSION_CODES.O)
class AudioFocusManager28(
  context: Context,
  audioManager: AudioManager,
  dispatcher: CoroutineDispatcher
) : BaseAudioFocusManager(context, audioManager, dispatcher) {
  private val musicRequest = makeFocusRequest(AudioAttributes.CONTENT_TYPE_MUSIC)
  private val movieRequest by lazy { makeFocusRequest(AudioAttributes.CONTENT_TYPE_MOVIE) }

  override fun doRequestFocus(contentType: ContentType): Int {
    return when (contentType) {
      ContentType.Audio -> audioManager.requestAudioFocus(musicRequest)
      ContentType.Video -> audioManager.requestAudioFocus(movieRequest)
    }
  }

  override fun doAbandonFocus(contentType: ContentType): Int {
    return when (contentType) {
      ContentType.Audio -> audioManager.abandonAudioFocusRequest(musicRequest)
      ContentType.Video -> audioManager.abandonAudioFocusRequest(movieRequest)
    }
  }

  private fun makeFocusRequest(contentType: Int): AudioFocusRequest {
    return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(contentType)
          .build()
      )
      .setWillPauseWhenDucked(false)
      .setAcceptsDelayedFocusGain(true)
      .setOnAudioFocusChangeListener(this)
      .build()
  }
}

abstract class BaseAudioFocusManager(
  private val ctx: Context,
  protected val audioManager: AudioManager,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : AudioFocusManager, AudioManager.OnAudioFocusChangeListener {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val focusLock: ReentrantLock = ReentrantLock()
  private var haveAudioFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false
  private var endDuckOnFocusGain = false
  private var released = false
  private var playState: PlayState? = null

  private val receiver = AudioBecomingNoisyReceiver().apply {
    ctx.registerReceiver(this, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
  }

  override fun release() {
    released = true
    ctx.unregisterReceiver(receiver)
  }

  /**  */
  override val reactionFlow = MutableSharedFlow<FocusReaction>(extraBufferCapacity = 2)

  private fun emitReaction(reaction: FocusReaction) {
    scope.launch { reactionFlow.emit(reaction) }
  }

  protected abstract fun doRequestFocus(contentType: ContentType): Int
  protected abstract fun doAbandonFocus(contentType: ContentType): Int

  override fun requestFocus(contentType: ContentType, playState: PlayState): Boolean {
    LOG._e { it("requestFocus type=%s state=%s", contentType, playState) }
    return when {
      released -> {
        LOG.e { it("Requested focus after release") }
        false
      }
      haveAudioFocus -> true
      else -> {
        val res = doRequestFocus(contentType)
        focusLock.withLock {
          when (res) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
              LOG._e { it("AUDIOFOCUS_REQUEST_FAILED") }
              haveAudioFocus = false
              false
            }
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
              LOG._e { it("AUDIOFOCUS_REQUEST_GRANTED") }
              haveAudioFocus = true
              true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
              LOG._e { it("AUDIOFOCUS_REQUEST_DELAYED") }
              haveAudioFocus = false
              playbackDelayed = true
              false
            }
            else -> false
          }
        }
      }
    }
  }

  override fun abandonFocus(contentType: ContentType) {
    LOG._e { it("abandonFocus type=%s", contentType) }
    doAbandonFocus(contentType)
  }

  override fun onAudioFocusChange(focusChange: Int) {
    LOG._e { it("onAudioFocusChange") }
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        LOG._e { it("AUDIOFOCUS_GAIN") }
        if (playbackDelayed || resumeOnFocusGain) {
          focusLock.withLock {
            haveAudioFocus = true
            playbackDelayed = false
            resumeOnFocusGain = false
          }
          emitReaction(FocusReaction.Play)
        } else if (endDuckOnFocusGain) {
          focusLock.withLock {
            endDuckOnFocusGain = false
          }
          emitReaction(FocusReaction.EndDuck)
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        LOG._e { it("AUDIOFOCUS_LOSS") }
        focusLock.withLock {
          haveAudioFocus = false
          resumeOnFocusGain = false
          playbackDelayed = false
          endDuckOnFocusGain = false
        }
        emitReaction(FocusReaction.StopForeground)
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        LOG._e { it("AUDIOFOCUS_LOSS_TRANSIENT") }
        val isPlaying = playState?.isPlaying() ?: false
        focusLock.withLock {
          // only resume if playback is being interrupted
          resumeOnFocusGain = isPlaying
          playbackDelayed = false
        }
        if (isPlaying) emitReaction(FocusReaction.Pause)
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        LOG._e { it("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK") }
        val isPlaying = playState?.isPlaying() ?: false
        focusLock.withLock {
          endDuckOnFocusGain = isPlaying
        }
        if (isPlaying) emitReaction(FocusReaction.Duck)
      }
    }
  }

  private inner class AudioBecomingNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        LOG.i { it("ACTION_AUDIO_BECOMING_NOISY") }
        emitReaction(FocusReaction.Pause)
      }
    }
  }
}
