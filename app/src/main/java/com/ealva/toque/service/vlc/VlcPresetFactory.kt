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
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audio.AudioOutputRoute
import com.ealva.toque.audio.AudioOutputState
import com.ealva.toque.db.AlbumId
import com.ealva.toque.db.DaoExceptionMessage
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.EqPresetAssociationDao
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.db.EqPresetInfo
import com.ealva.toque.db.MediaId
import com.ealva.toque.db.NullEqPresetDao
import com.ealva.toque.db.getErrorString
import com.ealva.toque.media.EqPreset
import com.ealva.toque.media.EqPresetFactory
import com.ealva.toque.media.EqPresetFactory.Companion.DEFAULT_SYSTEM_PRESET_NAME
import com.ealva.toque.service.vlc.VlcEqPreset.Companion.NONE
import com.ealva.toque.service.vlc.VlcEqPreset.Companion.setNativeEqValues
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import org.videolan.libvlc.MediaPlayer

private val LOG by lazyLogger(VlcPresetFactory::class)

class VlcPresetFactory(
  private val appCtx: Context,
  private val eqPresetDao: EqPresetDao,
  private val eqPresetAssocDao: EqPresetAssociationDao,
  private val audioOutputState: AudioOutputState
) : EqPresetFactory, EqPresetSelector {
  override val bandCount: Int = MediaPlayer.Equalizer.getBandCount()
  override val bandIndices: IntRange = 0 until bandCount
  override val allPresets: List<EqPresetInfo>
    get() = TODO("Not yet implemented")
  override val systemPresetCount: Int = MediaPlayer.Equalizer.getPresetCount()

  override suspend fun getPreset(presetId: Long): Result<EqPreset, DaoMessage> =
    runCatching {
      if (presetId in 0..systemPresetCount) {
        VlcEqPreset(
          MediaPlayer.Equalizer.createFromPreset(presetId.toInt()),
          MediaPlayer.Equalizer.getPresetName(presetId.toInt()),
          true,
          presetId,
          NullEqPresetDao
        )
      } else {
        when (val result = eqPresetDao.getPresetData(presetId)) {
          is Ok -> {
            val data = result.value
            VlcEqPreset(
              MediaPlayer.Equalizer.create().setNativeEqValues(data.preAmpAndBands),
              data.name,
              false,
              data.id,
              eqPresetDao
            )
          }
          is Err -> throw NoSuchElementException("PresetId:$presetId")
        }
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun makeFrom(eqPreset: EqPreset, name: String): Result<EqPreset, DaoMessage> {
    val preAmpAndBands = eqPreset.getAllValues()
    return when (val result = eqPresetDao.insertPreset(name, preAmpAndBands)) {
      is Ok -> {
        runCatching {
          VlcEqPreset(
            MediaPlayer.Equalizer.create().apply { setNativeEqValues(preAmpAndBands) },
            name,
            false,
            result.value,
            eqPresetDao
          )
        }.mapError { DaoExceptionMessage(it) }
      }
      is Err -> Err(result.error)
    }
  }

  override suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId
  ): Result<EqPreset, DaoMessage> = doGetPreferred(mediaId, albumId, audioOutputState.output)

  private suspend fun doGetPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute
  ): Result<EqPreset, DaoMessage> = when (
    val result = eqPresetAssocDao.getPreferredId(mediaId, albumId, route) { defaultPresetId }
  ) {
    is Ok -> getPreset(result.value)
    is Err -> {
      LOG.e { it("getPreferred ${result.getErrorString(appCtx)}") }
      getPreset(defaultPresetId)
    }
  }

  override suspend fun getPreferredEqPreset(mediaId: MediaId, albumId: AlbumId): VlcEqPreset {
    return when (val result = getPreferred(mediaId, albumId)) {
      is Ok -> result.value as VlcEqPreset
      is Err -> NONE
    }
  }

  override val nonePreset: EqPreset
    get() = NONE

  companion object {
    private var defaultPresetId: Long = 0L

    init {
      for (i in 0 until MediaPlayer.Equalizer.getPresetCount()) {
        if (MediaPlayer.Equalizer.getPresetName(i) == DEFAULT_SYSTEM_PRESET_NAME)
          defaultPresetId = i.toLong()
      }
    }
  }
}
