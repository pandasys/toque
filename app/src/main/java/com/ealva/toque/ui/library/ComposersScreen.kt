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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.LibraryCategories.LibraryCategory
import com.ealva.toque.ui.library.data.ComposerInfo
import com.ealva.toque.ui.library.data.makeCategoryMediaList
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.nav.goToRootScreen
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(ComposersScreen::class)

@Immutable
@Parcelize
data class ComposersScreen(
  private val noArg: String = ""
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) = with(serviceBinder) {
    add(
      ComposersViewModel(
        category = LibraryCategories.Composers,
        audioMediaDao = get(),
        localAudioQueueModel = lookup(),
        backstack = backstack,
        appPrefs = get(AppPrefs.QUALIFIER)
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ComposersViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val composers = viewModel.composerFlow.collectAsState()
    val selected = viewModel.selectedItems.collectAsState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        category = viewModel.category,
        itemCount = composers.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() },
        scrollConnection = scrollConnection
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

@OptIn(ExperimentalFoundationApi::class)
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
          modifier = Modifier.combinedClickable(
            onClick = { itemClicked(composerInfo) },
            onLongClick = { itemLongClicked(composerInfo.id) }
          )
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ComposerItem(
  composerInfo: ComposerInfo,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) },
    icon = { ListItemArtwork(artwork = composerInfo.artwork, fallback = R.drawable.ic_person) },
    text = { ListItemText(text = composerInfo.name.value) },
    secondaryText = {
      CountDurationYear(composerInfo.songCount, composerInfo.duration, year = 0)
    },
  )
}

interface ComposersViewModel : ActionsViewModel {

  val category: LibraryCategory
  val composerFlow: StateFlow<List<ComposerInfo>>
  val selectedItems: SelectedItemsFlow<ComposerId>

  fun itemClicked(composer: ComposerInfo)
  fun itemLongClicked(composerId: ComposerId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  fun goBack()

  companion object {
    operator fun invoke(
      category: LibraryCategory,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      appPrefs: AppPrefsSingleton,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ComposersViewModel =
      ComposersViewModelImpl(
        category,
        audioMediaDao,
        localAudioQueueModel,
        backstack,
        appPrefs,
        dispatcher
      )
  }
}

private class ComposersViewModelImpl(
  override val category: LibraryCategory,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val appPrefs: AppPrefsSingleton,
  dispatcher: CoroutineDispatcher
) : ComposersViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  private val composerDao = audioMediaDao.composerDao
  override val composerFlow = MutableStateFlow<List<ComposerInfo>>(emptyList())

  override val selectedItems = SelectedItemsFlow<ComposerId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun selectAll() = selectedItems.selectAll(getGenreKeys())
  private fun getGenreKeys() = composerFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    getSelectedComposers().makeCategoryMediaList()

  private suspend fun List<ComposerInfo>.makeCategoryMediaList() =
    makeCategoryMediaList(audioMediaDao)

  private fun getSelectedComposers() = composerFlow.value
    .filterIfHasSelection(selectedItems.value) { it.id }

  private fun selectModeOff() = selectedItems.turnOffSelectionMode()

  private suspend fun selectModeOffMaybeGoHome() {
    selectModeOff()
    if (appPrefs.instance().goToNowPlaying()) backstack.goToRootScreen()
  }

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

  private fun goToComposersSongs(info: ComposerInfo) =
    backstack.goToScreen(
      ComposerSongsScreen(
        composerId = info.id,
        composerName = info.name,
        artwork = info.artwork,
        fetch(R.string.Composers)
      )
    )

  @OptIn(FlowPreview::class)
  override fun onServiceRegistered() {
    // may want onStart+drop(1) for chunking and onEach to not chunk
    filterFlow
      .onStart { requestComposers(processChunks = true) }
      .drop(1)
      .debounce(Filter.debounceDuration)
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
              .mapTo(infoList) { composer -> composer.toComposerInfo(audioMediaDao) }
              .toList()
          }
      } else {
        composerFlow.value = composerList
          .map { composer -> composer.toComposerInfo(audioMediaDao) }
      }
    }
  }

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  override fun goBack() {
    selectedItems.inSelectionModeThenTurnOff()
    backstack.backIfAllowed()
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

suspend fun Result<List<ComposerDescription>, Throwable>.mapToComposerInfo(
  audioMediaDao: AudioMediaDao
): List<ComposerInfo> = onFailure { cause -> LOG.e(cause) { it("Error getting Composers") } }
  .getOrElse { emptyList() }
  .map { composer -> composer.toComposerInfo(audioMediaDao) }

suspend fun ComposerDescription.toComposerInfo(
  audioMediaDao: AudioMediaDao
) = ComposerInfo(
  id = composerId,
  name = composerName,
  songCount = songCount.toInt(),
  duration = duration,
  artwork = audioMediaDao.albumDao
    .getAlbumArtFor(composerId)
    .onFailure { cause ->
      LOG.e(cause) { it("Error getting art for %s %s", composerId, composerName) }
    }
    .getOrElse { Uri.EMPTY }
)

@Parcelize
private data class ComposersViewModelState(val selected: SelectedItems<ComposerId>) : Parcelable

private const val KEY_MODEL_STATE = "GenresModelState"
