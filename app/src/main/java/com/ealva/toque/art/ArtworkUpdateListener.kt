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
import com.ealva.toque.db.AlbumDaoEvent
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
  private val appPrefs: AppPrefsSingleton,
  private val albumArtWorkerFactory: DownloadAlbumArtWorkerFactory,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
  private lateinit var scope: CoroutineScope

  fun start() {
    loadFactories(work)
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    albumDao.albumDaoEvents
      .onEach { event -> if (event is AlbumDaoEvent.AlbumArtworkUpdated) handleAlbumUpdate(event) }
      .catch { cause -> LOG.e(cause) { it("Error updating from Album Dao events") } }
      .onCompletion { LOG._e { it("Completed ArtworkUpdate album dao event flow") } }
      .launchIn(scope)
  }

  fun stop() {
    scope.cancel()
  }

  private suspend fun handleAlbumUpdate(artworkUpdated: AlbumDaoEvent.AlbumArtworkUpdated) {
    if (artworkUpdated.shouldDownload(appPrefs.instance())) {
      albumDao.getArtistAlbum(artworkUpdated.albumId)
        .onFailure { cause -> LOG.e(cause) { it("Error getting artist/album") } }
        .onSuccess { (artistName, albumTitle) ->
          work.enqueue(
            DownloadAlbumArtWorker.makeDownloadRequest(
              albumId = artworkUpdated.albumId,
              artistName = artistName,
              albumTitle = albumTitle,
              source = artworkUpdated.albumArt,
            )
          )
        }
    }
  }

  private fun AlbumDaoEvent.AlbumArtworkUpdated.shouldDownload(appPrefs: AppPrefs) =
    localAlbumArt === Uri.EMPTY && albumArt !== Uri.EMPTY && appPrefs.downloadArt()

  private fun loadFactories(work: Work) {
    work.addFactory(DownloadAlbumArtWorker.workerClassName, albumArtWorkerFactory)
  }
}
