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

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict
import com.ealva.toque.db.ComposerMediaTable as Table

private val LOG by lazyLogger(ComposerMediaDao::class)

interface ComposerMediaDao {
  /**
   * Insert or replace all artists for [mediaId]
   */
  fun replaceMediaComposer(
    txn: Transaction,
    composerId: ComposerId,
    mediaId: MediaId,
    createTime: Long
  )

  fun deleteAll(txn: Transaction)

  companion object {
    operator fun invoke(): ComposerMediaDao = ComposerMediaDaoImpl()
  }
}

private var INSERT_COMPOSER = -1
private var INSERT_MEDIA = -1
private var INSERT_CREATE_TIME = -1
private val INSERT_COMPOSER_MEDIA = Table.insertValues(OnConflict.Replace) {
  it[composerId].bindArg()
  it[mediaId].bindArg()
  it[createdTime].bindArg()
  INSERT_COMPOSER = it.indexOf(composerId)
  INSERT_MEDIA = it.indexOf(mediaId)
  INSERT_CREATE_TIME = it.indexOf(createdTime)
}

private val DELETE_MEDIA = Table.deleteWhere { Table.mediaId eq bindLong() }

private class ComposerMediaDaoImpl : ComposerMediaDao {
  override fun replaceMediaComposer(
    txn: Transaction,
    composerId: ComposerId,
    mediaId: MediaId,
    createTime: Long
  ) = txn.run {
    INSERT_COMPOSER_MEDIA.insert {
      it[INSERT_COMPOSER] = composerId.id
      it[INSERT_MEDIA] = mediaId.id
      it[INSERT_CREATE_TIME] = createTime
    }
    Unit
  }

  override fun deleteAll(txn: Transaction) = txn.run {
    val count = Table.deleteAll()
    LOG.i { it("Deleted %d composer/media associations", count) }
  }
}
