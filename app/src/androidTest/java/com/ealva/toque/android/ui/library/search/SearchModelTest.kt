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

package com.ealva.toque.android.ui.library.search

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.toque.android.sharedtest.AlbumDaoSpy
import com.ealva.toque.android.sharedtest.ArtistDaoSpy
import com.ealva.toque.android.sharedtest.AudioMediaDaoSpy
import com.ealva.toque.android.sharedtest.ComposerDaoSpy
import com.ealva.toque.android.sharedtest.GenreDaoSpy
import com.ealva.toque.android.sharedtest.PlaylistDaoSpy
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asTitle
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDescription
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.ui.library.AlbumsViewModel.AlbumInfo
import com.ealva.toque.ui.library.ArtistsViewModel.ArtistInfo
import com.ealva.toque.ui.library.ComposersViewModel.ComposerInfo
import com.ealva.toque.ui.library.GenresViewModel.GenreInfo
import com.ealva.toque.ui.library.PlaylistsViewModel.PlaylistInfo
import com.ealva.toque.ui.library.SongsViewModel.SongInfo
import com.ealva.toque.ui.library.search.SearchCategorySpy
import com.ealva.toque.ui.library.search.SearchModel
import com.ealva.toque.ui.library.search.SearchModel.SearchCategory
import com.ealva.toque.ui.library.search.SearchModel.SearchResult
import com.github.michaelbull.result.Ok
import com.nhaarman.expect.expect
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchModelTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @get:Rule
  var coroutineRule = CoroutineRule()

  private val testFilter = "xxx".wrapAsFilter()
  private val testLimit = Limit(1)


  @Test
  fun testSearch() = runTest {
    val category = SearchCategorySpy()
    val songInfo = SongInfo(
      MediaId.INVALID,
      "".asTitle,
      Duration.ZERO,
      Rating.RATING_0,
      AlbumTitle.UNKNOWN,
      ArtistName.UNKNOWN,
      Uri.EMPTY
    )
    category._findReturn = SearchResult(
      category,
      listOf(songInfo)
    )
    val audioMediaDao = AudioMediaDaoSpy()
    makeCloseableModel(audioMediaDao).use { model ->
      val categoryList = listOf(category)
      model.setCategories(categoryList)
      val searchTerm = "blah"
      model.search(searchTerm)
      advanceUntilIdle()
      expect(model.searchCategories.value).toBe(categoryList)
      expect(model.searchFlow.value).toBe(searchTerm)
      expect(category._findCalled).toBe(1)
      expect(category._findFilter).toBe(searchTerm.wrapAsFilter())
      model.searchResults.value.let { list ->
        expect(list).toHaveSize(1)
      }
    }
  }

  @Test
  fun testSetCategories() = runTest {
    val audioMediaDao = AudioMediaDaoSpy()
    makeCloseableModel(audioMediaDao).use { model ->
      model.setCategories(emptyList())
      advanceUntilIdle()
      model.searchCategories.value.let { list ->
        expect(list).toBe(SearchCategory.available)
      }
    }
  }

  @Test
  fun testSongsSearchCategory() = runTest {
    val audioDescription = AudioDescription(
      MediaId.INVALID,
      Title.UNKNOWN,
      Duration.ZERO,
      Rating.RATING_0,
      AlbumTitle.UNKNOWN,
      ArtistName.UNKNOWN,
      Uri.EMPTY,
      Uri.EMPTY
    )
    val descriptionList = listOf(audioDescription)
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      _getAllAudioReturn = Ok(descriptionList)
    }

    val result = SearchCategory.Songs
      .find(audioMediaDaoSpy, testFilter, testLimit)

    expect(result).toBeInstanceOf<SearchResult<SongInfo>>()
    expect(result.category).toBe(SearchCategory.Songs)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<SongInfo>>()
    expect(audioMediaDaoSpy._getAllAudioCalled).toBeGreaterThan(0)
    expect(audioMediaDaoSpy._getAllAudioFilter).toBe(testFilter)
    expect(audioMediaDaoSpy._getAllAudioLimit).toBe(testLimit)
  }

  @Test
  fun testAlbumsSearchCategory() = runTest {
    val albumDescription = AlbumDescription(
      AlbumId.INVALID,
      AlbumTitle.UNKNOWN,
      ArtistName.UNKNOWN,
      2020,
      Uri.EMPTY,
      Uri.EMPTY,
      23,
      Duration.ZERO
    )
    val descriptionList = listOf(albumDescription)
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAllAlbumsReturn = Ok(descriptionList)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.Albums.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<AlbumInfo>>()
    expect(result.category).toBe(SearchCategory.Albums)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<AlbumInfo>>()
    expect(albumDaoSpy._getAllAlbumsCalled).toBeGreaterThan(0)
    expect(albumDaoSpy._getAllAlbumsFilter).toBe(testFilter)
    expect(albumDaoSpy._getAllAlbumsLimit).toBe(testLimit)
  }

  @Test
  fun testArtistsSearchCategory() = runTest {
    val artistDescription = ArtistDescription(
      ArtistId.INVALID,
      ArtistName.UNKNOWN,
      Uri.EMPTY,
      Uri.EMPTY,
      2,
      10,
      Duration.ZERO
    )
    val descriptionList = listOf(artistDescription)
    val artistDaoSpy = ArtistDaoSpy().apply {
      _getSongArtistsReturn = Ok(descriptionList)
    }
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAlbumArtForArtistReturn = Ok(Uri.EMPTY)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      artistDao = artistDaoSpy
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.Artists.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<ArtistInfo>>()
    expect(result.category).toBe(SearchCategory.Artists)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<ArtistInfo>>()
    expect(artistDaoSpy._getSongArtistsCount).toBeGreaterThan(0)
    expect(artistDaoSpy._getSongArtistsFilter).toBe(testFilter)
    expect(artistDaoSpy._getSongArtistsLimit).toBe(testLimit)
    expect(albumDaoSpy._getAlbumArtForArtistCalled).toBeGreaterThan(0)
  }

  @Test
  fun testAlbumArtistsSearchCategory() = runTest {
    val artistDescription = ArtistDescription(
      ArtistId.INVALID,
      ArtistName.UNKNOWN,
      Uri.EMPTY,
      Uri.EMPTY,
      2,
      10,
      Duration.ZERO
    )
    val descriptionList = listOf(artistDescription)
    val artistDaoSpy = ArtistDaoSpy().apply {
      _getAlbumArtistsReturn = Ok(descriptionList)
    }
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAlbumArtForArtistReturn = Ok(Uri.EMPTY)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      artistDao = artistDaoSpy
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.AlbumArtists.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<ArtistInfo>>()
    expect(result.category).toBe(SearchCategory.AlbumArtists)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<ArtistInfo>>()
    expect(artistDaoSpy._getAlbumArtistsCount).toBeGreaterThan(0)
    expect(artistDaoSpy._getAlbumArtistsFilter).toBe(testFilter)
    expect(artistDaoSpy._getAlbumArtistsLimit).toBe(testLimit)
    expect(albumDaoSpy._getAlbumArtForArtistCalled).toBeGreaterThan(0)
  }

  @Test
  fun testGenresSearchCategory() = runTest {
    val genreDescription = GenreDescription(
      GenreId.INVALID,
      GenreName.UNKNOWN,
      100,
      Duration.ZERO
    )
    val descriptionList = listOf(genreDescription)
    val genreDaoSpy = GenreDaoSpy().apply {
      _getAllGenresResult = Ok(descriptionList)
    }
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAlbumArtForGenreReturn = Ok(Uri.EMPTY)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      genreDao = genreDaoSpy
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.Genres.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<GenreInfo>>()
    expect(result.category).toBe(SearchCategory.Genres)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<GenreInfo>>()
    expect(genreDaoSpy._getAllGenresCalled).toBeGreaterThan(0)
    expect(genreDaoSpy._getAllGenresFilter).toBe(testFilter)
    expect(genreDaoSpy._getAllGenresLimit).toBe(testLimit)
    expect(albumDaoSpy._getAlbumArtForGenreCalled).toBeGreaterThan(0)
  }

  @Test
  fun testComposersSearchCategory() = runTest {
    val composerDescription = ComposerDescription(
      ComposerId.INVALID,
      ComposerName.UNKNOWN,
      100,
      Duration.ZERO
    )
    val descriptionList = listOf(composerDescription)
    val composerDaoSpy = ComposerDaoSpy().apply {
      _getAllComposersResult = Ok(descriptionList)
    }
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAlbumArtForComposerReturn = Ok(Uri.EMPTY)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      composerDao = composerDaoSpy
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.Composers.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<ComposerInfo>>()
    expect(result.category).toBe(SearchCategory.Composers)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<ComposerInfo>>()
    expect(composerDaoSpy._getAllComposersCalled).toBeGreaterThan(0)
    expect(composerDaoSpy._getAllComposersFilter).toBe(testFilter)
    expect(composerDaoSpy._getAllComposersLimit).toBe(testLimit)
    expect(albumDaoSpy._getAlbumArtForComposerCalled).toBeGreaterThan(0)
  }

  @Test
  fun testPlaylistsSearchCategory() = runTest {
    val playlistDescription = PlaylistDescription(
      PlaylistId.INVALID,
      PlaylistName.UNKNOWN,
      PlayListType.Rules,
      100,
      Duration.ZERO
    )
    val descriptionList = listOf(playlistDescription)
    val playlistDaoSpy = PlaylistDaoSpy().apply {
      _getAllPlaylistsResult = Ok(descriptionList)
    }
    val albumDaoSpy = AlbumDaoSpy().apply {
      _getAlbumArtForPlaylistReturn = Ok(Uri.EMPTY)
    }
    val audioMediaDaoSpy = AudioMediaDaoSpy().apply {
      playlistDao = playlistDaoSpy
      albumDao = albumDaoSpy
    }
    val result = SearchCategory.Playlists.find(audioMediaDaoSpy, testFilter, testLimit)
    expect(result).toBeInstanceOf<SearchResult<PlaylistInfo>>()
    expect(result.category).toBe(SearchCategory.Playlists)
    expect(result.list).toHaveSize(1)
    expect(result.list).toBeInstanceOf<List<ComposerInfo>>()
    expect(playlistDaoSpy._getAllPlaylistsCalled).toBeGreaterThan(0)
    expect(playlistDaoSpy._getAllPlaylistsFilter).toBe(testFilter)
    expect(playlistDaoSpy._getAllPlaylistsLimit).toBe(testLimit)
    expect(albumDaoSpy._getAlbumArtForPlaylistCalled).toBeGreaterThan(0)
  }

  private fun makeCloseableModel(audioMediaDao: AudioMediaDao): CloseableSearchModel =
    CloseableSearchModel(SearchModel(audioMediaDao, coroutineRule.testDispatcher))
}


private class CloseableSearchModel(
  realModel: SearchModel
) : SearchModel by realModel, AutoCloseable {
  private var closed = false
  private val registered = (realModel as ScopedServices.Registered).apply { onServiceRegistered() }

  // Should be idempotent, though no test yet relies on that
  override fun close() {
    if (!closed) {
      closed = true
      registered.onServiceUnregistered()
    }
  }
}
