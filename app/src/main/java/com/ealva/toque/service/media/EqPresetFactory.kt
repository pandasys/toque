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

import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.EqPresetIdName
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId

interface EqPresetFactory {

  /**
   * Number of distinct frequency bands for an equalizer
   */
  val bandCount: Int

  val bandIndices: IntRange

  val systemPresetCount: Int

  suspend fun getAllPresets(): List<EqPresetIdName>

  suspend fun getPreset(id: EqPresetId): DaoResult<EqPreset>

  suspend fun makeFrom(eqPreset: EqPreset, name: String): DaoResult<EqPreset>

  suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    outputRoute: AudioOutputRoute
  ): DaoResult<EqPreset>

  /**
   * Setting this preset turns off equalization.
   */
  val nonePreset: EqPreset

  companion object {
    const val DEFAULT_SYSTEM_PRESET_NAME = "Flat"
  }
}
