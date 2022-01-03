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

import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.SQLiteSchema
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.coroutines.runSuspendCatching

interface SchemaDao {
  suspend fun getTableAndViewNames(): DaoResult<List<SchemaName>>

  companion object {
    operator fun invoke(db: Database): SchemaDao = SchemaDaoImpl(db)
  }
}

private class SchemaDaoImpl(private val db: Database) : SchemaDao {
  override suspend fun getTableAndViewNames(): DaoResult<List<SchemaName>> =
    runSuspendCatching {
      db.query {
        SQLiteSchema
          .select { name }
          .where { (type eq MasterType.Table.value) or (type eq MasterType.View.value) }
          .sequence { it[name].asSchemaName }
          .toList()
      }
    }
}
