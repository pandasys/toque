/*
 * Copyright 2022 Eric A. Snell
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

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.asArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.asArtistId
import com.ealva.toque.prefs.AppPrefsSingleton

private val LOG by lazyLogger(DownloadArtistArtWorker::class)

private const val FOLDER_NAME = "album"

class DownloadArtistArtWorker(
  appContext: Context,
  workerParams: WorkerParameters,
  private val appPrefs: AppPrefsSingleton,
  private val artistDao: ArtistDao,
  private val artworkDownloader: ArtworkDownloader
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val artistId = inputData.artistId
    val artist = inputData.artistName.value
    val source = inputData.sourceUri
    val dest = Artwork.getArtworkDestination(artist, FOLDER_NAME, true)
    return try {
      val quality = appPrefs.instance().compressionQuality()
      artistDao.downloadArt(artistId) {
        artworkDownloader.download(
          source,
          dest,
          ORIGINAL_SIZE,
          quality,
        )
        artistDao.setArtistArt(artistId, source, dest)
      }
      Result.success()
    } catch (e: Exception) {
      LOG.e(e) {
        it("Error downloading %s to %s for %s", source, dest, artist)
      }
      Result.failure()
    }
  }

  private val Data.artistId: ArtistId
    get() = getLong(KEY_ARTIST_ID, ArtistId.INVALID.value).asArtistId
  private val Data.artistName: ArtistName get() = getString(KEY_ARTIST_NAME).asArtistName
  private val Data.sourceUri: Uri get() = getString(KEY_SOURCE_URI).toUriOrEmpty()

  companion object {
    const val KEY_ARTIST_ID = "ArtistIdKey"
    const val KEY_ARTIST_NAME = "ArtistNameKey"
    const val KEY_SOURCE_URI = "SourceUriKey"

    val workerClassName: String = DownloadArtistArtWorker::class.java.name

    fun makeDownloadRequest(
      artistId: ArtistId,
      artistName: ArtistName,
      source: Uri
    ): WorkRequest {
      return OneTimeWorkRequestBuilder<DownloadArtistArtWorker>()
        .setConstraints(constraints)
        .setInputData(
          workDataOf(
            KEY_ARTIST_ID to artistId.value,
            KEY_ARTIST_NAME to artistName.value,
            KEY_SOURCE_URI to source.toString()
          )
        )
        .build()
    }

    private val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.UNMETERED)
      .setRequiresStorageNotLow(true)
      .build()
  }
}

class DownloadArtistArtWorkerFactory(
  private val appPrefs: AppPrefsSingleton,
  private val artistDao: ArtistDao,
  private val artworkDownloader: ArtworkDownloader
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): ListenableWorker? = when (workerClassName) {
    DownloadArtistArtWorker.workerClassName -> DownloadArtistArtWorker(
      appContext,
      workerParameters,
      appPrefs,
      artistDao,
      artworkDownloader
    )
    else -> null
  }
}
