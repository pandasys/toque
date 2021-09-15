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

package com.ealva.toque.db

import com.ealva.toque.common.Amp
import com.ealva.toque.service.media.PreAmpAndBands
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.SQLiteSequence
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching

interface EqPresetDao {
  /**
   * Insert the preset name and all preset data. Returns the preset ID if successful
   */
  suspend fun insertPreset(name: String, preAmpAndBands: PreAmpAndBands): LongResult

  /**
   * Get all the data for [presetId]
   */
  suspend fun getPresetData(presetId: Long): Result<EqPresetData, DaoMessage>

  /**
   * Get all user defined presets
   */
  suspend fun getAllUserPresets(): Result<List<EqPresetInfo>, DaoMessage>

  /**
   * Update all the preset data the preset with id=```presetData.id```. [duringTxn] is called
   * if the operation was successful and within the transaction boundary. If [duringTxn] throws
   * an exception the transaction is rolled back
   */
  suspend fun updatePreset(presetData: EqPresetData, duringTxn: () -> Unit = {}): BoolResult

  /**
   * Update [presetId]'s PreAmp [amplitude]
   */
  suspend fun updatePreAmp(presetId: Long, amplitude: Amp): BoolResult

  /**
   * Update [presetId]'s band [amplitude] at band column [index]
   */
  suspend fun updateBand(presetId: Long, index: Int, amplitude: Amp): BoolResult

  suspend fun deletePreset(presetId: Long): BoolResult

  companion object {
    operator fun invoke(db: Database): EqPresetDao = EqPresetDaoImpl(db)

    const val MIN_USER_PRESET_ID = 1000L
    val BAND_COUNT = EqPresetTable.bandColumns.size

    /**
     * Establishes a minimum row id in the EqPresetTable so user preset IDs do not collide with
     * system preset IDs. If a row id does not exist, inserts a dummy entry into the EqPreset table,
     * establishes the minimum row id, then deletes the dummy record.
     *
     * The [EqPresetTable] must have been created before calling this function. Expected to be
     * called when the DB is created or opened. Call must be idempotent so it can be called from
     * DB open
     */
    fun establishMinimumRowId(txn: TransactionInProgress) = txn.run {
      check(EqPresetTable.exists) { "EqPresetTable must have been created" }
      val tableName = SQLiteSequence
        .select { name }
        .where { name eq EqPresetTable.tableName }
        .sequence { it[name] }
        .singleOrNull()
      if (tableName == null) {
        val dummyName = "DummyDeleteMeDummy"
        val id = EqPresetTable.insert { it[presetName] = dummyName }
        try {
          if (id < MIN_USER_PRESET_ID) { // should always be true if tableName was null
            SQLiteSequence
              .updateColumns { it[seq] = (MIN_USER_PRESET_ID - 1) }
              .where { name eq EqPresetTable.tableName }
              .update()
          }
        } finally {
          EqPresetTable.delete { presetName eq dummyName }
        }
      }
    }
  }
}

private class EqPresetDaoImpl(
  private val db: Database
) : EqPresetDao {
  override suspend fun insertPreset(
    name: String,
    preAmpAndBands: PreAmpAndBands
  ): LongResult = db.transaction {
    runCatching { doInsertPreset(name, preAmpAndBands) }
      .mapError { DaoExceptionMessage(it) }
      .andThen { if (it > 0) Ok(it) else Err(DaoFailedToInsert("$name $preAmpAndBands")) }
  }

  @Suppress("MagicNumber")
  private fun TransactionInProgress.doInsertPreset(
    name: String,
    preAmpAndBands: PreAmpAndBands
  ): Long = INSERT_PRESET.insert {
    it[presetName] = name
    it[updatedTime] = System.currentTimeMillis()
    it[preAmp] = preAmpAndBands.preAmp()
    with(preAmpAndBands) {
      it[band0] = bands[0]()
      it[band1] = bands[1]()
      it[band2] = bands[2]()
      it[band3] = bands[3]()
      it[band4] = bands[4]()
      it[band5] = bands[5]()
      it[band6] = bands[6]()
      it[band7] = bands[7]()
      it[band8] = bands[8]()
      it[band9] = bands[9]()
    }
  }

  override suspend fun getPresetData(
    presetId: Long
  ): Result<EqPresetData, DaoMessage> = db.query {
    runCatching { doGetPreset(presetId) }
      .mapError { DaoExceptionMessage(it) }
      .andThen { it?.let { data -> Ok(data) } ?: Err(DaoNotFound("Preset ID:$presetId")) }
  }

  private fun Queryable.doGetPreset(presetId: Long): EqPresetData? =
    EqPresetTable
      .select()
      .where { id eq presetId }
      .sequence { EqPresetData(it[id], it[presetName], dataFromCursor(it)) }
      .singleOrNull()

  private fun EqPresetTable.dataFromCursor(cursor: Cursor) = PreAmpAndBands(
    Amp(cursor[preAmp]),
    Array(bandColumns.size) { index -> Amp(cursor[bandColumns[index]]) }
  )

  override suspend fun getAllUserPresets(): Result<List<EqPresetInfo>, DaoMessage> = db.query {
    runCatching { doGetAll() }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAll(): List<EqPresetInfo> =
    EqPresetTable
      .selectAll()
      .sequence { EqPresetInfo(it[id], it[presetName]) }
      .toList()

  override suspend fun updatePreset(
    presetData: EqPresetData,
    duringTxn: () -> Unit
  ): BoolResult = db.transaction {
    runCatching { doUpdatePreset(presetData).also { if (it > 0) duringTxn() } }
      .mapError { DaoExceptionMessage(it) }
      .andThen { if (it > 0) Ok(true) else selectUpdateError(presetData.id, "$presetData") }
      .onFailure { rollback() }
  }

  @Suppress("MagicNumber")
  private fun TransactionInProgress.doUpdatePreset(presetData: EqPresetData) = UPDATE_ALL.update {
    it[BIND_PRESET_ID] = presetData.id
    it[updatedTime] = System.currentTimeMillis()
    it[preAmp] = presetData.preAmpAndBands.preAmp()
    with(presetData.preAmpAndBands) {
      it[band0] = bands[0]()
      it[band1] = bands[1]()
      it[band2] = bands[2]()
      it[band3] = bands[3]()
      it[band4] = bands[4]()
      it[band5] = bands[5]()
      it[band6] = bands[6]()
      it[band7] = bands[7]()
      it[band8] = bands[8]()
      it[band9] = bands[9]()
    }
  }

  override suspend fun updatePreAmp(
    presetId: Long,
    amplitude: Amp
  ): BoolResult = db.transaction {
    runCatching {
      EqPresetTable
        .updateColumns {
          it[preAmp] = amplitude()
          it[updatedTime] = System.currentTimeMillis()
        }
        .where { id eq presetId }
        .update()
    }.mapError {
      DaoExceptionMessage(it)
    }.andThen {
      if (it > 0) Ok(true) else selectUpdateError(presetId, "PresetId:$presetId preamp:$amplitude")
    }
  }

  override suspend fun updateBand(
    presetId: Long,
    index: Int,
    amplitude: Amp
  ): BoolResult = db.transaction {
    val columnIndices = EqPresetTable.bandColumns.indices
    require(index in columnIndices) { "Index $index not in bounds $columnIndices" }
    runCatching {
      EqPresetTable.updateColumns {
        it[bandColumns[index]] = amplitude()
        it[updatedTime] = System.currentTimeMillis()
      }.where {
        id eq presetId
      }.update()
    }.mapError {
      DaoExceptionMessage(it)
    }.andThen {
      if (it > 0) Ok(true)
      else selectUpdateError(presetId, "PresetId:$presetId band:$index value:$amplitude")
    }
  }

  override suspend fun deletePreset(presetId: Long): BoolResult = db.transaction {
    runCatching {
      EqPresetTable.delete { id eq presetId }
    }.mapError {
      DaoExceptionMessage(it)
    }.andThen {
      if (it > 0) Ok(true)
      else {
        selectErrorIfExists(
          presetId,
          DaoFailedToDelete("PresetId:$presetId"),
          DaoNotFound("PresetId:$presetId")
        )
      }
    }
  }

  private fun TransactionInProgress.selectUpdateError(presetId: Long, msg: String): BoolResult =
    selectErrorIfExists(presetId, DaoNotFound("Preset ID:$presetId"), DaoFailedToUpdate(msg))

  /**
   * These error conditions shouldn't occur in practice so another txn to narrow down this issue
   * is not a big deal
   */
  private fun Queryable.selectErrorIfExists(
    presetId: Long,
    existsMsg: DaoMessage,
    notExistsMsg: DaoMessage
  ): Result<Nothing, DaoMessage> = runCatching { presetExists(presetId) }
    .mapError { DaoExceptionMessage(it) }
    .andThen { exists ->
      if (exists) Err(existsMsg) else Err(notExistsMsg)
    }

  private fun Queryable.presetExists(presetId: Long): Boolean =
    EqPresetTable.select().where { id eq presetId }.count() > 0
}

data class EqPresetInfo(val id: Long, val name: String)

data class EqPresetData(
  val id: Long,
  val name: String,
  val preAmpAndBands: PreAmpAndBands
)

private val BIND_PRESET_ID = EqPresetTable.id.bindArg()
private val UPDATE_ALL = EqPresetTable.updateColumns {
  it[preAmp].bindArg()
  it[band0].bindArg()
  it[band1].bindArg()
  it[band2].bindArg()
  it[band3].bindArg()
  it[band4].bindArg()
  it[band5].bindArg()
  it[band6].bindArg()
  it[band7].bindArg()
  it[band8].bindArg()
  it[band9].bindArg()
  it[updatedTime].bindArg()
}.where {
  id eq BIND_PRESET_ID
}

private val INSERT_PRESET = EqPresetTable.insertValues {
  it[presetName].bindArg()
  it[preAmp].bindArg()
  it[band0].bindArg()
  it[band1].bindArg()
  it[band2].bindArg()
  it[band3].bindArg()
  it[band4].bindArg()
  it[band5].bindArg()
  it[band6].bindArg()
  it[band7].bindArg()
  it[band8].bindArg()
  it[band9].bindArg()
  it[updatedTime].bindArg()
}

object NullEqPresetDao : EqPresetDao {
  override suspend fun insertPreset(
    name: String,
    preAmpAndBands: PreAmpAndBands
  ): LongResult = NotImplemented

  override suspend fun getPresetData(
    presetId: Long
  ): Result<EqPresetData, DaoMessage> = NotImplemented

  override suspend fun getAllUserPresets(): Result<List<EqPresetInfo>, DaoMessage> = NotImplemented
  override suspend fun updatePreset(
    presetData: EqPresetData,
    duringTxn: () -> Unit
  ): BoolResult = NotImplemented

  override suspend fun updatePreAmp(presetId: Long, amplitude: Amp): BoolResult = NotImplemented

  override suspend fun updateBand(
    presetId: Long,
    index: Int,
    amplitude: Amp
  ): BoolResult = NotImplemented

  override suspend fun deletePreset(presetId: Long): BoolResult = NotImplemented

  private val NotImplemented = Err(DaoNotImplemented)
}
