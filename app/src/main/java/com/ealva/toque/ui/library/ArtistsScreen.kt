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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ButtonColors
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.common.preferredArt
import com.ealva.toque.db.ArtistDaoEvent
import com.ealva.toque.db.ArtistDaoEvent.ArtistArtworkUpdated
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.art.SelectArtistArtScreen
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.data.ArtistInfo
import com.ealva.toque.ui.library.data.makeCategoryMediaList
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.nav.goToRootScreen
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueColors
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.google.accompanist.insets.LocalWindowInsets
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.hours

private val LOG by lazyLogger(ArtistsScreen::class)

enum class ArtistType(
  @StringRes val categoryTitleRes: Int,
  @StringRes val allSongsRes: Int,
  @DrawableRes val typeIcon: Int
) {
  AlbumArtist(R.string.AlbumArtists, R.string.AllAlbumArtistSongs, R.drawable.ic_account_box),
  SongArtist(R.string.Artists, R.string.AllArtistSongs, R.drawable.ic_microphone)
}

@Immutable
@Parcelize
data class ArtistsScreen(
  private val artistType: ArtistType
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        ArtistsViewModel(
          category = LibraryCategories.Artists,
          audioDao = get(),
          localAudioQueueModel = lookup(),
          artistType = artistType,
          appPrefs = get(AppPrefs.QUALIFIER),
          backstack = backstack
        )
      )
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ArtistsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val artists = viewModel.artistFlow.collectAsState()
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
        itemCount = artists.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() },
        scrollConnection = scrollConnection,
        selectActions = {
          ArtistSelectActions(
            selectedCount = selected.value.selectedCount,
            buttonColors = ActionButtonDefaults.colors(),
            selectArtistArt = { viewModel.selectArtistArt() }
          )
        }
      )
      AllArtistsList(
        list = artists.value,
        selectedItems = selected.value,
        artistType = artistType,
        itemClicked = { artistInfo -> viewModel.itemClicked(artistInfo) },
        itemLongClicked = { viewModel.itemLongClicked(it) }
      )
    }
  }
}

@Composable
fun ArtistSelectActions(
  selectedCount: Int,
  buttonColors: ButtonColors,
  selectArtistArt: () -> Unit
) {
  Row(
    modifier = Modifier
  ) {
    ActionButton(
      modifier = Modifier.height(24.dp),
      iconSize = 24.dp,
      drawable = R.drawable.ic_image,
      description = R.string.MediaInfo,
      enabled = selectedCount == 1,
      colors = buttonColors,
      onClick = selectArtistArt
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllArtistsList(
  list: List<ArtistInfo>,
  selectedItems: SelectedItems<ArtistId>,
  artistType: ArtistType,
  itemClicked: (ArtistInfo) -> Unit,
  itemLongClicked: (ArtistId) -> Unit
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
      items(items = list, key = { it.id }) { artistInfo ->
        ArtistItem(
          modifier = Modifier.combinedClickable(
            onClick = { itemClicked(artistInfo) },
            onLongClick = { itemLongClicked(artistInfo.id) }
          ),
          artistInfo = artistInfo,
          artistType = artistType,
          isSelected = selectedItems.isSelected(artistInfo.id),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ArtistItem(
  modifier: Modifier = Modifier,
  artistInfo: ArtistInfo,
  artistType: ArtistType,
  isSelected: Boolean
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) },
    icon = { ListItemArtwork(artistInfo.artwork, artistType.typeIcon) },
    text = { ListItemText(text = artistInfo.name.value) },
    overlineText = { AlbumCount(artistInfo.albumCount) },
    secondaryText = {
      CountDurationYear(artistInfo.songCount, artistInfo.duration, year = 0)
    }
  )
}

@Composable
private fun AlbumCount(albumCount: Int) {
  Text(
    text = LocalContext.current.resources.getQuantityString(
      R.plurals.AlbumCount,
      albumCount,
      albumCount,
    ),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

interface ArtistsViewModel : ActionsViewModel {
  val category: LibraryCategories.LibraryCategory
  val artistFlow: StateFlow<List<ArtistInfo>>
  val selectedItems: SelectedItemsFlow<ArtistId>

  fun itemClicked(artistInfo: ArtistInfo)
  fun itemLongClicked(artistId: ArtistId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  fun goBack()

  fun selectArtistArt()

  companion object {
    operator fun invoke(
      category: LibraryCategories.LibraryCategory,
      audioDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      artistType: ArtistType,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ArtistsViewModel = ArtistsViewModelImpl(
      category = category,
      audioMediaDao = audioDao,
      localAudioQueueModel = localAudioQueueModel,
      artistType = artistType,
      appPrefs = appPrefs,
      backstack = backstack,
      dispatcher = dispatcher
    )
  }
}

private class ArtistsViewModelImpl(
  override val category: LibraryCategories.LibraryCategory,
  private val audioMediaDao: AudioMediaDao,
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val artistType: ArtistType,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : ArtistsViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  private val scope: CoroutineScope = CoroutineScope(Job() + dispatcher)
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  private val artistDao = audioMediaDao.artistDao
  private val stateKey: String get() = artistType.javaClass.name

  override val artistFlow = MutableStateFlow<List<ArtistInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<ArtistId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  @OptIn(FlowPreview::class)
  override fun onServiceRegistered() {
    // may want onStart+drop(1) for chunking and onEach to not chunk
    filterFlow
      .onStart { requestArtists(processChunks = true) }
      .drop(1)
      .debounce(Filter.debounceDuration)
      .onEach { requestArtists(processChunks = true) }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow for %s", javaClass) } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    daoEventsJob = artistDao.artistDaoEvents
      .onEach { event -> handleDaoEvent(event) }
      .catch { cause -> LOG.e(cause) { it("ArtistDao event flow error") } }
      .onCompletion { LOG._i { it("End collecting ArtistDao events") } }
      .launchIn(scope)
    if (wentInactive) {
      wentInactive = false
      requestArtists(processChunks = false)
    }
  }

  override fun onServiceInactive() {
    daoEventsJob?.cancel()
    daoEventsJob = null
    wentInactive = true
  }

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(stateKey, ArtistsViewModelState(selectedItems.value, searchFlow.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<ArtistsViewModelState>(stateKey)?.let { modelState ->
      selectedItems.value = modelState.selected
      setSearch(modelState.search)
    }
  }

  private fun handleDaoEvent(event: ArtistDaoEvent) = when (event) {
    is ArtistArtworkUpdated -> updateArtwork(event)
    else -> requestArtists(processChunks = false)
  }

  private fun updateArtwork(event: ArtistArtworkUpdated) {
    artistFlow.update { list ->
      list.map { artistInfo ->
        if (artistInfo.id != event.artistId) artistInfo else {
          artistInfo.copy(artwork = event.preferredArt)
        }
      }
    }
  }

  private fun requestArtists(processChunks: Boolean) {
    requestJob?.cancel()
    requestJob = scope.launch(Dispatchers.IO) {
      val list = when (artistType) {
        ArtistType.AlbumArtist -> artistDao.getAlbumArtists(filterFlow.value)
        ArtistType.SongArtist -> artistDao.getSongArtists(filterFlow.value)
      }.onFailure { cause -> LOG.e(cause) { it("Error getting %s artists", artistType) } }
        .getOrElse { emptyList() }

      if (processChunks) {
        // We convert to sequence and break into chunks because we may be querying the DB for
        // artwork and this gets the first chunk to the UI quickly.
        val infoList = ArrayList<ArtistInfo>(list.size)
        list
          .asSequence()
          .chunked(20)
          .forEach { sublist ->
            artistFlow.value = sublist
              .mapTo(infoList) { artist -> artist.toArtistInfo(artistType, audioMediaDao) }
              .toList()
          }
      } else {
        artistFlow.value = list.map { artist -> artist.toArtistInfo(artistType, audioMediaDao) }
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

  override fun selectArtistArt() {
    localAudioQueueModel.clearPrompt()
    val selected = selectedItems.value
    if (selected.selectedCount == 1) {
      artistFlow.value
        .find { artist -> selected.isSelected(artist.id) }
        ?.let { artist ->
          backstack.goToScreen(
            SelectArtistArtScreen(
              artist.id,
              artist.name
            )
          )
        }
    }
  }

  override fun selectAll() = selectedItems.selectAll(getArtistKeys())
  private fun getArtistKeys() = artistFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    getSelectedArtists().makeCategoryMediaList()

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

  private suspend fun List<ArtistInfo>.makeCategoryMediaList() =
    makeCategoryMediaList(audioMediaDao)

  private fun getSelectedArtists() = artistFlow.value
    .filterIfHasSelection(selectedItems.value) { it.id }

  private fun goToArtistAlbums(artistInfo: ArtistInfo) =
    backstack.goToScreen(
      ArtistAlbumsScreen(
        artistId = artistInfo.id,
        artistType = artistType,
        artistName = artistInfo.name,
        artwork = artistInfo.artwork,
        songCount = artistInfo.songCount,
        tertiaryInfo = formatSongsInfo(artistInfo.songCount, artistInfo.duration),
        backTo = fetch(artistType.categoryTitleRes)
      )
    )

  override fun itemClicked(artistInfo: ArtistInfo) = selectedItems
    .ifInSelectionModeToggleElse(artistInfo.id) { goToArtistAlbums(artistInfo) }

  override fun itemLongClicked(artistId: ArtistId) = selectedItems.toggleSelection(artistId)

  override fun onBackEvent(): Boolean = selectedItems
    .inSelectionModeThenTurnOff()
    .cancelFlingOnBack(artistFlow)
}

@Parcelize
private data class ArtistsViewModelState(
  val selected: SelectedItems<ArtistId>,
  val search: String
) : Parcelable

suspend fun Result<List<ArtistDescription>, Throwable>.mapToArtistInfo(
  artistType: ArtistType,
  audioMediaDao: AudioMediaDao
): List<ArtistInfo> = onFailure { cause -> LOG.e(cause) { it("Error getting %s", artistType) } }
  .getOrElse { emptyList() }
  .map { artistDescription -> artistDescription.toArtistInfo(artistType, audioMediaDao) }

private suspend fun ArtistDescription.toArtistInfo(
  artistType: ArtistType,
  audioMediaDao: AudioMediaDao,
  artwork: Uri = preferredArt,
) = ArtistInfo(
  id = artistId,
  name = name,
  artwork = if (artwork !== Uri.EMPTY) artwork else audioMediaDao.albumDao
    .getAlbumArtFor(artistId, artistType)
    .getOrElse { Uri.EMPTY },
  albumCount = albumCount.toInt(),
  songCount = songCount.toInt(),
  duration = duration
)


@Preview
@Composable
fun AllArtistsListPreview() {
  val list = listOf(
    ArtistInfo(
      id = ArtistId(1),
      name = ArtistName("George Harrison"),
      artwork = Uri.EMPTY,
      albumCount = 12,
      songCount = 85,
      duration = 4.hours
    ),
    ArtistInfo(
      id = ArtistId(2),
      name = ArtistName("John Lennon"),
      artwork = Uri.EMPTY,
      albumCount = 15,
      songCount = 100,
      duration = 5.hours
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
      itemClicked = {},
      itemLongClicked = {}
    )
  }
}
