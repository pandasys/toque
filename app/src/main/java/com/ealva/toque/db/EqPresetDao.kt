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

package com.ealva.toque.db

import android.os.Parcelable
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Millis
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
import com.github.michaelbull.result.coroutines.runSuspendCatching
import kotlinx.parcelize.Parcelize

interface EqPresetDao {
  @Parcelize
  data class PreAmpAndBands(val preAmp: Amp, val bands: Array<Amp>) : Parcelable {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PreAmpAndBands

      if (preAmp != other.preAmp) return false
      if (!bands.contentEquals(other.bands)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = preAmp.hashCode()
      result = 31 * result + bands.contentHashCode()
      return result
    }

    override fun toString(): String {
      return """PreAmpAndBands[$preAmp, ${bands.contentToString()}]"""
    }
  }

  /**
   * Insert the preset name and all preset data. Returns the preset ID if successful
   */
  suspend fun insertPreset(name: String, preAmpAndBands: PreAmpAndBands): DaoResult<EqPresetId>

  /**
   * Get all the data for [id]
   */
  suspend fun getPresetData(id: EqPresetId): DaoResult<EqPresetData>

  /**
   * Get all user defined presets
   */
  suspend fun getAllUserPresets(): DaoResult<List<EqPresetIdName>>

  /**
   * Update all the preset data the preset with id=```presetData.id```. [duringTxn] is called
   * if the operation was successful and within the transaction boundary. If [duringTxn] throws
   * an exception the transaction is rolled back
   */
  suspend fun updatePreset(presetData: EqPresetData, duringTxn: () -> Unit = {}): BoolResult

  /**
   * Update [presetId]'s PreAmp [amplitude]
   */
  suspend fun updatePreAmp(presetId: EqPresetId, amplitude: Amp): BoolResult

  /**
   * Update [presetId]'s band [amplitude] at band column [index]
   */
  suspend fun updateBand(presetId: EqPresetId, index: Int, amplitude: Amp): BoolResult

  suspend fun deletePreset(id: EqPresetId): BoolResult

  companion object {
    operator fun invoke(db: Database): EqPresetDao = EqPresetDaoImpl(db)

    val MIN_USER_PRESET_ID = EqPresetId(1000L)
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
        .sequence { cursor -> cursor[name] }
        .singleOrNull()
      if (tableName == null) {
        val dummyName = "DummyDeleteMeDummy"
        val id = EqPresetTable.insert { it[presetName] = dummyName }
        try {
          if (id < MIN_USER_PRESET_ID.value) { // should always be true if tableName was null
            SQLiteSequence
              .updateColumns { it[seq] = (MIN_USER_PRESET_ID.value - 1) }
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
    preAmpAndBands: EqPresetDao.PreAmpAndBands
  ): DaoResult<EqPresetId> = runSuspendCatching {
    db.transaction {
      EqPresetId(INSERT_PRESET.insert {
        it[presetName] = name
        it[updatedTime] = Millis.currentUtcEpochMillis().value
        it[preAmp] = preAmpAndBands.preAmp.value
        with(preAmpAndBands) {
          it[band0] = bands[0].value
          it[band1] = bands[1].value
          it[band2] = bands[2].value
          it[band3] = bands[3].value
          it[band4] = bands[4].value
          it[band5] = bands[5].value
          it[band6] = bands[6].value
          it[band7] = bands[7].value
          it[band8] = bands[8].value
          it[band9] = bands[9].value
        }
      }).also { if (it.value < 1) throw DaoInsertFailedException("Failed to insert $name") }
    }
  }

  override suspend fun getPresetData(
    id: EqPresetId
  ): DaoResult<EqPresetData> = runSuspendCatching {
    db.query { doGetPreset(id.value) } ?: throw DaoNotFoundException("Could not find $id")
  }

  private fun Queryable.doGetPreset(presetId: Long): EqPresetData? =
    EqPresetTable
      .select()
      .where { id eq presetId }
      .sequence { cursor ->
        EqPresetData(EqPresetId(cursor[id]), cursor[presetName], dataFromCursor(cursor))
      }
      .singleOrNull()

  private fun EqPresetTable.dataFromCursor(cursor: Cursor) = EqPresetDao.PreAmpAndBands(
    Amp(cursor[preAmp]),
    Array(bandColumns.size) { index -> Amp(cursor[bandColumns[index]]) }
  )

  override suspend fun getAllUserPresets(): DaoResult<List<EqPresetIdName>> =
    runSuspendCatching {
      db.query {
        EqPresetTable
          .selectAll()
          .sequence { cursor -> EqPresetIdName(EqPresetId(cursor[id]), cursor[presetName]) }
          .toList()
      }
    }

  override suspend fun updatePreset(presetData: EqPresetData, duringTxn: () -> Unit): BoolResult =
    runSuspendCatching {
      db.transaction { (doUpdatePreset(presetData) > 0).also { if (it) duringTxn() } }
    }

  private fun TransactionInProgress.doUpdatePreset(presetData: EqPresetData) = UPDATE_ALL.update {
    it[BIND_PRESET_ID] = presetData.id.value
    it[updatedTime] = Millis.currentUtcEpochMillis().value
    it[preAmp] = presetData.preAmpAndBands.preAmp.value
    with(presetData.preAmpAndBands) {
      it[band0] = bands[0].value
      it[band1] = bands[1].value
      it[band2] = bands[2].value
      it[band3] = bands[3].value
      it[band4] = bands[4].value
      it[band5] = bands[5].value
      it[band6] = bands[6].value
      it[band7] = bands[7].value
      it[band8] = bands[8].value
      it[band9] = bands[9].value
    }
  }

  override suspend fun updatePreAmp(
    presetId: EqPresetId,
    amplitude: Amp
  ): BoolResult = runSuspendCatching {
    db.transaction {
      EqPresetTable
        .updateColumns {
          it[preAmp] = amplitude.value
          it[updatedTime] = Millis.currentUtcEpochMillis().value
        }
        .where { id eq presetId.value }
        .update() > 0
    }
  }

  override suspend fun updateBand(
    presetId: EqPresetId,
    index: Int,
    amplitude: Amp
  ): BoolResult = runSuspendCatching {
    db.transaction {
      val columnIndices = EqPresetTable.bandColumns.indices
      require(index in columnIndices) { "Index $index not in bounds $columnIndices" }
      EqPresetTable
        .updateColumns {
          it[bandColumns[index]] = amplitude.value
          it[updatedTime] = Millis.currentUtcEpochMillis().value
        }
        .where { id eq presetId.value }
        .update() > 0
    }
  }

  override suspend fun deletePreset(id: EqPresetId): BoolResult = runSuspendCatching {
    db.transaction { EqPresetTable.delete { this.id eq id.value } } > 0
  }
}

data class EqPresetIdName(val id: EqPresetId, val name: String)

data class EqPresetData(
  val id: EqPresetId,
  val name: String,
  val preAmpAndBands: EqPresetDao.PreAmpAndBands
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
    preAmpAndBands: EqPresetDao.PreAmpAndBands
  ): DaoResult<EqPresetId> = Err(NotImplementedError())

  override suspend fun getPresetData(
    id: EqPresetId
  ): DaoResult<EqPresetData> = NotImplemented

  override suspend fun getAllUserPresets(): DaoResult<List<EqPresetIdName>> =
    NotImplemented

  override suspend fun updatePreset(
    presetData: EqPresetData,
    duringTxn: () -> Unit
  ): BoolResult = NotImplemented

  override suspend fun updatePreAmp(presetId: EqPresetId, amplitude: Amp): BoolResult =
    NotImplemented

  override suspend fun updateBand(
    presetId: EqPresetId,
    index: Int,
    amplitude: Amp
  ): BoolResult = NotImplemented

  override suspend fun deletePreset(id: EqPresetId): BoolResult = NotImplemented

  private val NotImplemented = Err(NotImplementedError())
}
