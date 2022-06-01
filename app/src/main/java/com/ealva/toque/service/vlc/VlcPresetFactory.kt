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

package com.ealva.toque.service.vlc

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.getOrThrow
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.EqPresetAssociationDao
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.db.EqPresetData
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.EqPresetFactory.Companion.DEFAULT_SYSTEM_PRESET_NAME
import com.ealva.toque.service.vlc.VlcEqPreset.Companion.setNativeEqValues
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.MediaPlayer

private val BandData.asPreAmpAndBands: EqPresetDao.PreAmpAndBands
  get() = EqPresetDao.PreAmpAndBands(preAmp, bandValues.toTypedArray())

private val EqPresetData.asBandData: BandData
  get() = BandData(preAmpAndBands.preAmp, preAmpAndBands.bands.toList())

private val LOG by lazyLogger(VlcPresetFactory::class)

class VlcPresetFactory(
  private val eqPresetDao: EqPresetDao,
  private val eqPresetAssocDao: EqPresetAssociationDao
) : EqPresetFactory {
  override suspend fun allPresets(): StateFlow<List<EqPreset>> {
    cacheAllPresets()
    updateAllPresetsFlow()
    return allPresetsFlow.asStateFlow()
  }

  private suspend fun cacheAllPresets(): List<EqPreset> {
    return buildList {
      addAll(
        (0 until systemPresetCount)
          .mapNotNull { index -> systemPresetOrNull(EqPresetId(index)) }
      )

      addAll(
        eqPresetDao.getAllUserPresets()
          .onFailure { cause -> LOG.e(cause) { it("Error getting user presets") } }
          .getOrElse { emptyList() }
          .mapNotNull { eqPresetIdName -> userPresetOrNull(eqPresetIdName.id) }
      )
    }.sortedBy { preset -> preset.name }
  }

  /**
   * Create or get a cached preset based on ID. IDs up to [systemPresetCount] indicate a system
   * preset, which are not editable. ID numbers greater than that indicate user created preset
   * which may be edited and persisted. User preset IDs start at a high number, currently
   * 1000, so they should never clash with system IDs.
   */
  override suspend fun getPreset(id: EqPresetId): DaoResult<EqPreset> = runSuspendCatching {
    if (id.value in 0..systemPresetCount) getSystemPreset(id) else getUserPreset(id)
  }

  private suspend fun getUserPreset(id: EqPresetId): EqPreset = getOrPut(id) {
    eqPresetDao.getPresetData(id)
      .map { data ->
        VlcEqPreset(
          nativeEq = MediaPlayer.Equalizer.create().setNativeEqValues(data.asBandData),
          name = data.name,
          isSystemPreset = false,
          id = data.id,
          eqPresetDao = eqPresetDao
        )
      }.getOrThrow()
  }

  private suspend fun userPresetOrNull(presetId: EqPresetId): EqPreset? = try {
    getUserPreset(presetId)
  } catch (e: Exception) {
    LOG.e(e) { it("Error getting user preset %s", presetId) }
    null
  }

  private fun getSystemPreset(id: EqPresetId): EqPreset = getOrPut(id) {
    VlcEqPreset(
      nativeEq = MediaPlayer.Equalizer.createFromPreset(id.value.toInt()),
      name = MediaPlayer.Equalizer.getPresetName(id.value.toInt()),
      isSystemPreset = true,
      id = id,
      eqPresetDao = eqPresetDao
    )
  }

  private fun systemPresetOrNull(presetId: EqPresetId): EqPreset? = try {
    getSystemPreset(presetId)
  } catch (e: Exception) {
    LOG.e(e) { it("Error getting system preset %s", presetId) }
    null
  }

  override suspend fun makeFrom(eqPreset: EqPreset, name: String): DaoResult<EqPreset> {
    return eqPreset.takeIf { preset -> preset.isValid }?.let {
      makeUserPreset(name, eqPreset.getAllValues())
    } ?: Err(IllegalArgumentException("Preset ${eqPreset.name} is not valid"))
  }

  private suspend fun makeUserPreset(
    name: String,
    data: BandData
  ): Result<EqPreset, Throwable> = eqPresetDao.insertPreset(name, data.asPreAmpAndBands)
    .map { presetId -> makePresetFromData(presetId, name, data) }
    .onSuccess { preset -> put(preset.id, preset) }

  private fun makePresetFromData(
    presetId: EqPresetId,
    name: String,
    data: BandData
  ) = VlcEqPreset(
    MediaPlayer.Equalizer.create().apply { setNativeEqValues(data) },
    name,
    false,
    presetId,
    eqPresetDao
  )

  override suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    outputRoute: AudioOutputRoute
  ): DaoResult<EqPreset> = doGetPreferred(mediaId, albumId, outputRoute)

  private suspend fun doGetPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute
  ): DaoResult<EqPreset> = eqPresetAssocDao
    .getPreferredId(mediaId, albumId, route, defaultId)
    .map { id -> getPreset(id) }
    .onFailure { cause -> LOG.e(cause) { it("Error getting preferred preset") } }
    .getOrElse { getPreset(defaultId) }

  override val nonePreset: EqPreset
    get() = VlcEqPreset.NONE

  /**
   * There will likely be a very limited number of presets active in normal use, so we will keep
   * this very simple and not expire anything from the cache. Even using all available system and
   * a lot of user created presets shouldn't be that large.
   */
  private val presetCache = mutableMapOf<EqPresetId, EqPreset>()
  private val allPresetsFlow = MutableStateFlow<List<EqPreset>>(emptyList())

  private fun updateAllPresetsFlow() {
    allPresetsFlow.value = presetCache
      .map { entry -> entry.value }
      .sortedBy { eqPreset -> eqPreset.name }
  }

  private inline fun getOrPut(id: EqPresetId, defaultValue: () -> EqPreset): EqPreset =
    presetCache.getOrPut(id, defaultValue).also { updateAllPresetsFlow() }

  private fun put(id: EqPresetId, preset: EqPreset) {
    presetCache[id] = preset
    updateAllPresetsFlow()
  }

  private val systemPresetCount: Int
    get() = MediaPlayer.Equalizer.getPresetCount()

  /** Try to set the default to "Flat" system preset */
  private var systemDefaultId = EqPresetId(-1L)
  private val defaultId: EqPresetId
    get() {
      if (systemDefaultId.value == -1L) {
        for (i in 0 until systemPresetCount) {
          if (nameOfSystemPreset(i).equals(DEFAULT_SYSTEM_PRESET_NAME, ignoreCase = true))
            systemDefaultId = EqPresetId(i)
        }
        if (systemDefaultId.value == -1L) systemDefaultId = EqPresetId(0)
      }
      return systemDefaultId
    }

  private fun nameOfSystemPreset(index: Int) = MediaPlayer.Equalizer.getPresetName(index)
}
