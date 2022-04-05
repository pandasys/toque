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

package com.ealva.toque.art

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.R
import com.ealva.toque.file.MediaStorage
import com.ealva.toque.log._i
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(ArtworkSearchService::class)

private const val JOB_ID = 2000
private const val NOTIFICATION_ID = 40000
private const val SCANNER_CHANNEL_ID = "com.ealva.toque.art.ArtworkSearchService"

class ArtworkSearchService : JobIntentService() {

  private val storage: MediaStorage by inject()
  private val prefs: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)

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
          getString(R.string.MediaScannerChannelName),
          NotificationManager.IMPORTANCE_LOW
        )
      )
      notificationChannel = notificationManager.getNotificationChannel(SCANNER_CHANNEL_ID)
    }
  }

  private fun startNotification() {
    _isSearching.value = true
    startForeground(
      NOTIFICATION_ID,
      NotificationCompat.Builder(this, SCANNER_CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.Searching_for_artwork))
        .setSmallIcon(R.drawable.ic_baseline_refresh_24)
        .setOngoing(true)
        .setProgress(0, 0, true)
//        .setContentIntent(NowPlayingActivity.makeActivityPendingIntent(this))
        .build()
    )
  }

  private fun stopNotification() {
    _isSearching.value = false
    stopForeground(true)
  }

  override fun onHandleWork(intent: Intent) {
    startNotification()
    //try {
    //
    //} finally {
    //  stopNotification()
    //}
  }

  companion object {
    @Suppress("ObjectPropertyName")
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun startSearch(context: Context) {
      val intent = Intent(context, ArtworkSearchService::class.java)
      enqueueWork(context, intent)
    }

    private fun enqueueWork(context: Context, work: Intent) {
      LOG._i { it("enqueue work %s", work) }
      try {
        enqueueWork(context, ArtworkSearchService::class.java, JOB_ID, work)
      } catch (e: Exception) {
        LOG.e(e) { +it }
      }
    }
  }
}
