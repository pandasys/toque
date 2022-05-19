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

package com.ealva.toque.ui.library.search

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDescription
import com.ealva.toque.db.SearchDao
import com.ealva.toque.db.SearchTerm
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.sharedtest.AlbumDaoSpy
import com.ealva.toque.sharedtest.ArtistDaoSpy
import com.ealva.toque.sharedtest.AudioMediaDaoSpy
import com.ealva.toque.sharedtest.ComposerDaoSpy
import com.ealva.toque.sharedtest.CoroutineRule
import com.ealva.toque.sharedtest.GenreDaoSpy
import com.ealva.toque.sharedtest.LocalAudioQueueViewModelSpy
import com.ealva.toque.sharedtest.PlaylistDaoSpy
import com.ealva.toque.sharedtest.SearchDaoSpy
import com.ealva.toque.ui.library.LibraryCategories
import com.ealva.toque.ui.library.data.AlbumInfo
import com.ealva.toque.ui.library.data.ArtistInfo
import com.ealva.toque.ui.library.data.ComposerInfo
import com.ealva.toque.ui.library.data.GenreInfo
import com.ealva.toque.ui.library.data.PlaylistInfo
import com.ealva.toque.ui.library.data.SongInfo
import com.ealva.toque.ui.library.search.SearchModel.SearchResult
import com.github.michaelbull.result.Ok
import com.nhaarman.expect.expect
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
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

  private lateinit var searchCategorySpy: SearchCategorySpy
  private lateinit var audioMediaDaoSpy: AudioMediaDaoSpy
  private lateinit var searchDaoSpy: SearchDaoSpy
  private lateinit var albumDaoSpy: AlbumDaoSpy
  private lateinit var artistDaoSpy: ArtistDaoSpy
  private lateinit var genreDaoSpy: GenreDaoSpy
  private lateinit var composerDaoSpy: ComposerDaoSpy
  private lateinit var playlistDaoSpy: PlaylistDaoSpy

  @Before
  fun setup() {
    searchCategorySpy = SearchCategorySpy()
    audioMediaDaoSpy = AudioMediaDaoSpy()
    albumDaoSpy = AlbumDaoSpy().apply {
      _getAllAlbumsReturn = Ok(emptyList())
    }
    artistDaoSpy = ArtistDaoSpy().apply {
      _getSongArtistsReturn = Ok(emptyList())
      _getAlbumArtistsReturn = Ok(emptyList())
    }
    genreDaoSpy = GenreDaoSpy().apply {
      _getAllGenresResult = Ok(emptyList())
    }
    composerDaoSpy = ComposerDaoSpy().apply {
      _getAllComposersResult = Ok(emptyList())
    }
    playlistDaoSpy = PlaylistDaoSpy().apply {
      _getAllPlaylistsResult = Ok(emptyList())
    }
    audioMediaDaoSpy.apply {
      albumDao = albumDaoSpy
      artistDao = artistDaoSpy
      genreDao = genreDaoSpy
      composerDao = composerDaoSpy
      playlistDao = playlistDaoSpy
    }
    searchDaoSpy = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(emptyList())
    }

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
    audioMediaDaoSpy._getAllAudioReturn = Ok(descriptionList)
  }

  @Test
  fun testSearch() = runTest {
    searchDaoSpy._setSearchHistoryReturn = Ok(true)
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      model.setCategories(listOf(LibraryCategories.AllSongs))
      val searchTerm = TextFieldValue("blah")
      model.search(searchTerm)
      advanceUntilIdle()
      expect(model.searchCategories.value).toHaveSize(1)
      expect(model.stateFlow.value.query).toBe(searchTerm)
      model.stateFlow.value.results.let { list ->
        expect(list).toHaveSize(1)
      }
    }
  }

  @Test
  fun testSetCategories() = runTest {
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
    searchDaoSpy._getSearchHistoryResult = Ok(listOf(SearchTerm("rock")))
    audioMediaDaoSpy._getAllAudioReturn = Ok(descriptionList)
    audioMediaDaoSpy.albumDao = albumDaoSpy.apply {
      _getAllAlbumsReturn = Ok(emptyList())
      _getAlbumArtForArtistReturn = Ok(Uri.EMPTY)
    }
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      model.setCategories(listOf(LibraryCategories.AllSongs))
      advanceUntilIdle()
      model.searchCategories.value.let { list ->
        list.any { it.libraryCategory == LibraryCategories.AllSongs }
      }
      expect(searchDaoSpy._getSearchHistoryCalled).toBeGreaterThan(0)
    }
  }

  @Test
  fun testSongsSearchCategory() = runTest {
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<SongInfo, MediaId>(LibraryCategories.AllSongs)
        .find(audioMediaDaoSpy, testFilter, testLimit)

      expect(result).toBeInstanceOf<SearchResult<SongInfo, MediaId>>()
      expect(result.category.libraryCategory).toBe(LibraryCategories.AllSongs)
      expect(result.isNotEmpty()).toBe(true)
      expect(audioMediaDaoSpy._getAllAudioCalled).toBeGreaterThan(0)
      expect(audioMediaDaoSpy._getAllAudioFilter).toBe(testFilter)
      expect(audioMediaDaoSpy._getAllAudioLimit).toBe(testLimit)
    }
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
    audioMediaDaoSpy.albumDao = albumDaoSpy
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<AlbumInfo, AlbumId>(LibraryCategories.Albums)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.Albums)
      expect(result.isNotEmpty()).toBe(true)
      expect(albumDaoSpy._getAllAlbumsCalled).toBeGreaterThan(0)
      expect(albumDaoSpy._getAllAlbumsFilter).toBe(testFilter)
      expect(albumDaoSpy._getAllAlbumsLimit).toBe(testLimit)
    }
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
    audioMediaDaoSpy.artistDao = artistDaoSpy
    audioMediaDaoSpy.albumDao = albumDaoSpy

    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<ArtistInfo, ArtistId>(LibraryCategories.Artists)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.Artists)
      expect(result.isNotEmpty()).toBe(true)
      expect(artistDaoSpy._getSongArtistsCount).toBeGreaterThan(0)
      expect(artistDaoSpy._getSongArtistsFilter).toBe(testFilter)
      expect(artistDaoSpy._getSongArtistsLimit).toBe(testLimit)
      expect(albumDaoSpy._getAlbumArtForArtistCalled).toBeGreaterThan(0)
    }
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
    audioMediaDaoSpy.artistDao = artistDaoSpy
    audioMediaDaoSpy.albumDao = albumDaoSpy
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<ArtistInfo, ArtistId>(LibraryCategories.AlbumArtists)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.AlbumArtists)
      expect(result.isNotEmpty()).toBe(true)
      expect(artistDaoSpy._getAlbumArtistsCount).toBeGreaterThan(0)
      expect(artistDaoSpy._getAlbumArtistsFilter).toBe(testFilter)
      expect(artistDaoSpy._getAlbumArtistsLimit).toBe(testLimit)
      expect(albumDaoSpy._getAlbumArtForArtistCalled).toBeGreaterThan(0)
    }
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
    audioMediaDaoSpy.genreDao = genreDaoSpy
    audioMediaDaoSpy.albumDao = albumDaoSpy
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<GenreInfo, GenreId>(LibraryCategories.Genres)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.Genres)
      expect(result.isNotEmpty()).toBe(true)
      expect(genreDaoSpy._getAllGenresCalled).toBeGreaterThan(0)
      expect(genreDaoSpy._getAllGenresFilter).toBe(testFilter)
      expect(genreDaoSpy._getAllGenresLimit).toBe(testLimit)
      expect(albumDaoSpy._getAlbumArtForGenreCalled).toBeGreaterThan(0)
    }
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
    audioMediaDaoSpy.composerDao = composerDaoSpy
    audioMediaDaoSpy.albumDao = albumDaoSpy
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<ComposerInfo, ComposerId>(LibraryCategories.Composers)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.Composers)
      expect(result.isNotEmpty()).toBe(true)
      expect(composerDaoSpy._getAllComposersCalled).toBeGreaterThan(0)
      expect(composerDaoSpy._getAllComposersFilter).toBe(testFilter)
      expect(composerDaoSpy._getAllComposersLimit).toBe(testLimit)
      expect(albumDaoSpy._getAlbumArtForComposerCalled).toBeGreaterThan(0)
    }
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
    audioMediaDaoSpy.playlistDao = playlistDaoSpy
    audioMediaDaoSpy.albumDao = albumDaoSpy
    makeCloseableModel(audioMediaDaoSpy, searchDaoSpy).use { model ->
      val result = model.searchCategoryFor<PlaylistInfo, PlaylistId>(LibraryCategories.Playlists)
        .find(audioMediaDaoSpy, testFilter, testLimit)
      expect(result.category.libraryCategory).toBe(LibraryCategories.Playlists)
      expect(result.isNotEmpty()).toBe(true)
      expect(playlistDaoSpy._getAllPlaylistsCalled).toBeGreaterThan(0)
      expect(playlistDaoSpy._getAllPlaylistsFilter).toBe(testFilter)
      expect(playlistDaoSpy._getAllPlaylistsLimit).toBe(testLimit)
      expect(albumDaoSpy._getAlbumArtForPlaylistCalled).toBeGreaterThan(0)
    }
  }

  private fun makeCloseableModel(
    audioMediaDao: AudioMediaDao,
    searchDao: SearchDao,
  ): CloseableSearchModel =
    CloseableSearchModel(
      SearchModel(
        audioMediaDao = audioMediaDao,
        searchDao = searchDao,
        localAudioQueue = LocalAudioQueueViewModelSpy(),
        goToNowPlaying = { false },
        backstack = Backstack(),
        dispatcher = coroutineRule.testDispatcher
      )
    )
}


@Suppress("UNCHECKED_CAST", "PropertyName")
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
