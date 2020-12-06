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

package com.ealva.toque.db

import android.net.Uri
import androidx.core.net.toUri
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.log._e
import com.ealva.toque.media.MediaMetadata
import com.ealva.toque.media.MediaMetadataParser
import com.ealva.toque.prefs.AppPreferences
import com.ealva.welite.db.Database
import com.ealva.welite.db.Transaction
import java.io.File

private val LOG by lazyLogger(MediaDao::class)

interface MediaDao {
  /**
   * Insert or update a list of audio media.
   */
  suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    parser: MediaMetadataParser,
    appPrefs: AppPreferences
  )

  companion object {
    operator fun invoke(
      db: Database,
      genreDao: GenreDao
    ): MediaDao {
      return MediaDaoImpl(db, genreDao)
    }
  }
}

private fun AudioInfo.uriToParse(): Uri {
  val file = File(path)
  return if (file.exists()) file.toUri() else location
}

private class MediaDaoImpl(
  private val db: Database,
  private val genreDao: GenreDao
) : MediaDao {
  /**
   * Parse the file tags and create/update all media relationships. Usually the list would be of one
   * album/artist. There is no contract that the list is only one album/artist or that every track
   * on the album is included.
   */
  override suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    parser: MediaMetadataParser,
    appPrefs: AppPreferences
  ) = db.transaction {
    audioInfoList.forEach { audioInfo ->
      parser.parseMetadata(audioInfo.uriToParse()).use { metadata ->
        upsertAudio(audioInfo, metadata, appPrefs)
      }
    }
  }

  private suspend fun Transaction.upsertAudio(
    audioInfo: AudioInfo,
    metadata: MediaMetadata,
    prefs: AppPreferences
  ) {
    val duration = metadata.duration
    if (shouldNotIgnore(prefs, duration)) {
      @Suppress("UNUSED_VARIABLE")
      val genreIds: GenreIdList = genreDao.createOrGetGenreIds(this, metadata.genres)
      LOG._e { it("%s %s", audioInfo.title, genreIds) }
    } else {
      logIgnoring(audioInfo, duration, prefs)
    }
  }

  private fun shouldNotIgnore(
    prefs: AppPreferences,
    duration: Long
  ) = !prefs.ignoreSmallFiles() || duration > prefs.ignoreThreshold()

  private fun logIgnoring(audioInfo: AudioInfo, duration: Long, prefs: AppPreferences) {
    LOG.i {
      it(
        "Ignoring %s duration:%d < threshold:%d",
        audioInfo.title,
        duration,
        prefs.ignoreThreshold()
      )
    }
  }
}
