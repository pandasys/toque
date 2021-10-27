/*
 * Copyright 2021 eAlva.com
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toDurationString
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.AllSongsViewModel.SongInfo
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(LibrarySongsScreen::class)

@Immutable
@Parcelize
data class LibrarySongsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllSongsViewModel(get(), backstack)) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllSongsViewModel>()
    val songs = viewModel.allSongs.collectAsState()
    SongItemList(songs.value)
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SongItemList(list: List<SongInfo>) {
  val listState = rememberLazyListState()
  //val dragged = listState.interactionSource.collectIsDraggedAsState()

  val config = LocalScreenConfig.current
  val sheetHeight = config.getNavPlusBottomSheetHeight(isExpanded = true)

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = sheetHeight, end = 8.dp),
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
  ) {
    items(list) { songInfo -> SongItem(songInfo = songInfo) }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SongItem(songInfo: SongInfo) {
  ListItem(
    modifier = Modifier.fillMaxWidth(),
    icon = { SongArtwork(songInfo) },
    text = { Text(text = songInfo.title.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = { ArtistAndDuration(songInfo) },
    overlineText = { AlbumAndRating(songInfo) }
  )
}

@Composable
private fun SongArtwork(songInfo: SongInfo) {
  Image(
    painter = rememberImagePainter(
      data = songInfo.artwork,
      builder = {
        error(R.drawable.ic_big_album)
        placeholder(R.drawable.ic_big_album)
      }
    ),
    contentDescription = "Toggle Equalizer",
    modifier = Modifier.size(56.dp)
  )
}

@Composable
private fun ArtistAndDuration(songInfo: SongInfo) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (artist, duration) = createRefs()
    Text(
      text = songInfo.artist.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(artist) {
        start.linkTo(parent.start)
        end.linkTo(duration.start)
        width = Dimension.fillToConstraints
      }
    )
    Text(
      text = songInfo.duration.toDurationString(),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(duration) {
        start.linkTo(artist.end)
        end.linkTo(parent.end)
        width = Dimension.wrapContent
      }
    )
  }
}

@Composable
private fun AlbumAndRating(songInfo: SongInfo) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (text, ratingBar) = createRefs()
    Text(
      text = songInfo.album.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(text) {
        start.linkTo(parent.start)
        end.linkTo(ratingBar.start)
        width = Dimension.fillToConstraints
      }
    )
    RatingBar(
      value = songInfo.rating.toStarRating().value,
      size = 8.dp,
      padding = 2.dp,
      isIndicator = true,
      activeColor = Color.White,
      inactiveColor = Color.White,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {},
      onRatingChanged = {},
      modifier = Modifier.constrainAs(ratingBar) {
        top.linkTo(text.top)
        start.linkTo(text.end)
        end.linkTo(parent.end)
        bottom.linkTo(text.bottom)
        width = Dimension.wrapContent
      },
    )
  }
}

private interface AllSongsViewModel {
  data class SongInfo(
    val id: MediaId,
    val title: Title,
    val duration: Millis,
    val rating: Rating,
    val album: AlbumTitle,
    val artist: ArtistName,
    val artwork: Uri
  )

  val allSongs: StateFlow<List<SongInfo>>

  companion object {
    operator fun invoke(audioMediaDao: AudioMediaDao, backstack: Backstack): AllSongsViewModel =
      AllSongsViewModelImpl(audioMediaDao, backstack)
  }
}

private class AllSongsViewModelImpl(
  private val audioMediaDao: AudioMediaDao,
  private val backstack: Backstack
) : AllSongsViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val allSongs = MutableStateFlow<List<SongInfo>>(emptyList())

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + Dispatchers.Main)
    scope.launch {
      LOG._e { it("getAllAudio") }
      when (val result = audioMediaDao.getAllAudio(limit = Long.MAX_VALUE)) {
        is Ok -> handleAudioList(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handleAudioList(list: List<AudioDescription>) {
    LOG._e { it("list.size=%d", list.size) }
    allSongs.value = list.mapTo(ArrayList(list.size)) {
      SongInfo(
        id = it.mediaId,
        title = it.title,
        duration = it.duration,
        rating = it.rating,
        album = it.album,
        artist = it.artist,
        artwork = if (it.albumLocalArt !== Uri.EMPTY) it.albumLocalArt else it.albumArt
      )
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    allSongs.value = emptyList()
  }
}
