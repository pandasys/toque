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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.asComposerIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.ComposersViewModel.ComposerInfo
import com.ealva.toque.ui.library.LibraryCategories.CategoryItem
import com.ealva.toque.ui.library.LocalAudioQueueOps.Op
import com.ealva.toque.ui.nav.back
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(ComposersScreen::class)

@Immutable
@Parcelize
data class ComposersScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(ComposersViewModel(key, get(), lookup(), backstack)) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ComposersViewModel>()
    val composers = viewModel.composerFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        categoryItem = viewModel.categoryItem,
        itemCount = composers.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() }
      )
      AllComposers(
        list = composers.value,
        selected = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) }
      )
    }
  }
}

@Composable
private fun AllComposers(
  list: List<ComposerInfo>,
  selected: SelectedItems<ComposerId>,
  itemClicked: (ComposerInfo) -> Unit,
  itemLongClicked: (ComposerId) -> Unit
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
      items(items = list, key = { it.id }) { composerInfo ->
        ComposerItem(
          composerInfo = composerInfo,
          isSelected = selected.isSelected(composerInfo.id),
          itemClicked = itemClicked,
          itemLongClicked = itemLongClicked
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ComposerItem(
  composerInfo: ComposerInfo,
  isSelected: Boolean,
  itemClicked: (ComposerInfo) -> Unit,
  itemLongClicked: (ComposerId) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(composerInfo) },
        onLongClick = { itemLongClicked(composerInfo.id) }
      ),
    icon = {
      if (composerInfo.artwork !== Uri.EMPTY) {
        Image(
          painter = rememberImagePainter(
            data = composerInfo.artwork,
            builder = { error(R.drawable.ic_person) }
          ),
          contentDescription = stringResource(R.string.ArtistArt),
          modifier = Modifier.size(56.dp)
        )
      } else {
        Icon(
          painter = painterResource(id = R.drawable.ic_person),
          contentDescription = "Composer icon",
          modifier = Modifier.size(40.dp)
        )
      }
    },
    text = { ListItemText(text = composerInfo.name.value) },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          composerInfo.songCount,
          composerInfo.songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private interface ComposersViewModel : ActionsViewModel {
  @Immutable
  data class ComposerInfo(
    val id: ComposerId,
    val name: ComposerName,
    val songCount: Int,
    val artwork: Uri
  )

  val categoryItem: CategoryItem
  val composerFlow: StateFlow<List<ComposerInfo>>
  val selectedItems: SelectedItemsFlow<ComposerId>

  fun itemClicked(composer: ComposerInfo)
  fun itemLongClicked(composerId: ComposerId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  fun goBack()

  companion object {
    operator fun invoke(
      key: ComposeKey,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ComposersViewModel =
      ComposersViewModelImpl(key, audioMediaDao, localAudioQueueModel, backstack, dispatcher)
  }
}

private class ComposersViewModelImpl(
  private val key: ComposeKey,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : ComposersViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  private val composerDao = audioMediaDao.composerDao
  private val categories = LibraryCategories()

  override val categoryItem: CategoryItem
    get() = categories[key]

  override val composerFlow = MutableStateFlow<List<ComposerInfo>>(emptyList())

  override val selectedItems = SelectedItemsFlow<ComposerId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun selectAll() = selectedItems.selectAll(getGenreKeys())
  private fun getGenreKeys() = composerFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private fun offSelectMode() = selectedItems.turnOffSelectionMode()

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    makeCategoryMediaList(getSelectedComposers())

  private suspend fun makeCategoryMediaList(composerList: List<ComposerInfo>) = audioMediaDao
    .getMediaForComposers(
      composerList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asComposerIdList
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
    .map { idList -> CategoryMediaList(idList, CategoryToken(composerList.last().id)) }

  private fun getSelectedComposers() = composerFlow.value
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

  private fun goToComposersSongs(info: ComposerInfo) =
    backstack.goToScreen(
      ComposerSongsScreen(
        composerId = info.id,
        composerName = info.name,
        artwork = info.artwork,
        fetch(R.string.Composers)
      )
    )

  override fun onServiceRegistered() {
    scope = CoroutineScope(Job() + dispatcher)
    // may want onStart+drop(1) for chunking and onEach to not chunk
    filterFlow
      .onEach { requestComposers(processChunks = true) }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow for %s", javaClass) } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    daoEventsJob = composerDao.composerDaoEvents
      .onEach { requestComposers(processChunks = false) }
      .catch { cause -> LOG.e(cause) { it("Error collecting ComposerDao events") } }
      .onCompletion { LOG._i { it("End collecting ComposerDao events") } }
      .launchIn(scope)
    if (wentInactive) {
      wentInactive = false
      requestComposers(processChunks = false)
    }
  }

  override fun onServiceInactive() {
    daoEventsJob?.cancel()
    daoEventsJob = null
    wentInactive = true
  }

  private fun requestComposers(processChunks: Boolean) {
    requestJob?.cancel()

    requestJob = scope.launch(Dispatchers.IO) {
      val composerList = composerDao
        .getAllComposers(filterFlow.value)
        .onFailure { cause -> LOG.e(cause) { it("Error getting Composers") } }
        .getOrElse { emptyList() }

      if (processChunks) {
        // We convert to sequence and break into chunks because we may be querying the DB for
        // artwork and this gets the first chunk to the UI quickly.
        val infoList = ArrayList<ComposerInfo>(composerList.size)
        composerList
          .asSequence()
          .chunked(20)
          .forEach { sublist ->
            composerFlow.value = sublist
              .mapTo(infoList) { composerDescription -> composerDescription.toComposerInfo() }
              .toList()
          }
      } else {
        composerFlow.value = composerList
          .map { composerDescription -> composerDescription.toComposerInfo() }
      }
    }
  }

  private suspend fun ComposerDescription.toComposerInfo() = ComposerInfo(
    id = composerId,
    name = composerName,
    songCount = songCount.toInt(),
    artwork = audioMediaDao.albumDao
      .getAlbumArtFor(composerId)
      .onFailure { cause ->
        LOG.e(cause) { it("Error getting art for %s %s", composerId, composerName) }
      }
      .getOrElse { Uri.EMPTY }
  )

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  override fun goBack() {
    selectedItems.inSelectionModeThenTurnOff()
    backstack.back()
  }

  override fun itemClicked(composer: ComposerInfo) =
    selectedItems.ifInSelectionModeToggleElse(composer.id) { goToComposersSongs(composer) }

  override fun itemLongClicked(composerId: ComposerId) = selectedItems.toggleSelection(composerId)

  override fun onBackEvent(): Boolean = selectedItems
    .inSelectionModeThenTurnOff()
    .cancelFlingOnBack(composerFlow)

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(KEY_MODEL_STATE, ComposersViewModelState(selectedItems.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<ComposersViewModelState>(KEY_MODEL_STATE)?.let { modelState ->
      selectedItems.value = modelState.selected
    }
  }
}

@Parcelize
private data class ComposersViewModelState(val selected: SelectedItems<ComposerId>) : Parcelable

private const val KEY_MODEL_STATE = "GenresModelState"
