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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.asGenreIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.GenresViewModel.GenreInfo
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

private val LOG by lazyLogger(GenresScreen::class)

@Immutable
@Parcelize
data class GenresScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(GenresViewModel(key, get(), lookup(), backstack)) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<GenresViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val genres = viewModel.genreFlow.collectAsState()
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
          itemClicked = itemClicked,
          itemLongClicked = itemLongClicked,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GenreItem(
  genreInfo: GenreInfo,
  isSelected: Boolean,
  itemClicked: (GenreInfo) -> Unit,
  itemLongClicked: (GenreId) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(genreInfo) },
        onLongClick = { itemLongClicked(genreInfo.id) }
      ),
    icon = {
      if (genreInfo.artwork !== Uri.EMPTY) {
        Image(
          painter = rememberImagePainter(
            data = genreInfo.artwork,
            builder = { error(R.drawable.ic_guitar_acoustic) }
          ),
          contentDescription = stringResource(R.string.ArtistArt),
          modifier = Modifier.size(56.dp)
        )
      } else {
        Icon(
          painter = painterResource(id = R.drawable.ic_guitar_acoustic),
          contentDescription = "Genre icon",
          modifier = Modifier.size(40.dp)
        )
      }
    },
    text = { ListItemText(text = genreInfo.name.value) },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          genreInfo.songCount,
          genreInfo.songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private interface GenresViewModel : ActionsViewModel {
  @Immutable
  @Parcelize
  data class GenreInfo(
    val id: GenreId,
    val name: GenreName,
    val songCount: Int,
    val artwork: Uri
  ) : Parcelable

  val categoryItem: LibraryCategories.CategoryItem

  val genreFlow: StateFlow<List<GenreInfo>>
  val selectedItems: SelectedItemsFlow<GenreId>

  fun itemClicked(genreInfo: GenreInfo)
  fun itemLongClicked(genreId: GenreId)

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
    ): GenresViewModel = GenresViewModelImpl(
      key,
      audioMediaDao,
      localAudioQueueModel,
      backstack,
      dispatcher
    )
  }
}

private class GenresViewModelImpl(
  private val key: ComposeKey,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : GenresViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  private val categories = LibraryCategories()
  private val genreDao: GenreDao = audioMediaDao.genreDao

  override val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val genreFlow = MutableStateFlow<List<GenreInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<GenreId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun onServiceRegistered() {
    scope = CoroutineScope(Job() + dispatcher)
    filterFlow
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
              .mapTo(infoList) { genreDescription -> genreDescription.toGenreInfo() }
              .toList()
          }
      } else {
        genreFlow.value = genreList.map { genreDescription -> genreDescription.toGenreInfo() }
      }
    }
  }

  private suspend fun GenreDescription.toGenreInfo(): GenreInfo = GenreInfo(
    id = genreId,
    name = genreName,
    songCount = songCount.toInt(),
    artwork = audioMediaDao.albumDao
      .getAlbumArtFor(genreId)
      .onFailure { cause -> LOG.e(cause) { it("Error getting art for %s %s", genreId, genreName) } }
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

  override fun selectAll() = selectedItems.selectAll(getGenreKeys())
  private fun getGenreKeys() = genreFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private fun offSelectMode() = selectedItems.turnOffSelectionMode()

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    makeCategoryMediaList(getSelectedGenres())

  private suspend fun makeCategoryMediaList(genreList: List<GenreInfo>) = audioMediaDao
    .getMediaForGenres(
      genreList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asGenreIdList
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
    .map { idList -> CategoryMediaList(idList, CategoryToken(genreList.last().id)) }

  private fun getSelectedGenres() = genreFlow.value
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

@Parcelize
private data class GenresViewModelState(val selected: SelectedItems<GenreId>) : Parcelable

private const val KEY_MODEL_STATE = "GenresModelState"
