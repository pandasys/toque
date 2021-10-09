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

package com.ealva.toque.service.audio

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.db.EqPresetInfo
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val LOG by lazyLogger(ActiveEqPreset::class)

interface ActiveEqPreset {
  /**
   * Is the equalizer on or off. When this value changes the preferred preset for a piece of media
   * may change.
   */
  val eqMode: StateFlow<EqMode>

  /**
   * Indicate where audio is being routed. When this value changes the preferred preset for a piece
   * of media may change
   */
  val outputRoute: StateFlow<AudioOutputRoute>

  /**
   * EqPreset equals is identity. So when editing preset and setting that preset, ensure the preset
   * is a copy so that this flow will emit.
   */
  val currentPreset: StateFlow<EqPreset>

  /**
   * Returns the preferred preset based on [mediaId] or [albumId] or AudioOutputRoute. If a
   * preferred is not found based on those criteria, a default is returned. If [eqMode] is
   * [EqMode.Off] an preset representing "none" is returned. [currentPreset] is also updated to the
   * returned result.
   */
  suspend fun getPreferred(mediaId: MediaId, albumId: AlbumId): EqPreset

  /** Get all preset id/name pairs, typically to display to the user */
  suspend fun getAll(): List<EqPresetInfo>

  /** Set the [currentPreset] to the EqPreset with [id] */
  suspend fun setCurrent(id: EqPresetId)

  /**
   * This is used when a user preset is being edited. In this case, ensure each [preset[] is a copy
   * so the [currentPreset] will emit a new value. Also, the eq editor should probably suppress
   * changes by detecting any change and setting it back to it's copy. This can happen when
   * media changes during playback.
   */
  fun setCurrent(preset: EqPreset)

  companion object {
    operator fun invoke(
      factory: EqPresetFactory,
      eqMode: StateFlow<EqMode>,
      outputRoute: StateFlow<AudioOutputRoute>
    ): ActiveEqPreset = ActiveEqPresetImpl(factory, eqMode, outputRoute)
  }
}

private class ActiveEqPresetImpl(
  private val factory: EqPresetFactory,
  override val eqMode: StateFlow<EqMode>,
  override val outputRoute: StateFlow<AudioOutputRoute>
) : ActiveEqPreset {
  private val nonePreset by lazy { factory.nonePreset }
  override val currentPreset by lazy { MutableStateFlow(factory.nonePreset) }

  private fun emitPreset(eqPreset: EqPreset) {
    LOG._e { it("emitPreset cur=%s new=%s", currentPreset.value, eqPreset) }
    currentPreset.value = eqPreset
  }

  override suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId
  ): EqPreset = if (eqMode.value.isOff()) nonePreset else
    when (val result = factory.getPreferred(mediaId, albumId, outputRoute.value)) {
      is Ok -> result.value
      is Err -> {
        LOG.e { it("Error getPreferred $mediaId $albumId, map to $nonePreset") }
        nonePreset
      }
    }.also { emitPreset(it) }

  override suspend fun getAll(): List<EqPresetInfo> {
    return factory.getAllPresets()
  }

  override suspend fun setCurrent(id: EqPresetId) {
    if (eqMode.value.isOn()) {
      when (val result = factory.getPreset(id)) {
        is Ok -> emitPreset(result.value)
        is Err -> LOG.e { it("Error setting current preset. ${result.error}") }
      }
    }
  }

  override fun setCurrent(preset: EqPreset) {
    emitPreset(preset)
  }
}

object NullActiveEqPreset : ActiveEqPreset {
  override val eqMode = MutableStateFlow(EqMode.Off)
  override val outputRoute = MutableStateFlow(AudioOutputRoute.Speaker)
  override val currentPreset = MutableStateFlow(EqPreset.NONE)
  override suspend fun getPreferred(mediaId: MediaId, albumId: AlbumId): EqPreset = EqPreset.NONE
  override suspend fun getAll(): List<EqPresetInfo> = emptyList()
  override suspend fun setCurrent(id: EqPresetId) = Unit
  override fun setCurrent(preset: EqPreset) = Unit
}
