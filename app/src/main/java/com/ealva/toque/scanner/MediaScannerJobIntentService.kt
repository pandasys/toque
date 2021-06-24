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

package com.ealva.toque.scanner

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.toMillis
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.file.MediaStorage
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.scanner.MediaScannerJobIntentService.RescanType
import com.ealva.toque.service.media.MediaMetadataParser
import com.ealva.toque.service.media.MediaMetadataParserFactory
import com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import java.util.Date
import java.util.concurrent.TimeUnit

private val LOG by lazyLogger(MediaScannerJobIntentService::class)
private const val JOB_ID = 1000
private const val SUFFIX = ".MediaScannerJobIntentService"
private const val ACTION_FULL_RESCAN = "FullRescan$SUFFIX"
private const val NOTIFICATION_ID = 39000
private const val SCANNER_CHANNEL_ID = "com.ealva.toque.scanner.MediaScannerNotificationChannel"
private const val PERSIST_WORKER_COUNT = 3
private const val EXTRAS_KEY_SUFFIX = ".MediaScanner"

class MediaScannerJobIntentService : JobIntentService() {
  private val storage: MediaStorage by inject()
  private val audioMediaDao: AudioMediaDao by inject()
  private val mediaDataParserFactory: MediaMetadataParserFactory by inject()
  private val appPrefsSingleton: AppPrefsSingleton by inject(qualifier = named("AppPrefs"))

  private val notificationManager: NotificationManager by lazy {
    requireNotNull(getSystemService())
  }
  private lateinit var notificationChannel: NotificationChannel

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(
        NotificationChannel(
          SCANNER_CHANNEL_ID,
          getString(R.string.ScanningMedia),
          NotificationManager.IMPORTANCE_LOW
        )
      )
      notificationChannel = notificationManager.getNotificationChannel(SCANNER_CHANNEL_ID)
    }
  }

  private fun startNotification() {
    _isScanning.value = true
    startForeground(
      NOTIFICATION_ID,
      NotificationCompat.Builder(this, SCANNER_CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Scanning Music Library")
        .setSmallIcon(R.drawable.ic_refresh)
        .setOngoing(true)
        .setProgress(0, 0, true)
//        .setContentIntent(NowPlayingActivity.makeActivityPendingIntent(this))
        .build()
    )
  }

  private fun stopNotification() {
    _isScanning.value = false
    stopForeground(true)
  }

  override fun onHandleWork(intent: Intent) {
    startNotification()
    try {
      when (intent.action) {
        ACTION_FULL_RESCAN -> runBlocking {
          LOG.i { it("Rescan reason:%s", intent.scanReason) }
          tryFullRescan(intent.rescanType)
        }
        else -> {
          LOG.e { it("Unrecognized action=%s", intent.action ?: "null") }
        }
      }
    } catch (e: Exception) {
      LOG.e(e) { it("Media Scanner failed") }
    } finally {
      stopNotification()
    }
  }

  private suspend fun tryFullRescan(rescan: RescanType) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
        doFullRescanWithPermission(rescan)
      } else {
        LOG.e { it("No permission %s", READ_EXTERNAL_STORAGE) }
      }
    } else {
      doFullRescanWithPermission(rescan)
    }
  }

  private suspend fun doFullRescanWithPermission(rescan: RescanType) {
    val stopWatch = Stopwatch.createStarted()
    val prefs = appPrefsSingleton.instance()
    val parser = mediaDataParserFactory.make()
    val lastScanTime = if (rescan.forceUpdate) Date(0) else prefs.lastScanTime().toDate()
    LOG._i { it("lastScanTime=%s", lastScanTime) }
    if (rescan.cleanDb) deleteAllMediaFromDb()
    val createUpdateTime = System.currentTimeMillis().toMillis()
    scanAllAudioAfter(lastScanTime, prefs, parser, createUpdateTime)
    audioMediaDao.deleteEntitiesWithNoMedia()
    LOG.i { it("Elapsed:%d end scan", stopWatch.stop().elapsed(TimeUnit.MILLISECONDS)) }
    prefs.lastScanTime.set(createUpdateTime)
  }

  private suspend fun deleteAllMediaFromDb() {
    LOG._i { it("-->deleteAllMediaFromDb") }
    withContext(Dispatchers.IO) {
      audioMediaDao.deleteAll()
    }
  }

  private suspend fun scanAllAudioAfter(
    lastModified: Date,
    prefs: AppPrefs,
    parser: MediaMetadataParser,
    createUpdateTime: Millis
  ) {
    LOG._e { it("scanAllAudioAfter=%s", lastModified) }
    withContext(Dispatchers.IO) {
      storage.audioFlow(lastModified)
        .map { async { persistAudioList(it, parser, prefs, createUpdateTime) } }
        .buffer(PERSIST_WORKER_COUNT)
        .map { it.await() }
        .onCompletion { cause ->
          if (cause != null) {
            LOG.e(cause) { it("Scan audio error: %s", cause.message ?: "failed") }
          } else {
            LOG._e { it("Scan audio complete") }
            prefs.edit { it[firstRun] = false }
          }
        }
        .collect()
    }
  }

  private suspend fun persistAudioList(
    audioList: List<AudioInfo>,
    parser: MediaMetadataParser,
    prefs: AppPrefs,
    createUpdateTime: Millis
  ): Boolean {
    audioMediaDao.upsertAudioList(audioList, parser, prefs, createUpdateTime)
    return true
  }

  enum class RescanType(
    override val id: Int,
    val forceUpdate: Boolean,
    val cleanDb: Boolean
  ) : HasConstId {
    ModifiedSinceLast(1, false, false),
    RescanAll(2, true, false),
    DeleteAllThenRescanAll(3, true, true);
  }

  companion object {
    @Suppress("ObjectPropertyName")
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startRescan(context: Context, reason: String, rescan: RescanType) {
      LOG._i {
        it("reason=%s rescan=%s", reason, rescan)
      }
      val intent = Intent(context, MediaScannerJobIntentService::class.java).apply {
        action = ACTION_FULL_RESCAN
        scanReason = reason
        rescanType = rescan
      }
      return enqueueWork(context, intent)
    }

    private fun enqueueWork(context: Context, work: Intent) {
      LOG._i { it("enqueue work %s", work) }
      try {
        enqueueWork(context, MediaScannerJobIntentService::class.java, JOB_ID, work)
      } catch (e: Exception) {
        LOG.e(e) { +it }
      }
    }
  }
}

private const val RESCAN_KEY = "rescan$EXTRAS_KEY_SUFFIX"
var Intent.rescanType: RescanType
  get() = when (getIntExtra(RESCAN_KEY, RescanType.ModifiedSinceLast.id)) {
    RescanType.ModifiedSinceLast.id -> RescanType.ModifiedSinceLast
    RescanType.RescanAll.id -> RescanType.RescanAll
    RescanType.DeleteAllThenRescanAll.id -> RescanType.DeleteAllThenRescanAll
    else -> RescanType.ModifiedSinceLast
  }
  set(value) {
    putExtra(RESCAN_KEY, value.id)
  }

private const val REASON_KEY = "scan_reason$EXTRAS_KEY_SUFFIX"
var Intent.scanReason: String
  get() = extras?.getString(REASON_KEY) ?: "Unknown"
  set(value) {
    extras?.putString(REASON_KEY, value)
  }
