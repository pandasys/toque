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

package com.ealva.toque.test.db

import android.content.Context
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.db.EqPresetAssociationDao
import com.ealva.toque.db.EqPresetAssociationTable
import com.ealva.toque.db.PresetAssociation
import com.ealva.toque.persist.asAlbumId
import com.ealva.toque.persist.asMediaId
import com.ealva.toque.test.shared.withTestDatabase
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrElse
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

object CommonPresetAssocDaoTest {
  suspend fun testSetAsDefault(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      val preset = EqPresetStub(EqPresetId(1)).apply { name = "NewDefault" }
      dao.setAsDefault(preset)
      val result = dao.getAssociationsFor(preset)
      expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
      val list = result getOrElse { emptyList() }
      expect(list).toHaveSize(1)
      expect(list[0]).toBe(PresetAssociation.DEFAULT)
    }
  }

  suspend fun testReplaceDefault(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      val firstPreset = EqPresetStub(EqPresetId(1)).apply { name = "FirstPreset" }
      val lastPreset = EqPresetStub(EqPresetId(2)).apply { name = "SecondPreset" }
      dao.setAsDefault(firstPreset)
      dao.setAsDefault(lastPreset)
      val firstResult = dao.getAssociationsFor(firstPreset)
      expect(firstResult).toBeInstanceOf<Ok<List<PresetAssociation>>>()
      val firstList = firstResult.get()
      expect(firstList).toHaveSize(0)

      val lastResult = dao.getAssociationsFor(lastPreset)
      expect(lastResult).toBeInstanceOf<Ok<List<PresetAssociation>>>()
      val lastList = lastResult.getOrElse { emptyList() }
      expect(lastList).toHaveSize(1)
      expect(lastList[0]).toBe(PresetAssociation.DEFAULT)
    }
  }

  suspend fun testMakeAssociations(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      val preset = EqPresetStub(EqPresetId(1)).apply { name = "NewDefault" }

      val mediaId = 100L.asMediaId
      val albumId = 10L.asAlbumId
      val output = AudioOutputRoute.Speaker

      val assocs = listOf(
        PresetAssociation.makeForAlbum(albumId),
        PresetAssociation.makeForOutput(output),
        PresetAssociation.makeForMedia(mediaId)
      )
      expect(dao.makeAssociations(preset, assocs)).toBe(Ok(true))

      val result = dao.getAssociationsFor(preset)
      expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
      val list = result.getOr(emptyList())
      expect(list).toHaveSize(3)
      expect(list[0]).toBe(PresetAssociation.makeForMedia(mediaId))
      expect(list[1]).toBe(PresetAssociation.makeForAlbum(albumId))
      expect(list[2]).toBe(PresetAssociation.makeForOutput(output))
    }
  }

  suspend fun testReplaceAssociations(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      val preset1 = EqPresetStub(EqPresetId(1)).apply { name = "Preset1" }

      val mediaId1 = 100L.asMediaId
      val albumId1 = 10L.asAlbumId
      val output1 = AudioOutputRoute.Speaker

      val firstAssocs = listOf(
        PresetAssociation.makeForAlbum(albumId1),
        PresetAssociation.makeForOutput(output1),
        PresetAssociation.makeForMedia(mediaId1),
        PresetAssociation.DEFAULT
      )
      expect(dao.makeAssociations(preset1, firstAssocs)).toBe(Ok(true))

      dao.getAssociationsFor(preset1).let { result ->
        expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
        val list = result.getOr(emptyList())
        expect(list).toHaveSize(4)
        expect(list[0]).toBe(PresetAssociation.makeForMedia(mediaId1))
        expect(list[1]).toBe(PresetAssociation.makeForAlbum(albumId1))
        expect(list[2]).toBe(PresetAssociation.makeForOutput(output1))
      }

      val preset2 = EqPresetStub(EqPresetId(2)).apply { name = "Preset2" }
      dao.setAsDefault(preset2)

      val mediaId2 = 500L.asMediaId
      val albumId2 = 50L.asAlbumId
      val output2 = AudioOutputRoute.Bluetooth
      val secondAssocs = listOf(
        PresetAssociation.makeForOutput(output2),
        PresetAssociation.makeForAlbum(albumId2),
        PresetAssociation.makeForMedia(mediaId2)
      )
      expect(dao.makeAssociations(preset1, secondAssocs)).toBe(Ok(true))
      dao.getAssociationsFor(preset1).let { result ->
        expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
        val list = result.getOr(emptyList())
        expect(list).toHaveSize(3)
        expect(list[0]).toBe(PresetAssociation.makeForMedia(mediaId2))
        expect(list[1]).toBe(PresetAssociation.makeForAlbum(albumId2))
        expect(list[2]).toBe(PresetAssociation.makeForOutput(output2))
      }

      dao.getAssociationsFor(preset2).let { result ->
        expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
        val list = result.getOr(emptyList())
        expect(list).toHaveSize(1)
        expect(list[0]).toBe(PresetAssociation.DEFAULT)
      }
    }
  }

  suspend fun testGetPreferredIdDefault(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      dao.getPreferredId(
        10L.asMediaId,
        11L.asAlbumId,
        AudioOutputRoute.Speaker,
        EqPresetId(-1)
      ).let { result ->
        expect(result).toBeInstanceOf<Ok<Long>>()
        expect(result.get()).toBe(EqPresetId(-1))
      }
    }
  }

  suspend fun testGetPreferredId(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetAssociationTable), testDispatcher) {
      val dao = EqPresetAssociationDao(this)
      val preset = EqPresetStub(EqPresetId(1000)).apply { name = "A Preset" }

      val mediaId = 100L.asMediaId
      val albumId = 10L.asAlbumId
      val output = AudioOutputRoute.Speaker

      val assocs = listOf(
        PresetAssociation.makeForAlbum(albumId),
        PresetAssociation.makeForOutput(output),
        PresetAssociation.makeForMedia(mediaId)
      )
      expect(dao.makeAssociations(preset, assocs)).toBe(Ok(true))

      val result = dao.getAssociationsFor(preset)
      expect(result).toBeInstanceOf<Ok<List<PresetAssociation>>>()
      val list = result.getOr(emptyList())
      expect(list).toHaveSize(3)
      expect(list[0]).toBe(PresetAssociation.makeForMedia(mediaId))
      expect(list[1]).toBe(PresetAssociation.makeForAlbum(albumId))
      expect(list[2]).toBe(PresetAssociation.makeForOutput(output))
    }
  }
}
