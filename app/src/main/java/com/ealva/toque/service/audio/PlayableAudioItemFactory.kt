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

import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.asMillis
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.persist.HasId
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.WakeLockFactory
import com.ealva.toque.service.session.common.Metadata
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.LibVlcSingleton
import com.ealva.toque.service.vlc.VlcAudioItem
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import it.unimi.dsi.fastutil.longs.LongCollection

private val LOG by lazyLogger(PlayableAudioItemFactory::class)

interface PlayableAudioItemFactory {
  suspend fun makeUpNextQueue(
    shuffled: Boolean,
    request: AvPlayer.FocusRequest,
    sharedPlayerState: SharedPlayerState
  ): MutableList<PlayableAudioItem>

  /**
   * We want to get all the shuffled IDs in correct order, then create a new list using the same
   * objects from the [upNextQueue]. Because [upNextQueue] may contain duplicates we need a
   * data structure to support that. We'll use a map with ID as key and entry will be a list.
   * Removing from the end of the list won't require any copying
   */
  suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T>

  suspend fun makeNewQueueItems(
    idList: LongCollection,
    request: AvPlayer.FocusRequest,
    sharedPlayerState: SharedPlayerState
  ): QueueList

  companion object {
    operator fun invoke(
      audioMediaDao: AudioMediaDao,
      mediaFileStore: MediaFileStore,
      libVlcSingleton: LibVlcSingleton,
      wakeLockFactory: WakeLockFactory,
      appPrefsSingleton: AppPrefsSingleton,
      libVlcPrefsSingleton: LibVlcPrefsSingleton
    ): PlayableAudioItemFactory = PlayableAudioItemFactoryImpl(
      audioMediaDao,
      mediaFileStore,
      libVlcSingleton,
      libVlcPrefsSingleton,
      appPrefsSingleton,
      wakeLockFactory
    )
  }
}

private class PlayableAudioItemFactoryImpl(
  private val audioMediaDao: AudioMediaDao,
  private val mediaFileStore: MediaFileStore,
  private val libVlcSingleton: LibVlcSingleton,
  private val libVlcPrefsSingleton: LibVlcPrefsSingleton,
  private val appPrefsSingleton: AppPrefsSingleton,
  private val wakeLockFactory: WakeLockFactory
) : PlayableAudioItemFactory {
  override suspend fun makeUpNextQueue(
    shuffled: Boolean,
    request: AvPlayer.FocusRequest,
    sharedPlayerState: SharedPlayerState
  ): MutableList<PlayableAudioItem> = audioMediaDao
    .getAudioQueueItems(shuffled)
    .map { list ->
      ArrayList<PlayableAudioItem>(list.size).apply {
        val appPrefs = appPrefsSingleton.instance()
        val libVlcPrefs = libVlcPrefsSingleton.instance()
        list.forEach { itemData ->
          add(
            VlcAudioItem(
              Metadata(
                itemData.id,
                itemData.title,
                itemData.albumTitle,
                itemData.albumArtist,
                ArtistName(itemData.artists.joinToString { name -> name.value.trim(',') }),
                itemData.duration,
                itemData.trackNumber,
                itemData.localAlbumArt,
                itemData.albumArt,
                itemData.rating,
                itemData.location,
                itemData.fileUri
              ),
              itemData.displayName,
              itemData.albumId,
              itemData.artists,
              libVlcSingleton,
              mediaFileStore,
              sharedPlayerState,
              appPrefs,
              libVlcPrefs,
              wakeLockFactory,
              request,
            )
          )
        }
      }
    }
    .onFailure { cause -> LOG.e(cause) { it("Failed to get UpNextQueue data.") } }
    .getOrElse { mutableListOf() }

  override suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T> =
    audioMediaDao.makeShuffledQueue(upNextQueue)

  override suspend fun makeNewQueueItems(
    idList: LongCollection,
    request: AvPlayer.FocusRequest,
    sharedPlayerState: SharedPlayerState
  ): QueueList = audioMediaDao
    .getAudioItemsForQueue(idList)
    .map { itemList ->
      ArrayList<PlayableAudioItem>(itemList.size).apply {
        val appPrefs = appPrefsSingleton.instance()
        val libVlcPrefs = libVlcPrefsSingleton.instance()
        itemList.forEach { itemData ->
          add(
            VlcAudioItem(
              Metadata(
                itemData.id,
                itemData.title,
                itemData.albumTitle,
                itemData.albumArtist,
                ArtistName(itemData.artists.joinToString { name -> name.value.trim(',') }),
                itemData.duration,
                itemData.trackNumber,
                itemData.localAlbumArt,
                itemData.albumArt,
                itemData.rating,
                itemData.location,
                itemData.fileUri
              ),
              itemData.displayName,
              itemData.albumId,
              itemData.artists,
              libVlcSingleton,
              mediaFileStore,
              sharedPlayerState,
              appPrefs,
              libVlcPrefs,
              wakeLockFactory,
              request,
            )
          )
        }
      }
    }
    .onFailure { cause -> LOG.e(cause) { it("Make new queue items failed") } }
    .getOrElse { emptyList() }
}
