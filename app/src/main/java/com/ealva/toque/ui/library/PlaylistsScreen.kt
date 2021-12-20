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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoEmptyResult
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.PlaylistDescription
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asPlaylistIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.LocalAudioQueueOps.Op
import com.ealva.toque.ui.library.PlaylistsViewModel.PlaylistInfo
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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
    val playlists = viewModel.playlistsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      TitleBar(
        categoryItem = viewModel.categoryItem,
        newSmartPlaylist = { viewModel.newSmartPlaylist() }
      )
      LibraryItemsActions(
        itemCount = playlists.value.size,
        selectedItems = selected.value,
        viewModel = viewModel
      )
      AllPlaylists(
        list = playlists.value,
        selected = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) },
        editSmartPlaylist = { viewModel.editSmartPlaylist(it) }
      )
    }
  }

  @Composable
  private fun TitleBar(
    categoryItem: LibraryCategories.CategoryItem,
    newSmartPlaylist: () -> Unit
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      CategoryTitleBar(categoryItem)
      Spacer(modifier = Modifier.weight(1.0F, fill = true))
      var expanded by remember { mutableStateOf(false) }
      Box(
        modifier = Modifier
          .size(42.dp)
          .padding(end = 8.dp)
          .clickable { expanded = true },

        ) {
        Icon(
          painter = rememberImagePainter(data = R.drawable.ic_more_vert),
          contentDescription = stringResource(id = R.string.EmbeddedArtwork),
          modifier = Modifier.size(40.dp),
          tint = LocalContentColor.current
        )
        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
          DropdownMenuItem(
            onClick = {
              expanded = false
              newSmartPlaylist()
            }
          ) {
            Text(text = stringResource(id = R.string.NewSmartPlaylist))
          }
        }
      }
    }
  }
}

@Composable
private fun AllPlaylists(
  list: List<PlaylistInfo>,
  selected: SelectedItems<PlaylistId>,
  itemClicked: (PlaylistInfo) -> Unit,
  itemLongClicked: (PlaylistInfo) -> Unit,
  editSmartPlaylist: (PlaylistInfo) -> Unit
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
          itemLongClicked = itemLongClicked,
          editSmartPlaylist = editSmartPlaylist
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
  itemLongClicked: (PlaylistInfo) -> Unit,
  editSmartPlaylist: (PlaylistInfo) -> Unit
) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    ListItem(
      modifier = Modifier
        .weight(.80F)
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
      text = {
        Text(text = playlistInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
      },
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
    if (playlistInfo.type == PlayListType.Rules) {
      IconButton(
        modifier = Modifier
          .weight(.15F)
          .size(50.dp),
        onClick = { editSmartPlaylist(playlistInfo) }
      ) {
        Icon(
          painter = rememberImagePainter(data = R.drawable.ic_edit),
          contentDescription = "Item menu",
          modifier = Modifier.size(26.dp)
        )
      }
    }
  }
}

private interface PlaylistsViewModel : ActionsViewModel {
  @Immutable
  @Parcelize
  data class PlaylistInfo(
    val id: PlaylistId,
    val name: PlaylistName,
    val type: PlayListType,
    val songCount: Int
  ) : Parcelable

  val categoryItem: LibraryCategories.CategoryItem
  val playlistsFlow: StateFlow<List<PlaylistInfo>>
  val selectedItems: SelectedItemsFlow<PlaylistId>

  fun itemClicked(playlistInfo: PlaylistInfo)
  fun itemLongClicked(playlistInfo: PlaylistInfo)

  fun newSmartPlaylist()
  fun editSmartPlaylist(playlistInfo: PlaylistInfo)

  companion object {
    operator fun invoke(
      key: ComposeKey,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
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
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : PlaylistsViewModel, ScopedServices.Registered, ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private val playlistDao: PlaylistDao = audioMediaDao.playlistDao

  private val categories = LibraryCategories()

  override val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val playlistsFlow = MutableStateFlow<List<PlaylistInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<PlaylistId>()
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun selectAll() = selectedItems.selectAll(getPlaylistsKeys())
  private fun getPlaylistsKeys() = playlistsFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private fun offSelectMode() = selectedItems.turnOffSelectionMode()

  private suspend fun getMediaList(): Result<CategoryMediaList, DaoMessage> =
    makeCategoryMediaList(getSelectedPlaylists())

  private suspend fun makeCategoryMediaList(playlists: List<PlaylistInfo>) = audioMediaDao
    .getMediaForPlaylists(
      playlistIds = playlists
        .mapTo(LongArrayList(512)) { it.id.value }
        .asPlaylistIdList,
      removeDuplicates = !appPrefs.instance().allowDuplicates()
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { DaoEmptyResult }
    .map { idList -> CategoryMediaList(idList, CategoryToken(playlists.last().id)) }

  private fun getSelectedPlaylists() = playlistsFlow.value
    .filterIfHasSelection(selectedItems.value) { it.id }

  override fun play() {
    scope.launch { localQueueOps.doOp(Op.Play, ::getMediaList, ::offSelectMode) }
  }

  override fun shuffle() {
    scope.launch { localQueueOps.doOp(Op.Shuffle, ::getMediaList, ::offSelectMode) }
  }

  override fun playNext() {
    scope.launch { localQueueOps.doOp(Op.PlayNext, ::getMediaList, ::offSelectMode) }
  }

  override fun addToUpNext() {
    scope.launch { localQueueOps.doOp(Op.AddToUpNext, ::getMediaList, ::offSelectMode) }
  }

  override fun addToPlaylist() {
    scope.launch { localQueueOps.doOp(Op.AddToPlaylist, ::getMediaList, ::offSelectMode) }
  }

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    playlistDao
      .playlistDaoEvents
      .onStart { requestPlaylists() }
      .onEach { requestPlaylists() }
      .catch { cause -> LOG.e(cause) { it("Error collecting PlaylistDao events") } }
      .onCompletion { LOG._i { it("Completed collection PlaylistDao events") } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
    selectedItems.turnOffSelectionMode()
    playlistsFlow.value = emptyList()
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
    playlistsFlow.value = list.mapTo(ArrayList(list.size)) {
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

  override fun newSmartPlaylist() {
    backstack.goToScreen(SmartPlaylistEditorScreen())
  }

  override fun editSmartPlaylist(playlistInfo: PlaylistInfo) {
    if (playlistInfo.type == PlayListType.Rules) {
      backstack.goToScreen(SmartPlaylistEditorScreen(playlistInfo.id))
    }
  }

  private fun goToPlaylistSongs(playlistInfo: PlaylistInfo) =
    backstack.goToScreen(PlaylistSongsScreen(playlistInfo.id, playlistInfo.type))

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
