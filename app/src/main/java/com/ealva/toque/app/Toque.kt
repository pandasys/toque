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

package com.ealva.toque.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ealva.ealvalog.LogLevel
import com.ealva.ealvalog.Loggers
import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.Markers
import com.ealva.ealvalog.android.AndroidLogger
import com.ealva.ealvalog.android.AndroidLoggerFactory
import com.ealva.ealvalog.core.BasicMarkerFactory
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.logger
import com.ealva.toque.db.DbModule
import com.ealva.toque.file.FilesModule
import com.ealva.toque.log._e
import com.ealva.toque.prefs.PrefsModule
import com.ealva.toque.service.vlc.LibVlcModule
import com.ealva.toque.tag.TagModule
import com.jakewharton.processphoenix.ProcessPhoenix
import ealvatag.logging.EalvaTagLog
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

interface Toque {
  fun restartApp(intent: Intent, context: Context)

  companion object {
    val appContext: Context
      get() = checkNotNull(ToqueImpl.appContext)
  }
}

class ToqueImpl : Application(), Toque {
  private val appModule = module {
    single<Toque> { this@ToqueImpl }
  }

  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext
    setupLogging()

    val logger = logger()
    logger._e { it("App create") }

//    Class.forName("dalvik.system.CloseGuard")
//      .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
//      .invoke(null, true)

    startKoin {
      androidLogger(level = Level.INFO)
      androidContext(androidContext = this@ToqueImpl)

      modules(
        appModule,
        PrefsModule.module,
        FilesModule.module,
        TagModule.module,
        LibVlcModule.module,
        DbModule.module,
//        brainzModule,
//        spotifyModule,
//        artModule,
      )
    }
  }

  private fun setupLogging() {
    Markers.setFactory(BasicMarkerFactory())
    AndroidLogger.setHandler(ToqueLogHandler())
    Loggers.setFactory(AndroidLoggerFactory)
  }

  override fun restartApp(intent: Intent, context: Context) {
//    fileHandler?.close()
    ProcessPhoenix.triggerRebirth(context, intent)
  }

  companion object {
    var appContext: Context? = null
  }
}

/**
 * Filter out EAlvaTag logging and don't log less than INFO
 */
private class ToqueLogHandler : com.ealva.ealvalog.android.BaseLogHandler() {
  override fun isLoggable(
    tag: String,
    logLevel: LogLevel,
    marker: Marker?,
    throwable: Throwable?
  ): Boolean = logLevel.isAtLeast(LogLevel.INFO) && EalvaTagLog.MARKER != marker

  override fun shouldIncludeLocation(
    tag: String,
    androidLevel: Int,
    marker: Marker?,
    throwable: Throwable?
  ): Boolean = androidLevel > Log.INFO
}
