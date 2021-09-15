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

package com.ealva.toque.file

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

@JvmInline
value class AudioContentId(val prop: Long)

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toAudioContentId() = AudioContentId(this)

private val ARTWORK_URI = Uri.parse("content://media/external/audio/albumart")

/**
 * MediaStorage provides an interface to read and write the device MediaStore.
 */
interface MediaStorage {
  suspend fun location(id: AudioContentId): Uri

  /**
   * Get all audio grouped by artist and album. Each list of the flow contains all the tracks
   * found for a given artist.album. eg. a list might contain all the songs on the Beatles Let It Be
   * album and another list would contain all the songs on the Beatles Revolver album. This
   * grouping is based on the MediaStore database sorted by artist then album
   */
  fun audioFlow(modifiedAfter: Date, minimumDuration: Millis): Flow<List<AudioInfo>>

  companion object {
    operator fun invoke(context: Context): MediaStorage = MediaStorageImpl(context)

//    fun getAlbumArtUri(audioContentId: AudioContentId): Uri =
//      Uri.parse("content://media/external/audio/media/${audioContentId.prop}/albumart")
  }
}

private val LOG by lazyLogger(MediaStorage::class)

/**
 * Unlikely to be this many tracks on an album so use this as starting list size.
 */
private const val DEFAULT_AUDIO_LIST_SIZE = 64

private class MediaStorageImpl(context: Context) : MediaStorage {
  private val resolver = context.contentResolver

  private val audioCollectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
  } else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  }

  @Suppress("DEPRECATION")
  private val audioQueryFields = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.AlbumColumns.ALBUM,
    MediaStore.Audio.AlbumColumns.ALBUM_ID,
    MediaStore.MediaColumns.DATA,
    MediaStore.Audio.AudioColumns.TITLE,
    MediaStore.Audio.AudioColumns.DATE_ADDED,
    MediaStore.Audio.AudioColumns.DATE_MODIFIED,
    MediaStore.Audio.AudioColumns.MIME_TYPE,
    MediaStore.Audio.AudioColumns.SIZE,
  )

  override suspend fun location(id: AudioContentId): Uri =
    ContentUris.withAppendedId(audioCollectionUri, id.prop)

  fun Cursor.longColumnToDate(columnIndex: Int): Date =
    Date(TimeUnit.SECONDS.toMillis(getLong(columnIndex)))

  override fun audioFlow(modifiedAfter: Date, minimumDuration: Millis): Flow<List<AudioInfo>> {
    return flow {
      resolver.query(
        audioCollectionUri,
        audioQueryFields,
        makeAudioQueryWhereClause(modifiedAfter, minimumDuration),
        null,
        makeAudioQuerySortClause()
      )?.use { cursor: Cursor ->
        val iId = cursor.indexOf(MediaStore.Audio.Media._ID)
        val iAlbum = cursor.indexOf(MediaStore.Audio.AlbumColumns.ALBUM)
        val iAlbumId = cursor.indexOf(MediaStore.Audio.AlbumColumns.ALBUM_ID)
        @Suppress("DEPRECATION") val iData = cursor.indexOf(MediaStore.MediaColumns.DATA)
        val iTitle = cursor.indexOf(MediaStore.Audio.AudioColumns.TITLE)
        val iDateAdded = cursor.indexOf(MediaStore.Audio.AudioColumns.DATE_ADDED)
        val iDateModified = cursor.indexOf(MediaStore.Audio.AudioColumns.DATE_MODIFIED)
        val iMimeType = cursor.indexOf(MediaStore.Audio.AudioColumns.MIME_TYPE)
        val iSize = cursor.indexOf(MediaStore.Audio.AudioColumns.SIZE)

        val list = ArrayList<AudioInfo>(DEFAULT_AUDIO_LIST_SIZE)
        var currentAlbum = ""
        var currentAlbumId = -1L
        var currentAlbumArt = Uri.EMPTY
        while (cursor.moveToNext()) {
          val nextAlbum = cursor.getString(iAlbum)
          if (nextAlbum != currentAlbum) {
            if (list.isNotEmpty()) emit(list.toList())
            list.clear()
            currentAlbum = nextAlbum
            currentAlbumId = -1
          }
          val id = cursor.getLong(iId).toAudioContentId()
          if (currentAlbumId == -1) {
            val albumId = cursor.getLong(iAlbumId)
            if (albumId > 0) {
              currentAlbumId = albumId
              currentAlbumArt = currentAlbumId.albumArtFor()
            }
          }
          list += AudioInfo(
            id,
            location(id),
            File(cursor.getString(iData)),
            cursor.getString(iTitle),
            cursor.longColumnToDate(iDateAdded),
            cursor.longColumnToDate(iDateModified),
            cursor.getString(iMimeType),
            cursor.getLong(iSize),
            currentAlbumArt
          )
        }
        if (list.isNotEmpty()) emit(list.toList())
      }
    }
  }

  private fun makeAudioQuerySortClause() =
    "${MediaStore.Audio.ArtistColumns.ARTIST}, ${MediaStore.Audio.AlbumColumns.ALBUM}"

  private fun makeAudioQueryWhereClause(lastScan: Date, minimumDuration: Millis) =
    """${MediaStore.MediaColumns.DATE_MODIFIED} > ${TimeUnit.MILLISECONDS.toSeconds(lastScan.time)}
      | AND ${MediaStore.Audio.Media.DURATION} > ${minimumDuration()}""".trimMargin()

  private fun Long.albumArtFor(): Uri = ContentUris.withAppendedId(ARTWORK_URI, this).let { uri ->
    if (uri.exists()) uri else Uri.EMPTY
  }

  private fun Uri.exists(): Boolean {
    return try {
      resolver.openAssetFileDescriptor(this, "r", null)?.use { true } ?: false
    } catch (e: Exception) {
      false
    }
  }
}

private fun Cursor.indexOf(columnName: String): Int = getColumnIndexOrThrow(columnName)

data class AudioInfo(
  val id: AudioContentId,
  val location: Uri,
  val path: File,
  val title: String,
  val dateAdded: Date,
  val dateModified: Date,
  val mimeType: String,
  val size: Long,
  val albumArt: Uri
)
