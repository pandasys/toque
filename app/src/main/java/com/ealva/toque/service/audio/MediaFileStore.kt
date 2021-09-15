/*
 * Copyright 2021 eAlva.com
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

import com.ealva.toque.common.Millis
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating

/**
 * This interface is passed to [PlayableAudioItem]s to allow them to update themselves
 */
interface MediaFileStore {
  fun incrementPlayedCountAsync(id: MediaId)

  fun incrementSkippedCountAsync(id: MediaId)

  fun updateDurationAsync(id: MediaId, duration: Millis)

  /**
   * Sets and returns [newRating] if successful, else throws
   */
  suspend fun setRating(id: MediaId, newRating: Rating): Rating

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
    operator fun invoke(audioMediaDao: AudioMediaDao): MediaFileStore {
      return MediaFileStoreImpl(audioMediaDao)
    }
  }
}

private class MediaFileStoreImpl(private val audioMediaDao: AudioMediaDao) : MediaFileStore {
  override fun incrementPlayedCountAsync(id: MediaId) {
    audioMediaDao.incrementPlayedCount(id)
  }

  override fun incrementSkippedCountAsync(id: MediaId) {
    audioMediaDao.incrementSkippedCount(id)
  }

  override fun updateDurationAsync(id: MediaId, duration: Millis) {
    audioMediaDao.updateDuration(id, duration)
  }

  override suspend fun setRating(id: MediaId, newRating: Rating): Rating =
    audioMediaDao.setRating(id, newRating)
}
