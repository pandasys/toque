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

package com.ealva.toque.ui.library.data

import com.ealva.toque.common.Duplicates
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoResult
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.asAlbumIdList
import com.ealva.toque.persist.asArtistIdList
import com.ealva.toque.persist.asComposerIdList
import com.ealva.toque.persist.asGenreIdList
import com.ealva.toque.persist.asPlaylistIdList
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import it.unimi.dsi.fastutil.longs.LongArrayList

@JvmName("makeAlbumCategoryMediaList")
suspend fun List<AlbumInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao
): Result<CategoryMediaList, Throwable> = audioMediaDao
  .getMediaForAlbums(mapTo(LongArrayList(512)) { it.id.value }.asAlbumIdList)
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }

@JvmName("makeArtistCategoryMediaList")
suspend fun List<ArtistInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao
): Result<CategoryMediaList, Throwable> = audioMediaDao
  .getMediaForArtists(mapTo(LongArrayList(512)) { it.id.value }.asArtistIdList)
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }

suspend fun List<AlbumInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao,
  artistId: ArtistId
): Result<CategoryMediaList, Throwable> = audioMediaDao
  .getMediaForAlbums(mapTo(LongArrayList(512)) { it.id.value }.asAlbumIdList, artistId)
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }

@JvmName("makeComposerCategoryMediaList")
suspend fun List<ComposerInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao
): Result<CategoryMediaList, Throwable> = audioMediaDao
  .getMediaForComposers(mapTo(LongArrayList(512)) { it.id.value }.asComposerIdList)
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }

@JvmName("makeGenreCategoryMediaList")
suspend fun List<GenreInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao
): Result<CategoryMediaList, Throwable> = audioMediaDao
  .getMediaForGenres(mapTo(LongArrayList(512)) { it.id.value }.asGenreIdList)
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }

suspend fun List<PlaylistInfo>.makeCategoryMediaList(
  audioMediaDao: AudioMediaDao,
  duplicates: Duplicates
): DaoResult<CategoryMediaList> = audioMediaDao
  .getMediaForPlaylists(
    playlistIds = mapTo(LongArrayList(512)) { it.id.value }.asPlaylistIdList,
    removeDuplicates = duplicates.doNotAllow
  )
  .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
  .map { idList -> CategoryMediaList(idList, CategoryToken(last().id)) }
