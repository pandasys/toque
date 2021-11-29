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

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.PlaylistDescription
import com.ealva.toque.db.SongListType
import com.ealva.toque.log._e
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asPlaylistIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.PlaylistsViewModel.PlaylistInfo
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.statebundle.StateBundle
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(PlaylistsScreen::class)

@Immutable
@Parcelize
data class PlaylistsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {

  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    serviceBinder.add(
      PlaylistsViewModel(
        key,
        get(),
        serviceBinder.lookup(),
        get(AppPrefs.QUALIFIER),
        serviceBinder.backstack
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<PlaylistsViewModel>()
    val playlists = viewModel.playlists.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      CategoryTitleBar(viewModel.categoryItem)
      LibraryItemsActions(
        itemCount = playlists.value.size,
        inSelectionMode = selected.value.inSelectionMode,
        selectedCount = selected.value.selectedCount,
        play = { viewModel.play() },
        shuffle = { /*viewModel.shuffle()*/ },
        playNext = { /*viewModel.playNext()*/ },
        addToUpNext = { /*viewModel.addToUpNext()*/ },
        addToPlaylist = { },
        selectAllOrNone = { /*all -> if (all) viewModel.selectAll() else viewModel.clearSelection()*/ },
        startSearch = {}
      )
      AllPlaylists(
        list = playlists.value,
        selected = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) }
      )
    }
  }
}

@Composable
private fun AllPlaylists(
  list: List<PlaylistInfo>,
  selected: SelectedItems<PlaylistId>,
  itemClicked: (PlaylistInfo) -> Unit,
  itemLongClicked: (PlaylistInfo) -> Unit
) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
      .padding(top = 18.dp, bottom = config.getNavPlusBottomSheetHeight(isExpanded = true))
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      )
    ) {
      items(items = list, key = { it.id }) { playlistInfo ->
        PlaylistItem(
          playlistInfo = playlistInfo,
          isSelected = selected.isSelected(playlistInfo.id),
          itemClicked = itemClicked,
          itemLongClicked = itemLongClicked
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItem(
  playlistInfo: PlaylistInfo,
  isSelected: Boolean,
  itemClicked: (PlaylistInfo) -> Unit,
  itemLongClicked: (PlaylistInfo) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(MaterialTheme.toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(playlistInfo) },
        onLongClick = { itemLongClicked(playlistInfo) }
      ),
    icon = {
      Icon(
        painter = rememberImagePainter(data = playlistInfo.type.icon),
        contentDescription = "Playlist Icon",
        modifier = Modifier.size(40.dp)
      )
    },
    text = { Text(text = playlistInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          playlistInfo.songCount,
          playlistInfo.songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private interface PlaylistsViewModel {
  @Immutable
  @Parcelize
  data class PlaylistInfo(
    val id: PlaylistId,
    val name: PlaylistName,
    val type: PlayListType,
    val songCount: Int
  ) : Parcelable

  val categoryItem: LibraryCategories.CategoryItem
  val playlists: StateFlow<List<PlaylistInfo>>
  val selectedItems: SelectedItemsFlow<PlaylistId>

  fun itemClicked(playlistInfo: PlaylistInfo)
  fun itemLongClicked(playlistInfo: PlaylistInfo)
  fun play()

  companion object {
    operator fun invoke(
      key: ComposeKey,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueModel,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): PlaylistsViewModel =
      PlaylistsViewModelImpl(
        key,
        audioMediaDao,
        localAudioQueueModel,
        appPrefs,
        backstack,
        dispatcher
      )
  }
}

private class PlaylistsViewModelImpl(
  private val key: ComposeKey,
  private val audioMediaDao: AudioMediaDao,
  private val localAudioQueueModel: LocalAudioQueueModel,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : PlaylistsViewModel, ScopedServices.Activated, ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private val playlistDao: PlaylistDao = audioMediaDao.playlistDao

  private val categories = LibraryCategories()

  override val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val playlists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<PlaylistId>()

  override fun play() {
    scope.launch {
      when (val result = makeAudioIdList(getSelectedPlaylists())) {
        is Ok -> if (localAudioQueueModel.play(result.value).wasExecuted)
          selectedItems.turnOffSelectionMode()
        is Err -> LOG._e { it("Error getting media list. %s", result.error) }
      }
    }
  }

  suspend fun makeAudioIdList(playlists: List<PlaylistInfo>): Result<AudioIdList, DaoMessage> {
    val title = playlists.lastOrNull()?.name ?: PlaylistName.UNKNOWN
    return audioMediaDao
      .getMediaForPlaylists(
        playlistIds = playlists
          .mapTo(LongArrayList(512)) { it.id.value }
          .asPlaylistIdList,
        removeDuplicates = !appPrefs.instance().allowDuplicates()
      )
      .map { mediaIdList -> AudioIdList(mediaIdList, title.value, SongListType.Artist) }
  }

  private fun getSelectedPlaylists() = playlists.value
    .filterIfHasSelection(selectedItems.value) { it.id }

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    requestPlaylists()
  }

  override fun onServiceInactive() {
    scope.cancel()
    selectedItems.turnOffSelectionMode()
    playlists.value = emptyList()
  }

  private fun requestPlaylists() {
    scope.launch {
      when (val result = playlistDao.getAllPlaylists()) {
        is Ok -> handlePlaylistList(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handlePlaylistList(list: List<PlaylistDescription>) {
    playlists.value = list.mapTo(ArrayList(list.size)) {
      PlaylistInfo(
        id = it.id,
        name = it.name,
        type = it.type,
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun itemClicked(playlistInfo: PlaylistInfo) =
    selectedItems.ifInSelectionModeToggleElse(playlistInfo.id) { goToPlaylistSongs(playlistInfo) }

  override fun itemLongClicked(playlistInfo: PlaylistInfo) = selectedItems.toggleSelection(
    playlistInfo.id
  )

  private fun goToPlaylistSongs(playlistInfo: PlaylistInfo) {
    backstack.goTo(PlaylistSongsScreen(playlistInfo.id, playlistInfo.name, playlistInfo.type))
  }

  override fun onBackEvent(): Boolean = selectedItems.inSelectionModeThenTurnOff()

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(KEY_MODEL_STATE, PlaylistsViewModelState(selectedItems.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<PlaylistsViewModelState>(KEY_MODEL_STATE)?.let { modelState ->
      selectedItems.value = modelState.selected
    }
  }
}

@Parcelize
private data class PlaylistsViewModelState(val selected: SelectedItems<PlaylistId>) : Parcelable

private const val KEY_MODEL_STATE = "PlaylistsModelState"

