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

package com.ealva.toque.app

import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import android.telecom.TelecomManager
import android.view.WindowManager
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.ealva.ealvalog.LogLevel
import com.ealva.ealvalog.Loggers
import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.Markers
import com.ealva.ealvalog.android.AndroidLogger
import com.ealva.ealvalog.android.AndroidLoggerFactory
import com.ealva.ealvalog.core.BasicMarkerFactory
import com.ealva.toque.android.content.requireSystemService
import com.ealva.toque.art.ArtworkModule
import com.ealva.toque.audioout.AudioModule
import com.ealva.toque.db.DbModule
import com.ealva.toque.file.FilesModule
import com.ealva.toque.prefs.PrefsModule
import com.ealva.toque.service.ServiceModule
import com.ealva.toque.service.vlc.LibVlcModule
import com.ealva.toque.tag.TagModule
import com.ealva.toque.work.Work
import com.ealva.toque.work.WorkModule
import com.jakewharton.processphoenix.ProcessPhoenix
import ealvatag.logging.EalvaTagLog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.videolan.libvlc.LibVLC

interface Toque {
  fun restartApp(intent: Intent, context: Context)

  companion object {
    /** Not valid until the [Application.onCreate] function is called */
    val appContext: Context
      get() = checkNotNull(ToqueImpl.appContext)
  }
}

class ToqueImpl : Application(), Toque, ImageLoaderFactory, Configuration.Provider {
  private lateinit var koinApplication: KoinApplication
  private val appModule = module {
    single<Toque> { this@ToqueImpl }
    single<AudioManager> { requireSystemService() }
    single<NotificationManager> { requireSystemService() }
    single<PowerManager> { requireSystemService() }
    single<TelecomManager> { requireSystemService() }
    single<KeyguardManager> { requireSystemService() }
    single<WindowManager> { requireSystemService() }
    single<UiModeManager> { requireSystemService() }
    single<PackageManager> { requireSystemService() }
  }

  override fun getWorkManagerConfiguration(): Configuration =
    koinApplication.koin.get<Work>().getWorkManagerConfiguration()

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext

    // Loading here because we quickly access some static methods (EqPreset related)
    GlobalScope.launch { LibVLC.loadLibraries() }

    setupLogging()

//    debug {
//      WeLiteLog.logQueryPlans = true
//      WeLiteLog.logSql = true
//    }

//    debug {
//      val policy: StrictMode.VmPolicy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//        val logger = logger(ToqueImpl::class)
//        StrictMode.VmPolicy.Builder()
//          .detectLeakedClosableObjects()
//          .detectNonSdkApiUsage()
//          .penaltyListener(Executors.newFixedThreadPool(1)) { v ->
//            v?.let { violation -> logger._e(violation) { it("strict violation") } }
//          }
//          .build()
//      } else {
//        StrictMode.VmPolicy.Builder()
//          .detectLeakedClosableObjects()
//          .build()
//      }
//      StrictMode.setVmPolicy(policy)
//    }

    koinApplication = startKoin {
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
        ServiceModule.koinModule,
        WorkModule.koinModule,
        ArtworkModule.koinModule,
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

  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(applicationContext)
      .crossfade(true)
      .diskCache {
        DiskCache.Builder()
          .directory(applicationContext.cacheDir.resolve("image_cache"))
          .build()
      }
      .build()
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
