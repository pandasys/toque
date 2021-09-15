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

package com.ealva.toque.test.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.ealva.toque.common.Amp
import com.ealva.toque.db.DaoExceptionMessage
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.DaoNotFound
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.db.EqPresetData
import com.ealva.toque.db.EqPresetTable
import com.ealva.toque.service.media.PreAmpAndBands
import com.ealva.toque.test.shared.withTestDatabase
import com.ealva.welite.db.expr.eq
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

object CommonPresetDaoTest {
  suspend fun testEstablishMinRowId(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        val expectedFirstId = EqPresetDao.MIN_USER_PRESET_ID
        val expectedSecondId = expectedFirstId + 1
        val expectedThirdId = expectedSecondId + 1

        EqPresetDao.establishMinimumRowId(this)
        val myPresetName = "MyPreset"
        expect(EqPresetTable.insert { it[presetName] = myPresetName })
          .toBe(expectedFirstId)
        expect(EqPresetTable.delete { presetName eq myPresetName }).toBe(1)
        expect(EqPresetTable.insert { it[presetName] = myPresetName })
          .toBe(expectedSecondId)
        expect(EqPresetTable.delete { presetName eq myPresetName }).toBe(1)

        // call again to ensure calling multiple times does no harm
        EqPresetDao.establishMinimumRowId(this)
        expect(EqPresetTable.insert { it[presetName] = myPresetName })
          .toBe(expectedThirdId)
      }
    }
  }

  suspend fun testUpdateMissingPreset(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      val dao = EqPresetDao(this)
      val presetData = EqPresetData(
        id = 1000,
        name = "NotThere",
        preAmpAndBands = PreAmpAndBands(
          Amp(10F),
          Array(EqPresetDao.BAND_COUNT) { Amp(0F) }
        )
      )
      val result = dao.updatePreset(presetData)
      expect(result).toBeInstanceOf<Err<DaoMessage>>()
    }
  }

  suspend fun testInsertPreset(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp(12F)
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      val insertResult = dao.insertPreset(
        name,
        preAmpAndBands
      )
      val id = insertResult.get() ?: -1
      expect(id).toBe(EqPresetDao.MIN_USER_PRESET_ID)
      dao.getPresetData(id).let { result ->
        expect(result).toBeInstanceOf<Ok<EqPresetData>>()
        result.get()?.let { presetData ->
          expect(presetData.preAmpAndBands.preAmp).toBe(initialPreAmp)
          expect(presetData.preAmpAndBands.bands contentEquals preAmpAndBands.bands).toBe(true)
        } ?: error("unexpected null")
      }
    }
  }

  suspend fun testInsertDuplicatePreset(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp(12F)
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      dao.insertPreset(name, preAmpAndBands).let { result ->
        val id = result.get() ?: -1
        expect(id).toBe(EqPresetDao.MIN_USER_PRESET_ID)
        dao.getPresetData(id).let { dataResult ->
          expect(dataResult).toBeInstanceOf<Ok<EqPresetData>>()
          dataResult.get()?.let { presetData ->
            expect(presetData.preAmpAndBands.preAmp).toBe(initialPreAmp)
            expect(presetData.preAmpAndBands.bands contentEquals preAmpAndBands.bands).toBe(true)
          } ?: error("unexpected null")
        }
      }
      dao.insertPreset(name, preAmpAndBands).let { result ->
        expect(result).toBeInstanceOf<Err<DaoMessage>>()
        result.getError()?.let { error ->
          expect(error).toBeInstanceOf<DaoExceptionMessage>()
          error as DaoExceptionMessage
          expect(error.ex).toBeInstanceOf<SQLiteConstraintException>()
        } ?: error("unexpected null error")
      }
    }
  }

  suspend fun testUpdatePreset(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp(12F)
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      var id: Long
      dao.insertPreset(name, preAmpAndBands).let { result ->
        id = result.get() ?: -1
        expect(id).toBe(EqPresetDao.MIN_USER_PRESET_ID)
        dao.getPresetData(id).let { dataResult ->
          expect(dataResult).toBeInstanceOf<Ok<EqPresetData>>()
          dataResult.get()?.let { presetData ->
            expect(presetData.preAmpAndBands.preAmp).toBe(initialPreAmp)
            expect(presetData.preAmpAndBands.bands contentEquals preAmpAndBands.bands).toBe(true)
          } ?: error("unexpected null")
        }
      }

      val updatedPreAmp = Amp(11.5F)
      val updatedBands = Array(preAmpAndBands.bands.size) { index ->
        preAmpAndBands.bands[index] + 1F
      }
      val data = EqPresetData(id, name, PreAmpAndBands(updatedPreAmp, updatedBands))
      dao.updatePreset(data).let { result ->
        expect(result).toBeInstanceOf<Ok<Boolean>>()
      }
      dao.getPresetData(id).let { dataResult ->
        expect(dataResult).toBeInstanceOf<Ok<EqPresetData>>()
        dataResult.get()?.let { presetData ->
          expect(presetData.preAmpAndBands.preAmp).toBe(updatedPreAmp)
          expect(presetData.preAmpAndBands.bands contentEquals updatedBands).toBe(true)
        } ?: error("unexpected null")
      }
    }
  }

  suspend fun testUpdatePreAmp(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp(12F)
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      val insertResult = dao.insertPreset(
        name,
        preAmpAndBands
      )
      val id = insertResult.get() ?: -1
      val updatedPreAmp = Amp(0)
      val result = dao.updatePreAmp(id, updatedPreAmp)
      expect(result).toBe(Ok(true))
      val data = dao.getPresetData(id).get() ?: error("unexpected null data")
      expect(data.preAmpAndBands.preAmp).toBe(updatedPreAmp)
    }
  }

  suspend fun testUpdateBand(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp(12F)
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      val id = dao.insertPreset(name, preAmpAndBands).get() ?: -1
      expect(id).toBe(EqPresetDao.MIN_USER_PRESET_ID)
      val updatedBands = Array(preAmpAndBands.bands.size) { index ->
        preAmpAndBands.bands[index] + 1F
      }
      updatedBands.forEachIndexed { index, value ->
        dao.updateBand(id, index, value)
      }
      dao.getPresetData(id).get()?.let { data ->
        data.preAmpAndBands.bands.forEachIndexed { index, value ->
          expect(value).toNotBeTheSameAs(preAmpAndBands.bands[index])
          expect(value).toBe(updatedBands[index])
        }
      } ?: error("unexpected null data")
    }
  }

  suspend fun testDeletePreset(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(EqPresetTable), testDispatcher) {
      transaction {
        EqPresetDao.establishMinimumRowId(this)
      }
      val dao = EqPresetDao(this)
      val name = "MyPreset"
      val initialPreAmp = Amp.DEFAULT_PREAMP
      val preAmpAndBands = PreAmpAndBands(
        initialPreAmp,
        Array(EqPresetDao.BAND_COUNT) { index -> Amp(index) }
      )
      val id = dao.insertPreset(name, preAmpAndBands).get() ?: -1
      expect(id).toBe(EqPresetDao.MIN_USER_PRESET_ID)

      dao.deletePreset(id).let { result ->
        expect(result).toBeInstanceOf<Ok<Boolean>>()
        expect(result.get()).toBe(true)
      }

      dao.deletePreset(id).let { result ->
        expect(result).toBeInstanceOf<Err<DaoNotFound>>()
      }
    }
  }
}
