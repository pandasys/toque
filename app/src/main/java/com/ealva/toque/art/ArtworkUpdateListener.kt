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

import android.net.Uri
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDaoEvent.AlbumArtworkUpdated
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.ArtistDaoEvent.ArtistArtworkUpdated
import com.ealva.toque.log._e
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.work.Work
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

private val LOG by lazyLogger(ArtworkUpdateListener::class)

class ArtworkUpdateListener(
  private val work: Work,
  private val albumDao: AlbumDao,
  private val artistDao: ArtistDao,
  private val appPrefs: AppPrefsSingleton,
  private val albumArtWorkerFactory: DownloadAlbumArtWorkerFactory,
  private val artistArtWorkerFactory: DownloadArtistArtWorkerFactory,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
  private lateinit var scope: CoroutineScope

  fun start() {
    loadFactories(work)
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    albumDao.albumDaoEvents
      .onEach { event -> if (event is AlbumArtworkUpdated) handleAlbumUpdate(event) }
      .catch { cause -> LOG.e(cause) { it("Error updating from Album Dao events") } }
      .onCompletion { LOG._e { it("Completed ArtworkUpdate album dao event flow") } }
      .launchIn(scope)

    artistDao.artistDaoEvents
      .onEach { event -> if (event is ArtistArtworkUpdated) handleArtistUpdate(event) }
      .catch { cause -> LOG.e(cause) { it("Error updating from Artist Dao events") } }
      .onCompletion { LOG._e { it("Completed ArtworkUpdate artist dao event flow") } }
      .launchIn(scope)
  }

  fun stop() {
    scope.cancel()
  }

  private suspend fun handleAlbumUpdate(update: AlbumArtworkUpdated) {
    if (shouldDownload(update.localAlbumArt, update.albumArt, appPrefs.instance())) {
      albumDao.getArtistAlbum(update.albumId)
        .onFailure { cause -> LOG.e(cause) { it("Error getting artist/album") } }
        .onSuccess { (artistName, albumTitle) ->
          work.enqueue(
            DownloadAlbumArtWorker.makeDownloadRequest(
              albumId = update.albumId,
              artistName = artistName,
              albumTitle = albumTitle,
              source = update.albumArt,
            )
          )
        }
    }
  }

  private suspend fun handleArtistUpdate(update: ArtistArtworkUpdated) {
    if (shouldDownload(update.localArtwork, update.remoteArtwork, appPrefs.instance())) {
      artistDao.getArtistName(update.artistId)
        .onFailure { cause -> LOG.e(cause) { it("Error getting artist name") } }
        .onSuccess { artistName ->
          work.enqueue(
            DownloadArtistArtWorker.makeDownloadRequest(
              artistId = update.artistId,
              artistName = artistName,
              source = update.remoteArtwork,
            )
          )
        }
    }
  }

  private fun shouldDownload(localArt: Uri, remoteArt: Uri, appPrefs: AppPrefs): Boolean =
    localArt == Uri.EMPTY && remoteArt != Uri.EMPTY && appPrefs.downloadArt()

  private fun loadFactories(work: Work) {
    work.addFactory(DownloadAlbumArtWorker.workerClassName, albumArtWorkerFactory)
    work.addFactory(DownloadArtistArtWorker.workerClassName, artistArtWorkerFactory)
  }
}
