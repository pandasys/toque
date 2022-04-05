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
import android.util.Size
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
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.asAlbumTitle
import com.ealva.ealvabrainz.common.asArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.asAlbumId
import com.ealva.toque.prefs.AppPrefsSingleton
import kotlin.math.max

private val LOG by lazyLogger(DownloadAlbumArtWorker::class)

private const val FOLDER_NAME = "album"

class DownloadAlbumArtWorker(
  ctx: Context,
  workerParams: WorkerParameters,
  private val appPrefs: AppPrefsSingleton,
  private val albumDao: AlbumDao,
  private val artworkDownloader: ArtworkDownloader
) : CoroutineWorker(ctx, workerParams) {
  private val maxSize = ctx.resources.displayMetrics.run {
    val maxDimension = max(widthPixels, heightPixels)
    Size(maxDimension, maxDimension)
  }

  override suspend fun doWork(): Result {
    val albumId = inputData.albumId
    val artist = inputData.artistName.value
    val album = inputData.albumTitle.value
    val source = inputData.sourceUri
    val dest = Artwork.getArtworkDestination("$artist-$album", FOLDER_NAME, true)
    return try {
      val quality = appPrefs.instance().compressionQuality()
      albumDao.downloadArt(albumId) {
        artworkDownloader.download(
          source,
          dest,
          ORIGINAL_SIZE,
          quality,
          maxSize
        )
        albumDao.setAlbumArt(albumId, source, dest)
      }
      Result.success()
    } catch (e: Exception) {
      LOG.e(e) { it("Error downloading %s to %s for %s-%s", source, dest, artist, album) }
      Result.failure()
    }
  }

  private val Data.albumId: AlbumId get() = getLong(KEY_ALBUM_ID, AlbumId.INVALID.value).asAlbumId
  private val Data.artistName: ArtistName get() = getString(KEY_ARTIST_NAME).asArtistName
  private val Data.albumTitle: AlbumTitle get() = getString(KEY_ALBUM_TITLE).asAlbumTitle
  private val Data.sourceUri: Uri get() = getString(KEY_SOURCE_URI).toUriOrEmpty()

  companion object {
    const val KEY_ALBUM_ID = "AlbumIdKey"
    const val KEY_ARTIST_NAME = "ArtistNameKey"
    const val KEY_ALBUM_TITLE = "AlbumTitleKey"
    const val KEY_SOURCE_URI = "SourceUriKey"

    val workerClassName: String = DownloadAlbumArtWorker::class.java.name

    fun makeDownloadRequest(
      albumId: AlbumId,
      artistName: ArtistName,
      albumTitle: AlbumTitle,
      source: Uri
    ): WorkRequest {
      return OneTimeWorkRequestBuilder<DownloadAlbumArtWorker>()
        .setConstraints(constraints)
        .setInputData(
          workDataOf(
            KEY_ALBUM_ID to albumId.value,
            KEY_ARTIST_NAME to artistName.value,
            KEY_ALBUM_TITLE to albumTitle.value,
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

class DownloadAlbumArtWorkerFactory(
  private val appPrefs: AppPrefsSingleton,
  private val albumDao: AlbumDao,
  private val artworkDownloader: ArtworkDownloader
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): ListenableWorker? = when (workerClassName) {
    DownloadAlbumArtWorker::class.java.name -> DownloadAlbumArtWorker(
      appContext,
      workerParameters,
      appPrefs,
      albumDao,
      artworkDownloader
    )
    else -> null
  }
}
