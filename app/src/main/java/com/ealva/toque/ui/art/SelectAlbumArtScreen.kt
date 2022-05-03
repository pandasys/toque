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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.OriginalSize
import coil.size.Scale
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.asAlbumTitle
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
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.art.SelectAlbumArtViewModel.SelectState
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.nav.back
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

private val LOG by lazyLogger(SelectAlbumArtScreen::class)

@Immutable
@Parcelize
data class SelectAlbumArtScreen(
  private val albumId: AlbumId,
  private val albumTitle: AlbumTitle,
  private val artistName: ArtistName
) : ComposeKey(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        SelectAlbumArtViewModel(
          albumId = albumId,
          albumTitle = albumTitle,
          artistName = artistName,
          backstack = backstack,
          audioMediaDao = get(),
          musicInfoProvider = get(),
          appPrefs = get(AppPrefs.QUALIFIER)
        )
      )
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<SelectAlbumArtViewModel>()
    val selectState = viewModel.selectStateFlow.collectAsState()

    SelectAlbumArt(
      state = selectState.value,
      search = { viewModel.search() },
      reset = { viewModel.reset() },
      selected = { remoteImage -> viewModel.selectImage(remoteImage) },
      albumChanged = { album -> viewModel.setAlbum(album) },
      artistChanged = { artist -> viewModel.setArtist(artist) }
    )
  }
}

@Composable
private fun SelectAlbumArt(
  state: SelectState,
  search: () -> Unit,
  reset: () -> Unit,
  selected: (RemoteImage) -> Unit,
  albumChanged: (String) -> Unit,
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
    AlbumArtist(state.albumTitle, state.artistName, albumChanged, artistChanged)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkGrid(
  images: List<RemoteImage>,
  selected: (RemoteImage) -> Unit
) {
  val screenConfig = LocalScreenConfig.current
  val minSize = if (screenConfig.inPortrait) {
    (screenConfig.screenWidthDp / 2) - 12.dp
  } else {
    (screenConfig.screenWidthDp / 4) - 20.dp
  }
  LazyVerticalGrid(
    cells = GridCells.Adaptive(minSize = minSize),
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(
      start = 4.dp,
      top = 4.dp,
      end = 4.dp,
      bottom = screenConfig.getNavPlusBottomSheetHeight(true)
    )
  ) {
    items(items = images) { item -> RemoteImageItem(item, selected, minSize) }
  }
}

@Composable
fun RemoteImageItem(item: RemoteImage, selected: (RemoteImage) -> Unit, minSize: Dp) {
  Card(modifier = Modifier.padding(bottom = 6.dp)) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(minSize)
          .padding(2.dp)
          .clickable { selected(item) }
      ) {
        Image(
          painter = rememberImagePainter(
            data = item.location,
            builder = {
              scale(Scale.FIT)
              error(R.drawable.ic_album)
            }
          ),
          contentDescription = stringResource(R.string.AlbumArt),
          modifier = Modifier.fillMaxSize()
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .width(minSize)
          .padding(start = 4.dp, top = 2.dp, end = 4.dp, bottom = 2.dp)
      ) {
        val text = item.actualSize?.let { size -> "${size.width} x ${size.height}" } ?: "Unknown"
        Text(
          text = text,
          style = toqueTypography.body2
        )
        Spacer(modifier = Modifier.weight(.9F))
        Image(
          painter = painterResource(id = item.sourceLogoDrawableRes),
          contentDescription = "Source",
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
fun AlbumArtist(
  albumTitle: String,
  artistName: String,
  albumChanged: (String) -> Unit,
  artistChanged: (String) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center
  ) {
    OutlinedTextField(
      modifier = Modifier
        .weight(.45F)
        .padding(end = 2.dp),
      value = albumTitle,
      textStyle = toqueTypography.body2,
      maxLines = 1,
      singleLine = true,
      onValueChange = { albumChanged(it) },
      label = { Text(text = stringResource(id = R.string.Album)) }
    )
    OutlinedTextField(
      modifier = Modifier
        .weight(.45F)
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

interface SelectAlbumArtViewModel {
  @Immutable
  data class SelectState(
    val artistName: String,
    val albumTitle: String,
    val searching: Boolean,
    val images: List<RemoteImage>
  )

  val selectStateFlow: StateFlow<SelectState>

  fun setArtist(artistName: String)
  fun setAlbum(albumTitle: String)

  fun search()
  fun reset()

  fun selectImage(image: RemoteImage)

  fun goBack()

  companion object {
    operator fun invoke(
      albumId: AlbumId,
      albumTitle: AlbumTitle,
      artistName: ArtistName,
      backstack: Backstack,
      audioMediaDao: AudioMediaDao,
      musicInfoProvider: MusicInfoProvider,
      appPrefs: AppPrefsSingleton,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): SelectAlbumArtViewModel =
      SelectAlbumArtViewModelImpl(
        albumId,
        albumTitle,
        artistName,
        backstack,
        audioMediaDao,
        musicInfoProvider,
        appPrefs,
        dispatcher
      )
  }
}

private class SelectAlbumArtViewModelImpl(
  private val albumId: AlbumId,
  private val albumTitle: AlbumTitle,
  private val artistName: ArtistName,
  private val backstack: Backstack,
  private val audioMediaDao: AudioMediaDao,
  private val musicInfoProvider: MusicInfoProvider,
  private val appPrefs: AppPrefsSingleton,
  private val dispatcher: CoroutineDispatcher
) : SelectAlbumArtViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var allowHighRes = true
  private val acceptableSizes = makeAcceptableSizes()
  private var album = albumTitle.value
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

  override fun setAlbum(albumTitle: String) {
    cancelSearch()
    album = albumTitle
    selectStateFlow.update { state -> state.copy(albumTitle = albumTitle) }
  }

  override fun search() {
    cancelSearch()
    updateSearchingState(true)
    searchJob = scope.launch {
      musicInfoProvider.getMusicInfoService()
        .artFinder
        .findAlbumArt(artist.asArtistName, album.asAlbumTitle)
        .onStart { newSearchStarted() }
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
      val drawable = appContext.imageLoader.execute(request).drawable
      val bitmapDrawable = drawable as? BitmapDrawable
      if (bitmapDrawable != null) {
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
    album = albumTitle.value
    search()
  }

  private fun newSearchStarted() {
    imageList.clear()
    selectStateFlow.update { makeCurrentState() }
  }

  override fun selectImage(image: RemoteImage) {
    audioMediaDao.albumDao.setAlbumArt(albumId, image.location, Uri.EMPTY)
    goBack()
  }

  private fun makeCurrentState(): SelectState = SelectState(
    artistName = artist,
    albumTitle = album,
    searching = isSearching,
    images = imageList.toList()
  )

  override fun goBack() {
    backstack.back()
  }
}
