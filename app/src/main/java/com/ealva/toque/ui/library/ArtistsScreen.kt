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

package com.ealva.toque.ui.library

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
import com.ealva.toque.ui.library.ArtistsViewModel.ArtistInfo
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(ArtistsScreen::class)

enum class ArtistType(@StringRes val allSongsRes: Int, @DrawableRes val typeIcon: Int) {
  AlbumArtist(R.string.AllAlbumArtistSongs, R.drawable.ic_account_box),
  SongArtist(R.string.AllArtistSongs, R.drawable.ic_microphone)
}

@Immutable
@Parcelize
class ArtistsScreen(
  private val artistType: ArtistType
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(ArtistsViewModel(get(), artistType, backstack)) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ArtistsViewModel>()
    val artists = viewModel.allArtists.collectAsState()
    val selected = viewModel.selectedItems.asState()
    AllArtistsList(
      list = artists.value,
      selectedItems = selected.value,
      artistType = artistType,
      itemClicked = { artistId, songCount -> viewModel.itemClicked(artistId, songCount) },
      itemLongClicked = { viewModel.itemLongClicked(it) }
    )
  }
}

@Composable
private fun AllArtistsList(
  list: List<ArtistInfo>,
  selectedItems: SelectedItems<ArtistId>,
  artistType: ArtistType,
  itemClicked: (ArtistId, Int) -> Unit,
  itemLongClicked: (ArtistId) -> Unit
) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
      .padding(top = 18.dp, bottom = config.getNavPlusBottomSheetHeight(isExpanded = true))
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      ),
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      items(items = list, key = { it.artistId }) { artistInfo ->
        ArtistItem(
          artistInfo = artistInfo,
          artistType = artistType,
          isSelected = selectedItems.isSelected(artistInfo.artistId),
          itemClicked = itemClicked,
          itemLongClicked = itemLongClicked
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ArtistItem(
  artistInfo: ArtistInfo,
  artistType: ArtistType,
  isSelected: Boolean,
  itemClicked: (ArtistId, Int) -> Unit,
  itemLongClicked: (ArtistId) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(MaterialTheme.toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(artistInfo.artistId, artistInfo.songCount) },
        onLongClick = { itemLongClicked(artistInfo.artistId) }
      ),
    icon = {
      if (artistInfo.artwork != Uri.EMPTY) {
        Image(
          painter = rememberImagePainter(
            data = artistInfo.artwork,
            builder = {
              error(artistType.typeIcon)
              placeholder(artistType.typeIcon)
            }
          ),
          contentDescription = stringResource(R.string.ArtistImage),
          modifier = Modifier.size(40.dp)
        )
      } else {
        Icon(
          painter = rememberImagePainter(data = artistType.typeIcon),
          contentDescription = stringResource(R.string.ArtistImage),
          modifier = Modifier.size(40.dp)
        )
      }
    },
    text = { Text(text = artistInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = { AlbumAndSongCount(artistInfo = artistInfo) },
  )
}

@Composable
private fun AlbumAndSongCount(artistInfo: ArtistInfo) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = LocalContext.current.resources.getQuantityString(
        R.plurals.AlbumCount,
        artistInfo.albumCount,
        artistInfo.albumCount,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = LocalContext.current.resources.getQuantityString(
        R.plurals.SongCount,
        artistInfo.songCount,
        artistInfo.songCount,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private interface ArtistsViewModel {
  @Immutable
  @Parcelize
  data class ArtistInfo(
    val artistId: ArtistId,
    val name: ArtistName,
    val artwork: Uri,
    val albumCount: Int,
    val songCount: Int
  ) : Parcelable

  val allArtists: StateFlow<List<ArtistInfo>>
  val selectedItems: SelectedItemsFlow<ArtistId>

  fun itemClicked(artistId: ArtistId, songCount: Int)
  fun itemLongClicked(artistId: ArtistId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  companion object {
    operator fun invoke(
      artistDao: ArtistDao,
      artistType: ArtistType,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ArtistsViewModel =
      ArtistsViewModelImpl(artistDao, artistType, backstack, dispatcher)
  }
}

private class ArtistsViewModelImpl(
  private val artistDao: ArtistDao,
  private val artistType: ArtistType,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : ArtistsViewModel, ScopedServices.Activated, ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope

  override val allArtists = MutableStateFlow<List<ArtistInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<ArtistId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  private fun goToArtistAlbums(artistId: ArtistId, songCount: Int) {
    val name = allArtists.value
      .find { it.artistId == artistId }
      ?.name ?: ArtistName("")
    backstack.goTo(ArtistAlbumsScreen(artistId, artistType, name, songCount))
  }

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + dispatcher)
    filterFlow
      .drop(1)
      .onEach { requestArtists() }
      .launchIn(scope)

    artistDao.artistDaoEvents
      .onStart { requestArtists() }
      .onEach { requestArtists() }
      .catch { cause -> LOG.e(cause) { it("Error collecting ArtistDao events") } }
      .onCompletion { LOG._i { it("End collecting ArtistDao events") } }
      .launchIn(scope)
  }

  private fun requestArtists() {
    scope.launch {
      when (
        val result = when (artistType) {
          ArtistType.AlbumArtist -> artistDao.getAlbumArtists(filterFlow.value)
          ArtistType.SongArtist -> artistDao.getSongArtists(filterFlow.value)
        }
      ) {
        is Ok -> handleArtistList(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handleArtistList(list: List<ArtistDescription>) {
    allArtists.value = list.mapTo(ArrayList(list.size)) {
      ArtistInfo(
        artistId = it.artistId,
        name = it.name,
        artwork = it.artwork,
        albumCount = it.albumCount.toInt(),
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun itemClicked(artistId: ArtistId, songCount: Int) =
    selectedItems.ifInSelectionModeToggleElse(artistId) { goToArtistAlbums(it, songCount) }

  override fun itemLongClicked(artistId: ArtistId) = selectedItems.toggleSelection(artistId)

  override fun onBackEvent(): Boolean = selectedItems.inSelectionModeThenTurnOff()

  override fun onServiceInactive() {
    scope.cancel()
    allArtists.value = emptyList()
  }

  private val stateKey: String
    get() = artistType.javaClass.name

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(
      stateKey,
      ArtistsViewModelState(selectedItems.value, filterFlow.value)
    )
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<ArtistsViewModelState>(stateKey)?.let { modelState ->
      selectedItems.value = modelState.selected
      filterFlow.value = modelState.filter
    }
  }
}

@Parcelize
private data class ArtistsViewModelState(
  val selected: SelectedItems<ArtistId>,
  val filter: Filter
) : Parcelable

@Preview
@Composable
fun AllArtistsListPreview() {
  val list = listOf(
    ArtistInfo(
      artistId = ArtistId(1),
      name = ArtistName("George Harrison"),
      artwork = Uri.EMPTY,
      albumCount = 12,
      songCount = 85
    ),
    ArtistInfo(
      artistId = ArtistId(2),
      name = ArtistName("John Lennon"),
      artwork = Uri.EMPTY,
      albumCount = 15,
      songCount = 100
    ),
  )
  ProvideScreenConfig(
    screenConfig = makeScreenConfig(
      LocalConfiguration.current,
      LocalDensity.current,
      LocalWindowInsets.current
    )
  ) {
    AllArtistsList(
      list = list,
      SelectedItems(),
      ArtistType.SongArtist,
      itemClicked = { _, _ -> },
      itemLongClicked = {}
    )
  }
}

