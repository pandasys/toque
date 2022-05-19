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

package com.ealva.toque.ui.art

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.OriginalSize
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.asArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.musicinfo.service.art.RemoteImage
import com.ealva.musicinfo.service.art.RemoteImageType.FRONT
import com.ealva.musicinfo.service.art.SizeBucket
import com.ealva.toque.R
import com.ealva.toque.app.Toque.Companion.appContext
import com.ealva.toque.art.MusicInfoProvider
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.art.SelectArtistArtViewModel.SelectState
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.settings.AppBarTitle
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(SelectArtistArtScreen::class)

@Immutable
@Parcelize
data class SelectArtistArtScreen(
  private val artistId: ArtistId,
  private val artistName: ArtistName
) : ComposeKey(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        SelectArtistArtViewModel(
          artistId = artistId,
          artistName = artistName,
          backstack = backstack,
          artistDao = get(),
          musicInfoProvider = get(),
          appPrefs = get(AppPrefs.QUALIFIER)
        )
      )
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<SelectArtistArtViewModel>()
    val selectState = viewModel.selectStateFlow.collectAsState()

    SelectArtistArt(
      state = selectState.value,
      search = { viewModel.search() },
      reset = { viewModel.reset() },
      selected = { remoteImage -> viewModel.selectImage(remoteImage) },
      artistChanged = { artist -> viewModel.setArtist(artist) }
    )
  }
}

@Composable
private fun SelectArtistArt(
  state: SelectState,
  search: () -> Unit,
  reset: () -> Unit,
  selected: (RemoteImage) -> Unit,
  artistChanged: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
  ) {
    TitleBar(search, reset)
    if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Artist(state.artistName, artistChanged)
    ArtworkGrid(state.images, selected)
  }
}

@Composable
private fun TitleBar(search: () -> Unit, reset: () -> Unit) {
  TopAppBar(
    title = { AppBarTitle(stringResource(id = R.string.ArtworkSearch)) },
    backgroundColor = toqueColors.surface,
    modifier = Modifier.fillMaxWidth(),
    actions = {
      IconButton(onClick = search) {
        Icon(
          painter = painterResource(id = R.drawable.ic_search),
          contentDescription = "Search",
          modifier = Modifier.size(26.dp),
          tint = LocalContentColor.current
        )
      }
      IconButton(onClick = reset) {
        Icon(
          painter = painterResource(id = R.drawable.ic_restore),
          contentDescription = "Restore original",
          modifier = Modifier.size(26.dp),
          tint = LocalContentColor.current
        )
      }
    }
  )
}

@Composable
private fun Artist(
  artistName: String,
  artistChanged: (String) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center
  ) {
    OutlinedTextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 2.dp),
      value = artistName,
      textStyle = toqueTypography.body2,
      maxLines = 1,
      singleLine = true,
      onValueChange = { artistChanged(it) },
      label = { Text(text = stringResource(id = R.string.AlbumArtist)) }
    )
  }
}

interface SelectArtistArtViewModel {
  @Immutable
  data class SelectState(
    val artistName: String,
    val searching: Boolean,
    val images: List<RemoteImage>
  )

  val selectStateFlow: StateFlow<SelectState>

  fun setArtist(artistName: String)

  fun search()
  fun reset()

  fun selectImage(image: RemoteImage)

  fun goBack()

  companion object {
    operator fun invoke(
      artistId: ArtistId,
      artistName: ArtistName,
      backstack: Backstack,
      artistDao: ArtistDao,
      musicInfoProvider: MusicInfoProvider,
      appPrefs: AppPrefsSingleton,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): SelectArtistArtViewModel =
      SelectArtistArtViewModelImpl(
        artistId,
        artistName,
        backstack,
        artistDao,
        musicInfoProvider,
        appPrefs,
        dispatcher
      )
  }
}

private class SelectArtistArtViewModelImpl(
  private val artistId: ArtistId,
  private val artistName: ArtistName,
  private val backstack: Backstack,
  private val artistDao: ArtistDao,
  private val musicInfoProvider: MusicInfoProvider,
  private val appPrefs: AppPrefsSingleton,
  private val dispatcher: CoroutineDispatcher
) : SelectArtistArtViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var allowHighRes = true
  private val acceptableSizes = makeAcceptableSizes()
  private var artist = artistName.value
  private var isSearching = false
  private val imageList = mutableListOf<RemoteImage>()
  private var searchJob: Job? = null

  private fun makeAcceptableSizes(): Set<SizeBucket> = mutableSetOf(
    SizeBucket.Medium,
    SizeBucket.Large,
    SizeBucket.Original,
  ).apply { if (allowHighRes) add(SizeBucket.ExtraLarge) }

  override val selectStateFlow = MutableStateFlow(makeCurrentState())

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    search()
  }

  override fun onServiceInactive() {
    scope.cancel()
  }

  private fun cancelSearch() {
    if (searchJob != null) {
      searchJob?.cancel()
      searchJob = null
      updateSearchingState(false)
    }
  }

  override fun setArtist(artistName: String) {
    cancelSearch()
    artist = artistName
    selectStateFlow.update { state -> state.copy(artistName = artistName) }
  }

  override fun search() {
    cancelSearch()
    updateSearchingState(true)
    searchJob = scope.launch {
      musicInfoProvider.getMusicInfoService()
        .artFinder
        .findArtistArt(artist.asArtistName)
        .onStart { newSearchStarted() }
        .filterNot { remoteImage -> remoteImage.location == Uri.EMPTY }
        .filter { remoteImage -> remoteImage.types.any { type -> type is FRONT } }
        .filter { remoteImage -> acceptableSizes.contains(remoteImage.sizeBucket) }
        .take(appPrefs.instance().maxImageSearch())
        .map { remoteImage -> ensureSize(remoteImage) }
        .onEach { remoteImage -> addImage(remoteImage) }
        .catch { cause -> LOG.e(cause) { it("MusicInfoService album art flow error") } }
        .onCompletion { cancelSearch() }
        .collect()
    }
  }

  private suspend fun ensureSize(remoteImage: RemoteImage): RemoteImage {
    return if (remoteImage.actualSize != null) remoteImage else {
      val request = ImageRequest.Builder(appContext)
        .data(remoteImage.location)
        .size(OriginalSize)
        .allowHardware(false)
        .build()
      val drawable = appContext.imageLoader.execute(request).drawable as? BitmapDrawable
      if (drawable != null) {
        val bitmap = drawable.bitmap
        remoteImage.copy(actualSize = Size(bitmap.width, bitmap.height))
      } else remoteImage
    }
  }

  private fun addImage(remoteImage: RemoteImage) {
    imageList.add(remoteImage)
    selectStateFlow.update { state -> state.copy(images = imageList.toList()) }
  }

  private fun updateSearchingState(searching: Boolean) {
    isSearching = searching
    selectStateFlow.update { state -> state.copy(searching = searching) }
  }

  override fun reset() {
    searchJob?.cancel()
    artist = artistName.value
    search()
  }

  private fun newSearchStarted() {
    imageList.clear()
    selectStateFlow.update { makeCurrentState() }
  }

  override fun selectImage(image: RemoteImage) {
    artistDao.setArtistArt(artistId, image.location, Uri.EMPTY)
    goBack()
  }

  private fun makeCurrentState(): SelectState = SelectState(
    artistName = artist,
    searching = isSearching,
    images = imageList.toList()
  )

  override fun goBack() {
    backstack.backIfAllowed()
  }
}
