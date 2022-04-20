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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.Memento
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
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.PopupMenu
import com.ealva.toque.ui.common.PopupMenuItem
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.PlaylistsViewModel.PlaylistInfo
import com.ealva.toque.ui.library.smart.SmartPlaylistEditorScreen
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.nav.back
import com.ealva.toque.ui.nav.goToRootScreen
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIf
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopeKey
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
import kotlin.time.Duration

private val LOG by lazyLogger(PlaylistsScreen::class)

@Immutable
@Parcelize
data class PlaylistsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    serviceBinder.add(
      PlaylistsViewModel(
        key = key,
        audioMediaDao = get(),
        mainViewModel = serviceBinder.lookup(),
        localAudioQueueModel = serviceBinder.lookup(),
        appPrefs = get(AppPrefs.QUALIFIER),
        backstack = serviceBinder.backstack
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<PlaylistsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val playlists = viewModel.playlistsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        categoryItem = viewModel.categoryItem,
        menuItems = makeMenuItems(viewModel),
        itemCount = playlists.value.size,
        selectedItems = selected.value,
        backTo = stringResource(R.string.Library),
        back = { viewModel.goBack() },
        scrollConnection = scrollConnection
      )
      AllPlaylists(
        list = playlists.value,
        selected = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) },
        editSmartPlaylist = { viewModel.editSmartPlaylist(it) },
        deletePlaylist = { viewModel.deletePlaylist(it) }
      )
    }
  }

  @Composable
  private fun makeMenuItems(viewModel: PlaylistsViewModel) = listOf(
    PopupMenuItem(
      title = stringResource(id = R.string.NewSmartPlaylist),
      onClick = { viewModel.newSmartPlaylist() }
    ),
  )
}

@Composable
private fun AllPlaylists(
  list: List<PlaylistInfo>,
  selected: SelectedItems<PlaylistId>,
  itemClicked: (PlaylistInfo) -> Unit,
  itemLongClicked: (PlaylistInfo) -> Unit,
  editSmartPlaylist: (PlaylistInfo) -> Unit,
  deletePlaylist: (PlaylistInfo) -> Unit
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
          editSmartPlaylist = editSmartPlaylist,
          deletePlaylist = deletePlaylist
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
  editSmartPlaylist: (PlaylistInfo) -> Unit,
  deletePlaylist: (PlaylistInfo) -> Unit
) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    ListItem(
      modifier = Modifier
        .weight(.80F)
        .modifyIf(isSelected) { background(toqueColors.selectedBackground) }
        .combinedClickable(
          onClick = { itemClicked(playlistInfo) },
          onLongClick = { itemLongClicked(playlistInfo) }
        ),
      icon = {
        Image(
          painter = if (playlistInfo.artwork !== Uri.EMPTY) rememberImagePainter(
            data = playlistInfo.artwork,
            builder = { error(playlistInfo.type.icon) }
          ) else painterResource(id = playlistInfo.type.icon),
          contentDescription = stringResource(R.string.Artwork),
          modifier = Modifier.size(56.dp)
        )
      },
      text = { ListItemText(text = playlistInfo.name.value) },
      secondaryText = {
        CountDurationYear(playlistInfo.songCount, playlistInfo.duration, year = 0)
      },
      trailing = {
        PopupMenu(items = playlistInfo.makePopupMenuItems(editSmartPlaylist, deletePlaylist))
      }
    )
  }
}

private fun PlaylistInfo.makePopupMenuItems(
  editSmartPlaylist: (PlaylistInfo) -> Unit,
  deletePlaylist: (PlaylistInfo) -> Unit
): List<PopupMenuItem> {
  return when (type) {
    PlayListType.Rules -> {
      listOf(
        PopupMenuItem(title = fetch(R.string.Edit), onClick = { editSmartPlaylist(this) }),
        PopupMenuItem(title = fetch(R.string.Delete), onClick = { deletePlaylist(this) })
      )
    }
    PlayListType.UserCreated -> {
      listOf(PopupMenuItem(title = fetch(R.string.Delete), onClick = { deletePlaylist(this) }))
    }
    else -> emptyList()
  }
}

private interface PlaylistsViewModel : ActionsViewModel {
  @Immutable
  data class PlaylistInfo(
    val id: PlaylistId,
    val name: PlaylistName,
    val type: PlayListType,
    val songCount: Int,
    val duration: Duration,
    val artwork: Uri
  )

  val categoryItem: LibraryCategories.CategoryItem
  val playlistsFlow: StateFlow<List<PlaylistInfo>>
  val selectedItems: SelectedItemsFlow<PlaylistId>

  fun itemClicked(playlistInfo: PlaylistInfo)
  fun itemLongClicked(playlistInfo: PlaylistInfo)

  fun newSmartPlaylist()
  fun editSmartPlaylist(playlistInfo: PlaylistInfo)
  fun deletePlaylist(playlistInfo: PlaylistInfo)

  fun goBack()

  companion object {
    operator fun invoke(
      key: ComposeKey,
      audioMediaDao: AudioMediaDao,
      mainViewModel: MainViewModel,
      localAudioQueueModel: LocalAudioQueueViewModel,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): PlaylistsViewModel =
      PlaylistsViewModelImpl(
        key,
        audioMediaDao,
        mainViewModel,
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
  private val mainViewModel: MainViewModel,
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : PlaylistsViewModel, ScopedServices.Registered, ScopedServices.HandlesBack, Bundleable {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val playlistDao: PlaylistDao = audioMediaDao.playlistDao
  private val albumDao: AlbumDao = audioMediaDao.albumDao

  private val categories = LibraryCategories()

  override val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val playlistsFlow = MutableStateFlow<List<PlaylistInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<PlaylistId>()
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun selectAll() = selectedItems.selectAll(getPlaylistsKeys())
  private fun getPlaylistsKeys() = playlistsFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private fun selectModeOff() = selectedItems.turnOffSelectionMode()

  private suspend fun selectModeOffMaybeGoHome() {
    selectModeOff()
    if (appPrefs.instance().goToNowPlaying()) backstack.goToRootScreen()
  }

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    makeCategoryMediaList(getSelectedPlaylists())

  private suspend fun makeCategoryMediaList(
    playlists: List<PlaylistInfo>
  ): DaoResult<CategoryMediaList> = audioMediaDao
    .getMediaForPlaylists(
      playlistIds = playlists
        .mapTo(LongArrayList(512)) { it.id.value }
        .asPlaylistIdList,
      removeDuplicates = !appPrefs.instance().allowDuplicates()
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
    .map { idList -> CategoryMediaList(idList, CategoryToken(playlists.last().id)) }

  private fun getSelectedPlaylists() = playlistsFlow.value
    .filterIfHasSelection(selectedItems.value) { it.id }

  override fun play() {
    scope.launch { localQueueOps.play(::getMediaList, ::selectModeOffMaybeGoHome) }
  }

  override fun shuffle() {
    scope.launch { localQueueOps.shuffle(::getMediaList, ::selectModeOffMaybeGoHome) }
  }

  override fun playNext() {
    scope.launch { localQueueOps.playNext(::getMediaList, ::selectModeOff) }
  }

  override fun addToUpNext() {
    scope.launch { localQueueOps.addToUpNext(::getMediaList, ::selectModeOff) }
  }

  override fun addToPlaylist() {
    scope.launch { localQueueOps.addToPlaylist(::getMediaList, ::selectModeOff) }
  }

  override fun onServiceRegistered() {
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
      playlistsFlow.value = playlistDao.getAllPlaylists()
        .onFailure { cause -> LOG.e(cause) { it("Error getting all playlists") } }
        .getOrElse { emptyList() }
        .map { playlistDescription -> playlistDescription.toPlaylistInfo() }
    }
  }

  private suspend fun PlaylistDescription.toPlaylistInfo() = PlaylistInfo(
    id = id,
    name = name,
    type = type,
    songCount = songCount.toInt(),
    duration = duration,
    artwork = albumDao.getAlbumArtFor(id, type, name)
      .onFailure { LOG.e { it("Error getting artwork for %s %s %s", id, type, name) } }
      .getOrElse { Uri.EMPTY }
  )

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

  override fun deletePlaylist(playlistInfo: PlaylistInfo) {
    scope.launch {
      playlistDao.deletePlaylist(playlistId = playlistInfo.id)
        .onFailure { cause ->
          LOG.e(cause) { it("Couldn't delete %s", playlistInfo.name) }
          mainViewModel.notify(
            Notification(fetch(R.string.CouldNotDeleteName, playlistInfo.name.value))
          )
        }
        .onSuccess { memento ->
          mainViewModel.notify(
            Notification(
              fetch(R.string.DeletedName, playlistInfo.name.value),
              MementoAction(scope, memento)
            )
          )
        }
    }
  }

  override fun goBack() {
    selectedItems.inSelectionModeThenTurnOff()
    backstack.back()
  }

  private fun goToPlaylistSongs(playlistInfo: PlaylistInfo) =
    backstack.goToScreen(
      PlaylistSongsScreen(
        playlistId = playlistInfo.id,
        playListType = playlistInfo.type,
        playlistName = playlistInfo.name,
        artwork = playlistInfo.artwork,
        backTo = fetch(R.string.Playlists)
      )
    )

  override fun onBackEvent(): Boolean = selectedItems
    .inSelectionModeThenTurnOff()
    .cancelFlingOnBack(playlistsFlow)

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

class MementoAction(val scope: CoroutineScope, private val memento: Memento) : Notification.Action {
  override val label: String
    get() = fetch(R.string.Undo)

  override fun action() {
    scope.launch { memento.undo() }
  }

  override fun expired() {
    scope.launch { memento.release() }
  }
}
