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
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.view.WindowManager
import com.ealva.ealvalog.LogLevel
import com.ealva.ealvalog.Loggers
import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.Markers
import com.ealva.ealvalog.android.AndroidLogger
import com.ealva.ealvalog.android.AndroidLoggerFactory
import com.ealva.ealvalog.core.BasicMarkerFactory
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.logger
import com.ealva.toque.android.content.requireSystemService
import com.ealva.toque.audio.AudioModule
import com.ealva.toque.common.debug
import com.ealva.toque.db.DbModule
import com.ealva.toque.file.FilesModule
import com.ealva.toque.log._i
import com.ealva.toque.prefs.PrefsModule
import com.ealva.toque.service.vlc.LibVlcModule
import com.ealva.toque.tag.TagModule
import com.jakewharton.processphoenix.ProcessPhoenix
import com.zhuinden.simplestack.GlobalServices
import ealvatag.logging.EalvaTagLog
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

interface Toque {
  fun restartApp(intent: Intent, context: Context)

  val globalServicesBuilder: GlobalServices.Builder

  companion object {
    /** Not valid until the [Application.onCreate] function is called */
    val appContext: Context
      get() = checkNotNull(ToqueImpl.appContext)
  }
}

class ToqueImpl : Application(), Toque {
  private lateinit var _globalServicesBuilder: GlobalServices.Builder
  override val globalServicesBuilder: GlobalServices.Builder
    get() = _globalServicesBuilder

  private val appModule = module {
    single<Toque> { this@ToqueImpl }
    single<AudioManager> { requireSystemService() }
    single<NotificationManager> { requireSystemService() }
    single<PowerManager> { requireSystemService() }
    single<TelephonyManager> { requireSystemService() }
    single<KeyguardManager> { requireSystemService() }
    single<WindowManager> { requireSystemService() }
  }

  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext
    _globalServicesBuilder = GlobalServices.builder()

    setupLogging()

    val logger = logger()
    logger._i { it("App create") }

    debug {
//      WeLiteLog.logQueryPlans = true
//      WeLiteLog.logSql = true
    }

//    val policy: StrictMode.VmPolicy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//      StrictMode.VmPolicy.Builder()
//        .detectNonSdkApiUsage()
//        .penaltyListener(
//          Executors.newSingleThreadExecutor(),
//          { v -> v?.let { violation -> logger._e(violation) { it("strict violation") } } }
//        )
//        .build()
//    } else {
//      StrictMode.VmPolicy.Builder().build()
//    }
//    StrictMode.setVmPolicy(policy)

    startKoin {
//      androidLogger(Level.NONE)
      androidContext(androidContext = this@ToqueImpl)

      modules(
        appModule,
        PrefsModule.koinModule,
        AudioModule.koinModule,
        FilesModule.koinModule,
        TagModule.koinModule,
        LibVlcModule.koinModule,
        DbModule.koinModule,
//        MainModule.koinModule
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
    /** Set in Toque [onCreate] */
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
  ): Boolean = false // include location per logger and not via handler
}
