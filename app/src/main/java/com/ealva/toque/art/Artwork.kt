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
import com.ealva.toque.app.Toque
import com.ealva.toque.common.escapeForFile
import java.io.File

private const val ARTWORK_DIR = "artwork/"
private const val MAX_LENGTH = 127

object Artwork {
  /**
   * Creates a File using the path starting at [rootFolder], adding [folderName], then adding
   * a [fileName] with [fileExt]. If [shouldMkDirs] is true, mkdirs is called on the
   * destination folder.
   *
   * ```
   * getArtworkDestination(
   *   "${artistName.value}-${albumName.value}",
   *   "album"
   * )
   * ```
   */
  fun getArtworkDestination(
    fileName: String,
    folderName: String,
    shouldMkDirs: Boolean = true,
    fileExt: String = ".jpg",
    rootFolder: () -> File = { Toque.appContext.filesDir }
  ): Uri {
    val destinationFolder =
      File(rootFolder(), ARTWORK_DIR + checkFolder(folderName))

    if (shouldMkDirs) destinationFolder.mkdirs()
    val encoded = fileName.escapeForFile()
    val ext = checkExt(fileExt)
    return Uri.fromFile(
      File(
        destinationFolder,
        encoded.substring(0, encoded.length.coerceAtMost(MAX_LENGTH - ext.length)) + ext
      )
    )
  }

  private fun checkExt(fileExt: String) = when {
    fileExt.isBlank() -> ".jpg"
    fileExt.first() != '.' -> ".$fileExt"
    else -> fileExt
  }

  private fun checkFolder(folderName: String) = when {
    folderName.isBlank() -> ""
    folderName.last() != '/' -> "${folderName.escapeForFile()}/"
    else -> folderName.trimEnd { it == '/' }.escapeForFile().plus("/")
  }
}


/*
  override fun doRun() {
    if (downloader.networkIsAvailable()) {
      try {
        if (albumTable.shouldSearchForAlbumArt(albumId)) {
          albumTable.updateLastSearchTime(albumId, System.currentTimeMillis())
          val album = lastFmService.getAlbumInfo(artistName, albumName, LastFm.AutoCorrect.YES)
          album.mbid?.let { albumTable.maybeUpdateMbidAsync(albumId, album.mbid) }
          val uriList = lastFmService.getOrderedUris(album.image, prefs.downloadHiResAlbumArt)
          val source = if (!uriList.isNullOrEmpty()) {
            uriList[0]
          } else {
            runBlocking {
              var remoteImage = brainz.getFirstReleaseArt(artistName, albumName)
              if (remoteImage == null) {
                remoteImage = brainz.getFirstReleaseGroupArt(artistName, albumName)
              }
              remoteImage?.location ?: Uri.EMPTY
            }
          }
          if (source != Uri.EMPTY) {
            var destination: Uri = Uri.EMPTY
            if (prefs.autoDownloadArtwork) {
              destination = getArtworkDestination(
                downloader.appContext,
                "album/",
                "$artistName-${albumName.value}"
              )
              if (Uri.EMPTY !== destination) {
                downloadArtwork(source, destination, ORIGINAL_SIZE)
              } else {
                LOG.e { it("Could not establish download folder") }
              }
            }
            albumTable.updateDownloadedArtLocation(
              albumId,
              source,
              destination,
              System.currentTimeMillis()
            )
          }
        } else {
          LOG._i {
            it("Too soon to search id=%d artist='%s' album='%s'", albumId, artistName,
            albumName.value)
          }
        }
      } catch (e: Exception) {
        LOG.w(e) { +it }
      }
    } else {
      LOG._i { it("Network unavailable") }
    }
  }
*/
