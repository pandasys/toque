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

import android.content.Context
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._i
import com.ealva.welite.db.Database
import com.ealva.welite.db.OpenParams
import com.ealva.welite.db.table.SqlExecutor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val LOG by lazyLogger(DbModule::class)
private const val DB_FILENAME = "ToqueDB"

object DbModule {

  val koinModule = module {
    single { makeDatabase(androidContext()) }
    single { GenreDao(get()) }
    single { ArtistDao(get()) }
    single { AlbumDao(get()) }
    single { ArtistAlbumDao() }
    single { ComposerDao() }
    single { ArtistMediaDao() }
    single { GenreMediaDao() }
    single { ComposerMediaDao() }
    single { EqPresetDao(get()) }
    single { EqPresetAssociationDao(get()) }
    single {
      AudioMediaDao(
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get()
      )
    }
    single { QueueStateDaoFactory(get()) }
  }

  private fun makeDatabase(context: Context) = Database(
    context = context,
    fileName = DB_FILENAME,
    tables = setOfAllTables,
    version = 1,
    openParams = OpenParams(
      enableWriteAheadLogging = true,
      enableForeignKeyConstraints = true
    )
  ) {
    onConfigure {
      LOG._i { it("Database onConfigure") }
    }
    onCreate { db ->
      LOG._i { it("tables=%s", db.tables) }
      LOG.i { it("SQLite version=%s", sqliteVersion) }
      FullAudioView.create()
    }
    onOpen { db ->
      EqPresetDao.establishMinimumRowId(this)
      LOG._i { it("Database path:%s", db.path) }
    }
  }
}

class SqlExecutorSpy : SqlExecutor {
  val execSqlList: MutableList<String> = mutableListOf()
  override fun exec(sql: String, vararg bindArgs: Any) {
    execSqlList += sql
  }

  override fun exec(sql: List<String>) {
    execSqlList += sql
  }
}
