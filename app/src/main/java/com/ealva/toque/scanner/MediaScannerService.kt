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

package com.ealva.toque.scanner

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.BuildConfig
import com.ealva.toque.R
import com.ealva.toque.android.content.cannotWriteStorage
import com.ealva.toque.android.content.canReadStorage
import com.ealva.toque.common.Millis
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.file.MediaStorage
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.scanner.MediaScannerService.RescanType
import com.ealva.toque.service.media.MediaMetadataParser
import com.ealva.toque.service.media.MediaMetadataParserFactory
import com.ealva.toque.service.player.WakeLock
import com.ealva.toque.service.player.WakeLockFactory
import com.google.common.base.Stopwatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val LOG by lazyLogger(MediaScannerService::class)
private const val SUFFIX = ".MediaScannerJobIntentService"
private const val ACTION_FULL_RESCAN = "FullRescan$SUFFIX"
private const val NOTIFICATION_ID = 39000
private const val SCANNER_CHANNEL_ID = "com.ealva.toque.scanner.MediaScannerNotificationChannel"

/**
 * Surprisingly (to me), increasing this number was still showing performance improvements, albeit
 * in quickly decreasing increments, for non-debug builds on a real device. When I say performance,
 * I'm speaking of total time to scan over 2500 files, mostly MP3s. I'm choosing this value as a
 * stopping point as it is already eating a substantial amount of resources.
 *
 * I'm guessing a more efficient "concurrent" flow operator will require this to be revisited. Also,
 * a proper performance test harness is needed to select this number. For now, it seems high as it
 * requires spinning up a lot of threads that probably wouldn't exist otherwise. But that cost seems
 * to be worth it, given the current design of this app and my simple, homegrown, attempt at
 * concurrency. See [MediaScannerService.scanAllAudioAfter]. Also, keep increasing this number and
 * performance will eventually decline - especially on a low-end device.
 *
 * [Parallel flow processing #1147][https://github.com/Kotlin/kotlinx.coroutines/issues/1147]
 */
private const val PERSIST_WORKER_COUNT = 20

private const val EXTRAS_KEY_SUFFIX = ".MediaScanner"

private data class Work(
  val intent: Intent?,
  val startId: Int
)

class MediaScannerService : LifecycleService() {
  private val storage: MediaStorage by inject()
  private val audioMediaDao: AudioMediaDao by inject()
  private val mediaDataParserFactory: MediaMetadataParserFactory by inject()
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)
  private val workFlow = MutableSharedFlow<Work>(extraBufferCapacity = 10)
  private val notificationManager: NotificationManager by lazy {
    requireNotNull(getSystemService())
  }
  private lateinit var notificationChannel: NotificationChannel
  private var job: Job? = null
  private var workEnqueuer: WorkEnqueuer? = null

  override fun onCreate() {
    super.onCreate()
    if (cannotWriteStorage()) {
      stopSelf()
      return
    }
    workEnqueuer = WorkEnqueuer.getWorkEnqueuer(this)
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
    startNotification()
    job = workFlow
      .onEach { work -> handleNewWork(work) }
      .catch { cause -> LOG.e(cause) { it("Work flow error") } }
      .onCompletion { workEnqueuer?.serviceProcessingFinished() }
      .launchIn(lifecycleScope + Dispatchers.IO)
  }

  private suspend fun handleNewWork(work: Work) {
    workEnqueuer?.serviceProcessingStarted()
    if (work.intent != null) onHandleWork(work.intent)
    stopSelf(work.startId)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    if (cannotWriteStorage()) {
      return START_NOT_STICKY
    }
    workEnqueuer?.serviceStartReceived()
    LOG.i { it("onStartCommand id=%d action=%s", startId, intent?.action ?: "null") }
    val work = Work(intent, startId)
    lifecycleScope.launch {
      workFlow.emit(work)
    }
    return START_REDELIVER_INTENT
  }

  override fun onDestroy() {
    super.onDestroy()
    _isScanning.value = false
    workEnqueuer?.serviceProcessingFinished()
    stopNotification()
    job?.cancel()
  }

  private fun startNotification() {
    _isScanning.value = true
    startForeground(
      NOTIFICATION_ID,
      NotificationCompat.Builder(this, SCANNER_CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Scanning Music Library")
        .setSmallIcon(R.drawable.ic_baseline_refresh_24)
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

  private suspend fun onHandleWork(intent: Intent) {
    try {
      when (intent.action) {
        ACTION_FULL_RESCAN -> tryFullRescan(intent.rescanType)
        else -> LOG.e { it("Unrecognized action=%s", intent.action ?: "null") }
      }
    } catch (e: Exception) {
      LOG.e(e) { it("Media Scanner failed") }
    }
  }

  private suspend fun tryFullRescan(rescan: RescanType) {
    if (canReadStorage()) {
      doFullRescanWithPermission(rescan)
    } else {
      LOG.e { it("No permission %s", READ_EXTERNAL_STORAGE) }
    }
  }

  private suspend fun doFullRescanWithPermission(rescan: RescanType) {
    val prefs = appPrefsSingleton.instance()
    LOG._i { it("Start scan stopwatch") }
    val stopWatch = Stopwatch.createStarted()
    if (rescan.cleanDb) deleteAllMediaFromDb()
    val createUpdateTime = Millis.currentUtcEpochMillis()
    val minimumDuration = if (BuildConfig.DEBUG) {
      30_000.toDuration(DurationUnit.MILLISECONDS)
    } else {
      if (prefs.ignoreSmallFiles()) prefs.ignoreThreshold() else Duration.ZERO
    }
    scanAllAudioAfter(
      if (rescan.forceUpdate) Date(0) else prefs.lastScanTime().toDate(),
      mediaDataParserFactory.make(),
      minimumDuration,
      createUpdateTime
    ) { prefs.edit { it[firstRun] = false } }
    audioMediaDao.deleteEntitiesWithNoMedia()
    LOG.i { it("Elapsed:%d end scan", stopWatch.stop().elapsed(TimeUnit.MILLISECONDS)) }
    prefs.lastScanTime.set(createUpdateTime)
  }

  private suspend fun deleteAllMediaFromDb() = withContext(Dispatchers.IO) {
    audioMediaDao.deleteAll()
  }

  private suspend fun scanAllAudioAfter(
    modifiedAfter: Date,
    parser: MediaMetadataParser,
    minDuration: Duration,
    modifiedTime: Millis,
    onCompletion: suspend () -> Unit
  ) {
    LOG._e { it("scanAllAfter %s", modifiedAfter) }
    withContext(Dispatchers.IO) {
      storage.audioFlow(modifiedAfter, minDuration)
        .map { audioInfo -> async { persistAudio(audioInfo, parser, minDuration, modifiedTime) } }
        .buffer(PERSIST_WORKER_COUNT)
        .map { deferred -> deferred.await() }
        .catch { cause -> LOG.e(cause) { it("AudioFlow processing error") } }
        .onCompletion { onCompletion() }
        .collect()
    }
  }

  private suspend fun persistAudio(
    audioInfo: AudioInfo,
    parser: MediaMetadataParser,
    minimumDuration: Duration,
    createUpdateTime: Millis
  ): Boolean = try {
    audioMediaDao.upsertAudio(audioInfo, parser, minimumDuration, createUpdateTime)
    true
  } catch (e: Exception) {
    LOG.e(e) { it("Error persisting %s", audioInfo) }
    false
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

    fun startScanner(context: Context, reason: String, rescan: RescanType) {
      LOG._e { it("reason=%s rescan=%s", reason, rescan) }
      if (context.cannotWriteStorage()) {
        LOG.e { it("Don't have read external permission. Not starting media scanner.") }
      } else {
        val intent = Intent(context, MediaScannerService::class.java).apply {
          action = ACTION_FULL_RESCAN
          scanReason = reason
          rescanType = rescan
        }
        LOG._e { it("getWorkEnqueuer") }
        WorkEnqueuer.getWorkEnqueuer(context).enqueueWork(intent)
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

interface WorkEnqueuer {
  fun enqueueWork(work: Intent)
  fun serviceStartReceived()
  fun serviceProcessingStarted()
  fun serviceProcessingFinished()

  companion object {
    private var instance: WorkEnqueuer? = null
    private val lock = ReentrantLock()
    fun getWorkEnqueuer(context: Context): WorkEnqueuer {
      return instance ?: lock.withLock {
        instance ?: WorkEnqueuerImpl(
          context.applicationContext,
          WakeLockFactory(requireNotNull(context.getSystemService()))
        ).also { instance = it }
      }
    }
  }
}

private const val WAKE_LOCK_TIMEOUT_MINUTES = 10L
private val LAUNCH_LOCK_TIMEOUT = Millis(TimeUnit.MINUTES.toMillis(WAKE_LOCK_TIMEOUT_MINUTES))
private const val RUN_WAKE_LOCK_TIMEOUT_MINUTES = 1L
private val RUN_LOCK_TIMEOUT =
  Millis(TimeUnit.MINUTES.toMillis(RUN_WAKE_LOCK_TIMEOUT_MINUTES))
private const val LAUNCH_LOCK_NAME = "toque:LaunchMediaScannerService"
private const val RUN_LOCK_NAME = "toque:RunMediaScannerService"

private class WorkEnqueuerImpl(
  private val context: Context,
  wakeLockFactory: WakeLockFactory
) : WorkEnqueuer {
  private val mutex = ReentrantLock()
  private val launchWakeLock: WakeLock =
    wakeLockFactory.makeWakeLock(LAUNCH_LOCK_TIMEOUT, LAUNCH_LOCK_NAME)
  private val runWakeLock: WakeLock = wakeLockFactory.makeWakeLock(RUN_LOCK_TIMEOUT, RUN_LOCK_NAME)
  private var launching = false
  private var processing = false

  override fun enqueueWork(work: Intent) {
    // no need to defensively copy [work] since we created in this file
    LOG._e { it("startForegroundService") }
    ContextCompat.startForegroundService(context, work)
    LOG._e { it("after startForegroundService") }
    mutex.withLock {
      if (!launching) {
        launching = true
        if (!processing) launchWakeLock.acquire()
      }
    }
  }

  override fun serviceStartReceived() = mutex.withLock {
    launching = false
  }

  override fun serviceProcessingStarted() = mutex.withLock {
    if (!processing) {
      processing = true
      runWakeLock.acquire()
      launchWakeLock.release()
    }
  }

  override fun serviceProcessingFinished() = mutex.withLock {
    if (processing) {
      if (launching) launchWakeLock.acquire()
      processing = false
      runWakeLock.release()
    }
  }
}
