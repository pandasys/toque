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
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.GenreId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.data.GenreInfo
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

private val LOG by lazyLogger(GenresScreen::class)

@Immutable
@Parcelize
data class GenresScreen(
  private val noArg: String = ""
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) = with(serviceBinder) {
    add(
      GenresViewModel(
        category = LibraryCategories.Genres,
        audioMediaDao = get(),
        localAudioQueueModel = lookup(),
        appPrefs = get(AppPrefs.QUALIFIER),
        backstack = backstack
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<GenresViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val genres = viewModel.genreFlow.collectAsState()
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
        itemCount = genres.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() },
        scrollConnection = scrollConnection
      )
      AllGenres(
        list = genres.value,
        selected = selected.value,
        itemClicked = { genreInfo -> viewModel.itemClicked(genreInfo) },
        itemLongClicked = { viewModel.itemLongClicked(it) }
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllGenres(
  list: List<GenreInfo>,
  selected: SelectedItems<GenreId>,
  itemClicked: (GenreInfo) -> Unit,
  itemLongClicked: (GenreId) -> Unit
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
      items(items = list, key = { it.id }) { genreInfo ->
        GenreItem(
          genreInfo = genreInfo,
          isSelected = selected.isSelected(genreInfo.id),
          modifier = Modifier.combinedClickable(
            onClick = { itemClicked(genreInfo) },
            onLongClick = { itemLongClicked(genreInfo.id) }
          )
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GenreItem(
  genreInfo: GenreInfo,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) },
    icon = { ListItemArtwork(genreInfo.artwork, fallback = R.drawable.ic_guitar_acoustic) },
    text = { ListItemText(text = genreInfo.name.value) },
    secondaryText = {
      CountDurationYear(genreInfo.songCount, genreInfo.duration, year = 0)
    },
  )
}

interface GenresViewModel : ActionsViewModel {

  val category: LibraryCategories.LibraryCategory

  val genreFlow: StateFlow<List<GenreInfo>>
  val selectedItems: SelectedItemsFlow<GenreId>

  fun itemClicked(genreInfo: GenreInfo)
  fun itemLongClicked(genreId: GenreId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  fun goBack()

  companion object {
    operator fun invoke(
      category: LibraryCategories.LibraryCategory,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): GenresViewModel = GenresViewModelImpl(
      category,
      audioMediaDao,
      localAudioQueueModel,
      appPrefs,
      backstack,
      dispatcher
    )
  }
}

private class GenresViewModelImpl(
  override val category: LibraryCategories.LibraryCategory,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : GenresViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  private val genreDao: GenreDao = audioMediaDao.genreDao
  override val genreFlow = MutableStateFlow<List<GenreInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<GenreId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  @OptIn(FlowPreview::class)
  override fun onServiceRegistered() {
    filterFlow
      .onStart { requestGenres(processChunks = true) }
      .drop(1)
      .debounce(Filter.debounceDuration)
      .onEach { requestGenres(processChunks = true) }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow for %s", javaClass) } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    daoEventsJob = genreDao.genreDaoEvents
      .onEach { requestGenres(processChunks = false) }
      .catch { cause -> LOG.e(cause) { it("Error collecting GenreDao events") } }
      .onCompletion { LOG._i { it("End collecting GenreDao events") } }
      .launchIn(scope)
    if (wentInactive) {
      wentInactive = false
      requestGenres(processChunks = false)
    }
  }

  override fun onServiceInactive() {
    daoEventsJob?.cancel()
    daoEventsJob = null
    wentInactive = true
  }

  private fun requestGenres(processChunks: Boolean) {
    requestJob?.cancel()

    requestJob = scope.launch(Dispatchers.IO) {
      val genreList = genreDao
        .getAllGenres(filterFlow.value)
        .onFailure { cause -> LOG.e(cause) { it("Error getting Genres") } }
        .getOrElse { emptyList() }

      if (processChunks) {
        // We convert to sequence and break into chunks because we may be querying the DB for
        // artwork and this gets the first chunk to the UI quickly.
        val infoList = ArrayList<GenreInfo>(genreList.size)
        genreList
          .asSequence()
          .chunked(20)
          .forEach { sublist ->
            genreFlow.value = sublist
              .mapTo(infoList) { genre -> genre.toGenreInfo(audioMediaDao) }
              .toList()
          }
      } else {
        genreFlow.value = genreList.map { genre -> genre.toGenreInfo(audioMediaDao) }
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

  override fun selectAll() = selectedItems.selectAll(getGenreKeys())
  private fun getGenreKeys() = genreFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    getSelectedGenres().makeCategoryMediaList()

  private suspend fun List<GenreInfo>.makeCategoryMediaList() =
    makeCategoryMediaList(audioMediaDao)

  private fun getSelectedGenres() = genreFlow.value
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

  private fun goToGenreSongs(info: GenreInfo) =
    backstack.goToScreen(
      GenreSongsScreen(info.id, info.name, info.artwork, fetch(R.string.Genres))
    )

  override fun itemClicked(genreInfo: GenreInfo) =
    selectedItems.ifInSelectionModeToggleElse(genreInfo.id) { goToGenreSongs(genreInfo) }

  override fun itemLongClicked(genreId: GenreId) = selectedItems.toggleSelection(genreId)

  override fun onBackEvent(): Boolean = selectedItems
    .inSelectionModeThenTurnOff()
    .cancelFlingOnBack(genreFlow)

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(KEY_MODEL_STATE, GenresViewModelState(selectedItems.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<GenresViewModelState>(KEY_MODEL_STATE)?.let { modelState ->
      selectedItems.value = modelState.selected
    }
  }
}

suspend fun Result<List<GenreDescription>, Throwable>.mapToGenreInfo(
  audioMediaDao: AudioMediaDao
): List<GenreInfo> = onFailure { cause -> LOG.e(cause) { it("Error getting Genres") } }
  .getOrElse { emptyList() }
  .map { genreDescription -> genreDescription.toGenreInfo(audioMediaDao) }

private suspend fun GenreDescription.toGenreInfo(
  audioMediaDao: AudioMediaDao
): GenreInfo = GenreInfo(
  id = genreId,
  name = genreName,
  songCount = songCount.toInt(),
  duration = duration,
  artwork = audioMediaDao.albumDao
    .getAlbumArtFor(genreId)
    .onFailure { cause -> LOG.e(cause) { it("Error getting art for %s %s", genreId, genreName) } }
    .getOrElse { Uri.EMPTY }
)

@Parcelize
private data class GenresViewModelState(val selected: SelectedItems<GenreId>) : Parcelable

private const val KEY_MODEL_STATE = "GenresModelState"
