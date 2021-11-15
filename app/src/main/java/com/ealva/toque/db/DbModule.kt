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

import android.content.Context
import com.ealva.toque.prefs.AppPrefs
import com.ealva.welite.db.Database
import com.ealva.welite.db.OpenParams
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DB_FILENAME = "ToqueDB"

object DbModule {

  val koinModule = module {
    single { makeDatabase(androidContext()) }
    single { GenreDao(db = get()) }
    single { ArtistDao(db = get()) }
    single { AlbumDao(db = get()) }
    single { ArtistAlbumDao() }
    single { ComposerDao(db = get()) }
    single { ArtistMediaDao() }
    single { GenreMediaDao() }
    single { ComposerMediaDao() }
    single { EqPresetDao(db = get()) }
    single { EqPresetAssociationDao(db = get()) }
    single {
      AudioMediaDao(
        db = get(),
        artistParserFactory = get(),
        genreDao = get(),
        artistDao = get(),
        albumDao = get(),
        artistAlbumDao = get(),
        composerDao = get(),
        artistMediaDao = get(),
        genreMediaDao = get(),
        composerMediaDao = get(),
        eqPresetAssociationDao = get(),
        appPrefsSingleton = get(AppPrefs.QUALIFIER)
      )
    }
    single { QueuePositionStateDaoFactory(db = get()) }
    single { QueueDao(db = get()) }
  }

  private fun makeDatabase(context: Context) = Database(
    context = context,
    fileName = DB_FILENAME,
    tables = setOfAllTables,
    version = 1,
    otherCreatables = listOf(AudioViewQueueData, FullAudioView),
    openParams = OpenParams(
      enableWriteAheadLogging = true,
      enableForeignKeyConstraints = true
    )
  ) {
    onOpen { EqPresetDao.establishMinimumRowId(this) }
  }
}

// class SqlExecutorSpy : SqlExecutor {
//  val execSqlList: MutableList<String> = mutableListOf()
//  override fun exec(sql: String, vararg bindArgs: Any) {
//    execSqlList += sql
//  }
//
//  override fun exec(sql: List<String>) {
//    execSqlList += sql
//  }
// }
