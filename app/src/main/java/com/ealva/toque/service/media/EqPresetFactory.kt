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

package com.ealva.toque.service.media

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.fetch
import com.ealva.toque.db.BoolResult
import com.ealva.toque.db.DaoResult
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import kotlinx.coroutines.flow.StateFlow

interface EqPresetFactory {
  enum class EqPresetAssociation(@StringRes private val titleRes: Int) {
    Song(R.string.Song),
    Album(R.string.Album),
    Speaker(R.string.Speaker),
    WiredHeadset(R.string.WiredConnection),
    Bluetooth(R.string.Bluetooth),
    Default(R.string.Default);

    override fun toString(): String = fetch(titleRes)
  }

  /**
   * Setting this preset turns off equalization.
   */
  val nonePreset: EqPreset

  suspend fun allPresets(): StateFlow<List<EqPreset>>

  /**
   * Get an EqPreset based on the [id]. Result is either a system or user preset, based on the [id]
   */
  suspend fun getPreset(id: EqPresetId): DaoResult<EqPreset>

  /**
   * Make a new user [EqPreset] from [eqPreset] with [name].
   */
  suspend fun makeFrom(eqPreset: EqPreset, name: String): DaoResult<EqPreset>

  /**
   * Get the "preferred" [EqPreset] based on one of [mediaId], [albumId], or current [outputRoute],
   * in that order. If no match is found, the "default" preset is returned. The default is typically
   * the system "Flat preset.
   */
  suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    outputRoute: AudioOutputRoute
  ): DaoResult<EqPreset>

  suspend fun defaultPreset(): EqPreset

  suspend fun makeAssociations(
    eqPreset: EqPreset,
    mediaId: MediaId,
    albumId: AlbumId,
    associations: List<EqPresetAssociation>
  ): BoolResult

  suspend fun getAssociations(eqPreset: EqPreset): List<EqPresetAssociation>

  companion object {
    const val DEFAULT_SYSTEM_PRESET_NAME = "Flat"
  }
}
