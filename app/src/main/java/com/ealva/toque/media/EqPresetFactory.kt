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

package com.ealva.toque.media

import com.ealva.toque.db.AlbumId
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.EqPresetInfo
import com.ealva.toque.db.MediaId
import com.github.michaelbull.result.Result

interface EqPresetFactory {

  /**
   * Number of distinct frequency bands for an equalizer
   */
  val bandCount: Int

  val bandIndices: IntRange

  val allPresets: List<EqPresetInfo>

  val systemPresetCount: Int

  suspend fun getPreset(presetId: Long): Result<EqPreset, DaoMessage>

  suspend fun makeFrom(eqPreset: EqPreset, name: String): Result<EqPreset, DaoMessage>

  suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId
  ): Result<EqPreset, DaoMessage>

  /**
   * Setting this preset turns off equalization.
   */
  val nonePreset: EqPreset

  companion object {
    const val DEFAULT_SYSTEM_PRESET_NAME = "Flat"
  }
}
