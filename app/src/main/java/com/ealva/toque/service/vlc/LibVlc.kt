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

import android.content.Context
import android.net.Uri
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.compareTo
import com.ealva.toque.common.toFloat
import com.ealva.toque.log._e
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

private val LOG by lazyLogger(LibVlc::class)

class LibVlcSingleton(
  private val context: Context,
  private val prefsSingleton: LibVlcPreferencesSingleton,
  /** Default to null because don't want this during injection but want to stub for test */
  private val vlcUtil: VlcUtil? = null,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  /**
   * @throws IllegalStateException if error during LibVLC initialization
   */
  private suspend fun make(): LibVlc {
    return LibVlcImpl(
      LibVLC(context, libVlcOptions(prefsSingleton.instance(), vlcUtil ?: VlcUtil(context)))
    )
  }

  @Volatile
  private var instance: LibVlc? = null
  private val mutex = Mutex()

  /**
   * Get the single instance, which may need to be constructed
   *
   * @throws IllegalStateException if error during LibVLC initialization
   */
  suspend fun instance(): LibVlc {
    instance?.let { return it } ?: return withContext(dispatcher) {
      mutex.withLock {
        instance?.let { instance } ?: make().also { instance = it }
      }
    }
  }
}

interface LibVlc {
  fun libVlcVersion(): String

//  fun makeMediaPlayer(
//    media_uri: Uri,
//    hasHdmiAudio: Boolean,
//    initialSeek: Long,
//    startPaused: Boolean,
//    prefs: LibVlcPreferences
//  ): MediaPlayerExt

  fun makeNativeMedia(uri: Uri): IMedia

  fun makeAudioMedia(
    uri: Uri,
    initialSeek: Millis,
    startPaused: Boolean,
    prefs: LibVlcPreferences
  ): IMedia

  fun makeVideoMedia(uri: Uri, startPaused: Boolean, prefs: LibVlcPreferences): IMedia

  fun makeMediaPlayer(media: IMedia): MediaPlayer
}

private class LibVlcImpl(
  private val libVLC: LibVLC
) : LibVlc {

  override fun libVlcVersion(): String {
    return LibVLC.version()
  }

  override fun makeNativeMedia(uri: Uri): IMedia {
    return org.videolan.libvlc.Media(libVLC, uri)
  }

  override fun makeAudioMedia(
    uri: Uri,
    initialSeek: Millis,
    startPaused: Boolean,
    prefs: LibVlcPreferences
  ): IMedia {
    return makeNativeMedia(uri).setAudioMediaOptions(startPaused, initialSeek, prefs)
  }

  private fun IMedia.setAudioMediaOptions(
    startPaused: Boolean,
    initialSeek: Millis,
    prefs: LibVlcPreferences
  ) = apply {
    if (startPaused) {
      addMediaOption { ":start-paused" }
    }
    if (initialSeek > 0) {
      addMediaOption { """:start-time=${initialSeek.toFloat()}""" }
    }
    addMediaOption { ":no-video" }
    addMediaOption { ":no-volume-save" }
    maybeSetReplayGain { prefs }
    setMediaHardwareAcceleration { prefs }
//    val bufferSize = prefs.bufferSize
//    if (bufferSize != BufferSize.Default) {
//      addMediaOption(media, ":file-caching=" + bufferSize.size)
//    }
  }

  private inline fun IMedia.maybeSetReplayGain(prefs: () -> LibVlcPreferences) = prefs().apply {
    if (replayGainMode() != ReplayGainMode.None) {
      addMediaOption { """:audio-replay-gain-mode=${replayGainMode()}""" }
      addMediaOption { """:audio-replay-gain-preamp=${replayGainPreamp()}""" }
      addMediaOption { """:audio-replay-gain-default=${replayGainDefaultPreamp()}""" }
    }
  }

  private inline fun IMedia.setMediaHardwareAcceleration(prefs: () -> LibVlcPreferences) {
    when (prefs().hardwareAcceleration()) {
      HardwareAcceleration.DISABLED -> setHWDecoderEnabled(false, false)
      HardwareAcceleration.FULL -> setHWDecoderEnabled(true, true)
      HardwareAcceleration.DECODING -> {
        setHWDecoderEnabled(true, true)
        addMediaOption { ":no-mediacodec-dr" }
        addMediaOption { ":no-omxil-dr" }
      }
      HardwareAcceleration.AUTOMATIC -> {
      }
    }
  }

  private inline fun IMedia.addMediaOption(option: () -> String) {
    LOG._e { it("IMedia option=${option()}") }
    addOption(option())
  }

  override fun makeVideoMedia(uri: Uri, startPaused: Boolean, prefs: LibVlcPreferences): IMedia {
    TODO("Not yet implemented")
  }

  override fun makeMediaPlayer(media: IMedia): MediaPlayer {
    return MediaPlayer(media)
  }
}
