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

package com.ealva.toque.service.audio

import android.content.Context
import android.net.Uri
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.common.Millis
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoExceptionMessage
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.file.isLocalScheme
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.tag.SongTag
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This interface is passed to [PlayableAudioItem]s to allow them to update themselves
 */
interface MediaFileStore {
  /** Increments the played count for media with [id] without blocking the caller */
  fun incrementPlayedCountAsync(id: MediaId)

  /** Increments the skipped count for media with [id] without blocking the caller */
  fun incrementSkippedCountAsync(id: MediaId)

  /** Updates the [duration] for media with [id] without blocking the caller */
  fun updateDurationAsync(id: MediaId, duration: Millis)

  /**
   * Sets and returns [newRating] if successful else a DoaMessage. The [id] is used to locate the
   * media in the DB and [fileLocation] is used to locate the file in the media store. The user can
   * select, via a preference, if the setting should be saved in the file tag. The caller will
   * suspend as this results in a persistence call (DB and possibly file system).
   */
  suspend fun setRating(
    id: MediaId,
    fileLocation: Uri,
    fileExt: String,
    newRating: Rating,
    writeToFile: Boolean,
    beforeFileWrite: suspend () -> Unit,
    afterFileWrite: suspend () -> Unit
  ): Result<Rating, DaoMessage>

//  suspend fun getSongInfo(mediaId: Long): SongInfo
//
//  fun getSongArtInfo(mediaId: Long): AlbumSongArtInfo
//
//  fun getMissingMedia(list: List<PlayableQueueMediaItem>): HashSet<PlayableQueueMediaItem>
//
//  fun getSongsForAlbumsInDefaultOrder(
//    album_idList: LongCollection,
//    allowDuplicates: Boolean
//  ): LongList
//
//  fun getTitle(mediaId: Long): String
//
//  suspend fun markMediaInvalid(mediaId: Long)
//
//  suspend fun deleteMediaFile(mediaId: Long)

  companion object {
    operator fun invoke(
      ctx: Context,
      audioMediaDao: AudioMediaDao
    ): MediaFileStore {
      return MediaFileStoreImpl(ctx, audioMediaDao)
    }
  }
}

private val LOG by lazyLogger(MediaFileStoreImpl::class)

private class MediaFileStoreImpl(
  private val ctx: Context,
  private val audioMediaDao: AudioMediaDao,
) : MediaFileStore {
  private val resolver = ctx.contentResolver

  override fun incrementPlayedCountAsync(id: MediaId) {
    audioMediaDao.incrementPlayedCount(id)
  }

  override fun incrementSkippedCountAsync(id: MediaId) {
    audioMediaDao.incrementSkippedCount(id)
  }

  override fun updateDurationAsync(id: MediaId, duration: Millis) {
    audioMediaDao.updateDuration(id, duration)
  }

  override suspend fun setRating(
    id: MediaId,
    fileLocation: Uri,
    fileExt: String,
    newRating: Rating,
    writeToFile: Boolean,
    beforeFileWrite: suspend () -> Unit,
    afterFileWrite: suspend () -> Unit
  ): Result<Rating, DaoMessage> = binding {
    audioMediaDao.setRating(id, newRating).bind()
    if (writeToFile && fileLocation.isLocalScheme()) {
      writeRatingToFile(fileLocation, fileExt, newRating, beforeFileWrite, afterFileWrite).bind()
    } else newRating
  }

  /**
   * Caller requirement: location is local - content: or file: schemes.
   *
   * Work done on Dispatcher.IO
   */
  private suspend fun writeRatingToFile(
    location: Uri,
    fileExt: String,
    newRating: Rating,
    beforeFileWrite: suspend () -> Unit,
    afterFileWrite: suspend () -> Unit
  ): Result<Rating, DaoMessage> = withContext(Dispatchers.IO) {
    runSuspendCatching {
      doWriteToFile(
        location,
        fileExt,
        newRating,
        beforeFileWrite,
        afterFileWrite
      )
    }.mapError { DaoExceptionMessage(it) }
  }

  private suspend fun doWriteToFile(
    location: Uri,
    fileExt: String,
    newRating: Rating,
    beforeFileWrite: suspend () -> Unit,
    afterFileWrite: suspend () -> Unit
  ): Rating {
    require(location.isLocalScheme())
    makeTempEditorFile(location.lastPathSegment, fileExt).useTempFile { tempEditorFile ->
      location.copyTo(tempEditorFile)
      SongTag(tempEditorFile, ignoreArtwork = true, createMissingTag = false).use { songTag ->
        songTag.starRating = newRating.toStarRating()
        songTag.saveChanges()
        beforeFileWrite()
        try {
          tempEditorFile.copyTo(location)
        } finally {
          afterFileWrite()
        }
      }
    }
    return newRating
  }

  private fun makeTempEditorFile(path: String?, fileExt: String): File =
    if (path != null) File(getEditorTempDir(ctx), "$path.$fileExt").apply { if (exists()) delete() }
    else throw IllegalArgumentException("No path")

  private fun Uri.copyTo(target: File) {
    resolver.openInputStream(this)?.source()?.buffer()?.use { source ->
      target.sink().buffer().use { sink ->
        sink.writeAll(source)
      }
    } ?: LOG.e { +it("ContentResolver returned null input stream for %s", this) }
  }

  private fun File.copyTo(target: Uri) {
    resolver.openOutputStream(target)?.sink()?.buffer()?.use { sink ->
      source().buffer().use { source ->
        sink.writeAll(source)
      }
    } ?: LOG.e { +it("ContentResolver returned null output stream for %s", target) }
  }

  companion object {
    private var tmpDir: File? = null
    private val lock = ReentrantLock()

    /** First call may result in file IO so don't do on main thread */
    private fun getEditorTempDir(ctx: Context): File {
      return tmpDir ?: lock.withLock {
        File(ctx.cacheDir, "Editing").apply {
          if (doesNotExist) mkdirs()
          tmpDir = this
        }
      }
    }
  }
}

val File.doesNotExist: Boolean get() = !exists()

inline fun <T : File, R> T.useTempFile(block: (T) -> R): R {
  try {
    return block(this)
  } finally {
    if (exists()) delete()
  }
}
