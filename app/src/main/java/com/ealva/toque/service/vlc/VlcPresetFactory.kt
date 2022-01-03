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
import com.ealva.toque.db.EqPresetIdName
import com.ealva.toque.db.NullEqPresetDao
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.EqPresetFactory.Companion.DEFAULT_SYSTEM_PRESET_NAME
import com.ealva.toque.service.vlc.VlcEqPreset.Companion.setNativeEqValues
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import org.videolan.libvlc.MediaPlayer

private val LOG by lazyLogger(VlcPresetFactory::class)

/**
 * There will likely be a very limited number of presets active in normal use, so we will keep this
 * very simple and not expire anything from the cache. Even using all available system and a lot of
 * user created presets shouldn't be that large.
 */
private val presetCache = mutableMapOf<EqPresetId, EqPreset>()

class VlcPresetFactory(
  private val eqPresetDao: EqPresetDao,
  private val eqPresetAssocDao: EqPresetAssociationDao
) : EqPresetFactory {
  override val bandCount: Int
    get() = MediaPlayer.Equalizer.getBandCount()
  override val bandIndices: IntRange by lazy { 0 until bandCount }
  override val systemPresetCount: Int
    get() = presetCount

  override suspend fun getAllPresets(): List<EqPresetIdName> {
    TODO("Not yet implemented")
  }

  /**
   * Create or get a cached preset based on ID. IDs up to [systemPresetCount] indicate a system
   * preset, which are not editable. ID numbers greater than that indicate user created preset
   * which may be edited and persisted. User preset IDs start at a high number, currently
   * 1000, so they should never clash with system IDs.
   */
  override suspend fun getPreset(id: EqPresetId): DaoResult<EqPreset> = runSuspendCatching {
    if (id.value in 0..systemPresetCount) {
      presetCache.getOrPut(id) {
        VlcEqPreset(
          MediaPlayer.Equalizer.createFromPreset(id.value.toInt()),
          MediaPlayer.Equalizer.getPresetName(id.value.toInt()),
          true,
          id,
          NullEqPresetDao
        )
      }
    } else {
      presetCache.getOrPut(id) {
        eqPresetDao.getPresetData(id)
          .map { data ->
            VlcEqPreset(
              MediaPlayer.Equalizer.create().setNativeEqValues(data.preAmpAndBands),
              data.name,
              false,
              data.id,
              eqPresetDao
            )
          }.getOrThrow()
      }
    }
  }

  override suspend fun makeFrom(eqPreset: EqPreset, name: String): DaoResult<EqPreset> {
    val preAmpAndBands = eqPreset.getAllValues()
    return eqPresetDao.insertPreset(name, preAmpAndBands)
      .map { presetId ->
        VlcEqPreset(
          MediaPlayer.Equalizer.create().apply { setNativeEqValues(preAmpAndBands) },
          name,
          false,
          presetId,
          eqPresetDao
        )
      }
  }

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

  companion object {
    private val presetCount: Int
      get() = MediaPlayer.Equalizer.getPresetCount()

    /** Try to set the default to "Flat" system preset */
    private var systemDefaultId = EqPresetId(-1L)
    private val defaultId: EqPresetId
      get() {
        if (systemDefaultId.value == -1L) {
          for (i in 0 until presetCount) {
            if (nameOf(i).equals(DEFAULT_SYSTEM_PRESET_NAME, ignoreCase = true))
              systemDefaultId = EqPresetId(i)
          }
          if (systemDefaultId.value == -1L) systemDefaultId = EqPresetId(0)
        }
        return systemDefaultId
      }

    private fun nameOf(i: Int) = MediaPlayer.Equalizer.getPresetName(i)
  }
}
