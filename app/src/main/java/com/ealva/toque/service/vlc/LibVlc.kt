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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.StartPaused
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.FileNotFoundException

interface LibVlcSingleton {
  /**
   * Use the LibVlc instance within the scope of the [block] function. Do not save the instance
   * anywhere as it may become invalid. Specifically, [reset] invalidates the current instance
   * and forces it to be rebuilt when the function is called.
   */
  suspend fun <R> withInstance(block: (LibVlc) -> R): R

  /**
   * Set the LibVlc instance as invalid and force it's reconstruction when needed. This is needed
   * due to some parameters needed to construct LibVlc being changed. After this is called, any
   * current Media players should be reset to incorporate the new parameters.
   */
  fun reset()

  companion object {
    operator fun invoke(
      context: Context,
      prefsSingleton: LibVlcPrefsSingleton,
      vlcUtil: VlcUtil? = null,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): LibVlcSingleton = LibVlcSingletonImpl(context, prefsSingleton, vlcUtil, dispatcher)
  }
}

private class LibVlcSingletonImpl(
  private val context: Context,
  private val prefsSingleton: LibVlcPrefsSingleton,
  /** Default to null because don't want this during injection but want to stub for test */
  private val vlcUtil: VlcUtil?,
  private val dispatcher: CoroutineDispatcher
) : LibVlcSingleton {
  /**
   * @throws IllegalStateException if error during LibVLC initialization
   */
  private suspend fun make(): LibVlc {
    return LibVlcImpl(
      context,
      LibVLC(context, libVlcOptions(prefsSingleton.instance(), vlcUtil ?: VlcUtil(context)))
    )
  }

  @Volatile
  private var instance: LibVlc? = null
  private val mutex = Mutex()

  private suspend fun instance(): LibVlc = instance ?: withContext(dispatcher) {
    mutex.withLock { instance ?: make().also { instance = it } }
  }

  override suspend fun <R> withInstance(block: (LibVlc) -> R): R {
    return block(instance())
  }

  override fun reset() {
    instance = null
  }
}

private val LOG by lazyLogger(LibVlc::class)

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
    startPaused: StartPaused,
    prefs: LibVlcPrefs
  ): IMedia

  fun makeVideoMedia(uri: Uri, startPaused: Boolean, prefs: LibVlcPrefs): IMedia

  fun makeMediaPlayer(media: IMedia): MediaPlayer
}

@Suppress("NOTHING_TO_INLINE")
inline fun Millis.toFloat(): Float = value.toFloat()

private class LibVlcImpl(
  context: Context,
  private val libVLC: LibVLC
) : LibVlc {
  private val resolver = context.contentResolver
  override fun libVlcVersion(): String = LibVLC.version()

  override fun makeNativeMedia(uri: Uri): IMedia {
    return when (uri.scheme) {
      ContentResolver.SCHEME_CONTENT -> makeNativeMediaFromContentUri(uri)
      else -> org.videolan.libvlc.Media(libVLC, uri)
    }
  }

  private fun makeNativeMediaFromContentUri(uri: Uri) =
    resolver.openFileDescriptor(uri, "r")?.let { parcelFileDescriptor ->
      org.videolan.libvlc.Media(libVLC, parcelFileDescriptor.fileDescriptor)
    } ?: throw FileNotFoundException(uri.toString())

  override fun makeAudioMedia(
    uri: Uri,
    initialSeek: Millis,
    startPaused: StartPaused,
    prefs: LibVlcPrefs
  ): IMedia = makeNativeMedia(uri).setAudioMediaOptions(startPaused, initialSeek, prefs)

  private fun IMedia.setAudioMediaOptions(
    startPaused: StartPaused,
    initialSeek: Millis,
    prefs: LibVlcPrefs
  ) = apply {
    if (startPaused()) {
      addMediaOption { ":start-paused" }
    }
    if (initialSeek > Millis(0)) {
      addMediaOption { """:start-time=${initialSeek.toFloatSeconds()}""" }
    }
    addMediaOption { ":no-video" }
    addMediaOption { ":no-volume-save" }
    addMediaOption { ":no-sub-autodetect-file" }
    maybeSetReplayGain { prefs }
    setMediaHardwareAcceleration { prefs }
//    val bufferSize = prefs.bufferSize
//    if (bufferSize != BufferSize.Default) {
//      addMediaOption(media, ":file-caching=" + bufferSize.size)
//    }
  }

  private inline fun IMedia.maybeSetReplayGain(prefs: () -> LibVlcPrefs) = prefs().apply {
    if (replayGainMode() != ReplayGainMode.None) {
      addMediaOption { """:audio-replay-gain-mode=${replayGainMode()}""" }
      addMediaOption { """:audio-replay-gain-preamp=${replayGainPreamp()}""" }
      addMediaOption { """:audio-replay-gain-default=${defaultReplayGain()}""" }
    }
  }

  private inline fun IMedia.setMediaHardwareAcceleration(prefs: () -> LibVlcPrefs) {
    when (prefs().hardwareAcceleration()) {
      HardwareAcceleration.Disabled -> setHWDecoderEnabled(false, false)
      HardwareAcceleration.Full -> setHWDecoderEnabled(true, true)
      HardwareAcceleration.Decoding -> {
        setHWDecoderEnabled(true, true)
        addMediaOption { ":no-mediacodec-dr" }
        addMediaOption { ":no-omxil-dr" }
      }
      HardwareAcceleration.Automatic -> {
      }
    }
  }

  private inline fun IMedia.addMediaOption(option: () -> String) = addOption(option())

  override fun makeVideoMedia(uri: Uri, startPaused: Boolean, prefs: LibVlcPrefs): IMedia {
    TODO("Not yet implemented")
  }

  override fun makeMediaPlayer(media: IMedia): MediaPlayer {
    return MediaPlayer(media)
  }
}
