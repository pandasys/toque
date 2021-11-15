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

package com.ealva.toque.audioout

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle
import org.videolan.libvlc.util.HWDecoderUtil

enum class AudioOutputModule(
  override val id: Int,
  private val module: String,
  private val hardwareOutput: HWDecoderUtil.AudioOutput,
  val forceStartVolumeZero: Boolean,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle {
  OpenSlEs(
    1,
    "opensles_android",
    HWDecoderUtil.AudioOutput.OPENSLES,
    false,
    R.string.OpenSL
  ),
  AudioTrack(
    2,
    "android_audiotrack",
    HWDecoderUtil.AudioOutput.AUDIOTRACK,
    true,
    R.string.AudioTrack
  );

  override fun toString(): String = module

  companion object {
    val DEFAULT = select(AudioTrack)

    private val deviceCapable = HWDecoderUtil.getAudioOutputFromDevice()

    /**
     * Select a [preferred] output, but may be different if not supported by the device
     */
    fun select(preferred: AudioOutputModule): AudioOutputModule {
      return when (deviceCapable) {
        HWDecoderUtil.AudioOutput.ALL -> preferred
        HWDecoderUtil.AudioOutput.AUDIOTRACK -> { // always supported
          if (preferred.hardwareOutput == deviceCapable) preferred else OpenSlEs
        }
        HWDecoderUtil.AudioOutput.OPENSLES -> {
          if (preferred.hardwareOutput == deviceCapable) preferred else AudioTrack
        }
        else -> AudioTrack
      }
    }
  }
}
