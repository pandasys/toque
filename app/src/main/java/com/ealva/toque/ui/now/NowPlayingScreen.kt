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

package com.ealva.toque.ui.now

import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Card
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.android.content.inPortrait
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.log._e
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.service.vlc.toFloat
import com.ealva.toque.ui.main.LocalSnackbarHostState
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_ALBUM
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_ARTIST
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_BUTTON_ROW
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_DURATION_TEXT
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_EXTRA_INFO
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_POSITION_SLIDER
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_POSITION_TEXT
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_RATING_BAR_ROW
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_SLIDER_SPACE
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_TITLE
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_TITLE_SPACE
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_TOP_SPACE
import com.google.accompanist.insets.ExperimentalAnimatedInsets
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.WindowInsets
import com.google.accompanist.insets.statusBarsPadding
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(NowPlayingScreen::class)

object NowPlayingScreenIds {
  const val ID_SLIDER_SPACE = 1
  const val ID_BUTTON_ROW = 2
  const val ID_POSITION_SLIDER = 3
  const val ID_POSITION_TEXT = 4
  const val ID_DURATION_TEXT = 5
  const val ID_EXTRA_INFO = 6
  const val ID_TITLE = 7
  const val ID_ARTIST = 8
  const val ID_ALBUM = 9
  const val ID_TITLE_SPACE = 11
  const val ID_TOP_SPACE = 12
  const val ID_RATING_BAR_ROW = 13
}

@Immutable
@Parcelize
data class NowPlayingScreen(private val noArgPlaceholder: String = "") : ComposeKey() {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(NowPlayingViewModel(lookup(), lookup("AppPrefs")))
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<NowPlayingViewModel>()

    val nowPlayingState = viewModel.nowPlayingState.collectAsState()
    NowPlaying(
      state = nowPlayingState.value,
      goToIndex = { index -> viewModel.goToQueueIndexMaybePlay(index) },
      togglePlayPause = { viewModel.togglePlayPause() },
      next = { viewModel.nextMedia() },
      prev = { viewModel.previousMedia() },
      nextList = { viewModel.nextList() },
      prevList = { viewModel.previousList() },
      seekTo = { position -> viewModel.seekTo(position) },
      toggleShowRemaining = { viewModel.toggleShowTimeRemaining() },
      toggleEqMode = { viewModel.toggleEqMode() },
      nextRepeatMode = { viewModel.nextRepeatMode() },
      nextShuffleMode = { viewModel.nextShuffleMode() },
      modifier
    )
  }
}

data class ScreenConfig(
  val inPortrait: Boolean,
  val screenWidthDp: Dp,
  val screenWidthPx: Int,
  val screenHeightDp: Dp,
  val screenHeightPx: Int,
  val statusBarHeight: Dp,
  val navLeft: Dp,
  val navRight: Dp,
) {
  val imageSizePx: Int
    get() = if (inPortrait) screenWidthPx else screenHeightPx
  val imageSizeDp: Dp
    get() = if (inPortrait) screenHeightDp else screenHeightDp
  val navOnLeft: Boolean
    get() = navLeft > navRight
}

private val bottomSheetHeight = 50.dp
private val bottomSheetVertPadding = 8.dp

@OptIn(ExperimentalAnimatedInsets::class, ExperimentalUnitApi::class)
@Composable
fun NowPlaying(
  state: NowPlayingState,
  goToIndex: (Int) -> Unit,
  togglePlayPause: () -> Unit,
  next: () -> Unit,
  prev: () -> Unit,
  nextList: () -> Unit,
  prevList: () -> Unit,
  seekTo: (Millis) -> Unit,
  toggleShowRemaining: () -> Unit,
  toggleEqMode: () -> Unit,
  nextRepeatMode: () -> Unit,
  nextShuffleMode: () -> Unit,
  modifier: Modifier
) {
  val useDarkIcons = MaterialTheme.colors.isLight
  val screenConfig = makeScreenConfig(
    LocalConfiguration.current,
    LocalDensity.current,
    LocalWindowInsets.current
  )

  val systemUiController = rememberSystemUiController()
  SideEffect {
    systemUiController.setSystemBarsColor(
      color = Color(0x66000000),
      darkIcons = useDarkIcons,
    )
  }
  Box(modifier = modifier) {
    val isPortrait = screenConfig.inPortrait
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
      val (pager, controls) = createRefs()
      MediaArtPager(
        queue = state.queue,
        queueIndex = state.queueIndex,
        size = screenConfig.imageSizePx,
        goToIndex = goToIndex,
        modifier = if (isPortrait) {
          Modifier.constrainAs(pager) {
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(controls.top)
            height = Dimension.value(screenConfig.screenWidthDp)
          }
        } else {
          Modifier.constrainAs(pager) {
            top.linkTo(parent.top)
            if (screenConfig.navOnLeft) {
              start.linkTo(parent.start)
              end.linkTo(controls.start)
            } else {
              start.linkTo(controls.end)
              end.linkTo(parent.end)
            }
            bottom.linkTo(parent.bottom)
            width = Dimension.value(screenConfig.imageSizeDp)
          }
        }
      )
      PlayerControls(
        state = state,
        togglePlayPause = togglePlayPause,
        next = next,
        prev = prev,
        nextList = nextList,
        prevList = prevList,
        seekTo = seekTo,
        toggleShowRemaining = toggleShowRemaining,
        toggleEqMode = toggleEqMode,
        nextRepeatMode = nextRepeatMode,
        nextShuffleMode = nextShuffleMode,
        constraintSet = if (isPortrait) portraitConstraints() else landscapeConstraints(),
        modifier = if (isPortrait) {
          Modifier
            .constrainAs(controls) {
              top.linkTo(pager.bottom)
              start.linkTo(parent.start)
              end.linkTo(parent.end)
              bottom.linkTo(parent.bottom)
              height = Dimension.fillToConstraints
            }
            .padding(bottom = bottomSheetHeight + bottomSheetVertPadding)
        } else {
          Modifier
            .constrainAs(controls) {
              top.linkTo(parent.top)
              if (screenConfig.navOnLeft) {
                start.linkTo(pager.end)
                end.linkTo(parent.end)
              } else {
                start.linkTo(parent.start)
                end.linkTo(pager.start)
              }
              bottom.linkTo(parent.bottom)
              width = Dimension.fillToConstraints
            }
            .statusBarsPadding()
            .padding(bottom = bottomSheetHeight + bottomSheetVertPadding)
        }
      )
    }
  }
}

private const val ALPHA_ON = 1.0F
private const val ALPHA_OFF = 0.2F

@OptIn(ExperimentalUnitApi::class, ExperimentalCoilApi::class)
@Composable
fun PlayerControls(
  state: NowPlayingState,
  togglePlayPause: () -> Unit,
  next: () -> Unit,
  prev: () -> Unit,
  nextList: () -> Unit,
  prevList: () -> Unit,
  seekTo: (Millis) -> Unit,
  toggleShowRemaining: () -> Unit,
  toggleEqMode: () -> Unit,
  nextRepeatMode: () -> Unit,
  nextShuffleMode: () -> Unit,
  constraintSet: ConstraintSet,
  modifier: Modifier
) {
  val item = state.currentItem

  val snackbarHostState = LocalSnackbarHostState.current
  val scope = rememberCoroutineScope()

  BoxWithConstraints(modifier = modifier) {
    ConstraintLayout(constraintSet = constraintSet, modifier = Modifier.fillMaxSize()) {

      Spacer(modifier = Modifier.layoutId(ID_TOP_SPACE)) // can't top align rating bar without this
      RatingBarRow(
        eqMode = state.eqMode,
        rating = item.rating,
        playbackRate = state.playbackRate,
        repeatMode = state.repeatMode,
        shuffleMode = state.shuffleMode,
        toggleEqMode = toggleEqMode,
        setPlaybackRate = {
          scope.launch {
            when (snackbarHostState.showSnackbar("Snackbar it is", "Label")) {
              SnackbarResult.Dismissed -> LOG._e { it("dismissed") }
              SnackbarResult.ActionPerformed -> LOG._e { it("action") }
            }
          }
        },
        nextRepeatMode = nextRepeatMode,
        nextShuffleMode = nextShuffleMode,
        modifier = Modifier
          .layoutId(ID_RATING_BAR_ROW)
          .padding(horizontal = 8.dp)
          .fillMaxWidth()
      )
      Text(
        text = item.title.value,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h6,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.layoutId(ID_TITLE)
      )
      Text(
        text = item.albumArtist.value,
        style = MaterialTheme.typography.subtitle2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.layoutId(ID_ARTIST)
      )
      Text(
        text = item.albumTitle.value,
        style = MaterialTheme.typography.subtitle1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.layoutId(ID_ALBUM)
      )
      Text(
        text = state.extraMediaInfo,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = MaterialTheme.typography.caption,
        modifier = Modifier
          .layoutId(ID_EXTRA_INFO)
          .padding(horizontal = 8.dp)
      )
      Spacer(modifier = Modifier.layoutId(ID_SLIDER_SPACE))
      PositionSlider(
        state.position,
        0F..state.duration.toFloat(),
        seekTo,
        modifier = Modifier
          .layoutId(ID_POSITION_SLIDER)
          .padding(horizontal = 16.dp)
      )
      Text(
        text = state.getPositionDisplay(),
        textAlign = TextAlign.Start,
        maxLines = 1,
        style = MaterialTheme.typography.caption,
        modifier = Modifier
          .layoutId(ID_POSITION_TEXT)
          .padding(start = 12.dp)
      )
      Text(
        text = state.getDurationDisplay(),
        textAlign = TextAlign.End,
        maxLines = 1,
        style = MaterialTheme.typography.caption,
        modifier = Modifier
          .layoutId(ID_DURATION_TEXT)
          .clickable(onClick = toggleShowRemaining)
          .padding(end = 12.dp)
      )
      ButtonRow(
        state.playingState,
        togglePlayPause,
        next,
        prev,
        nextList,
        prevList,
        Modifier
          .layoutId(ID_BUTTON_ROW)
          .padding(horizontal = 8.dp)
          .fillMaxWidth()
      )
    }
  }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun MediaArtPager(
  queue: List<AudioItem>,
  queueIndex: Int,
  size: Int,
  goToIndex: (Int) -> Unit,
  modifier: Modifier
) {
  val pagerState = rememberPagerState(initialPage = queueIndex.coerceAtLeast(0))

  if (queueIndex >= 0) {
    LaunchedEffect(key1 = queueIndex) {
      if (queueIndex in queue.indices) pagerState.scrollToPage(queueIndex)
    }
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect { index -> goToIndex(index) }
  }
  HorizontalPager(
    count = queue.size,
    state = pagerState,
    modifier = modifier
  ) { page ->
    ArtPagerCard(queue, page, size)
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun ArtPagerCard(queue: List<AudioItem>, currentPage: Int, size: Int) {
  if (currentPage in queue.indices) {
    val item = queue[currentPage]
    Card(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = rememberImagePainter(
          data = if (item.localAlbumArt !== Uri.EMPTY) item.localAlbumArt else item.albumArt,
          builder = {
            size(size)
            error(R.drawable.ic_big_album)
            placeholder(R.drawable.ic_big_album)
          }
        ),
        contentDescription = "${item.title()} Album Cover Art",
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
fun PositionSlider(
  position: Millis,
  range: ClosedFloatingPointRange<Float>,
  seekTo: (Millis) -> Unit,
  modifier: Modifier
) {
  Slider(
    value = position.toFloat(),
    valueRange = range,
    onValueChange = { seekTo(Millis(it)) },
    modifier = modifier
  )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun RatingBarRow(
  eqMode: EqMode,
  rating: Rating,
  playbackRate: PlaybackRate,
  repeatMode: RepeatMode,
  shuffleMode: ShuffleMode,
  toggleEqMode: () -> Unit,
  setPlaybackRate: () -> Unit,
  nextRepeatMode: () -> Unit,
  nextShuffleMode: () -> Unit,
  modifier: Modifier
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceEvenly
  ) {
    IconButton(onClick = toggleEqMode, modifier = Modifier.size(26.dp)) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_audio_equalizer),
        contentDescription = "Toggle Equalizer",
        alpha = if (eqMode.isOn()) ALPHA_ON else ALPHA_OFF,
        modifier = Modifier.size(26.dp)
      )
    }
    Spacer(modifier = Modifier.height(26.dp))
    Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
      Text(
        text = "%.2fX".format(playbackRate.value),
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = MaterialTheme.typography.caption,
        modifier = Modifier
          .border(1.dp, Color.White)
          .padding(2.dp)
          .clickable(onClick = setPlaybackRate)
      )
    }
    Spacer(modifier = Modifier.height(26.dp))
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = rating.toStarRating().value,
      size = 24.dp,
      padding = 2.dp,
      isIndicator = true,
      activeColor = Color.White,
      inactiveColor = Color.White,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {},
      onRatingChanged = {},
    )
    Spacer(modifier = Modifier.height(26.dp))
    IconButton(onClick = nextRepeatMode, modifier = Modifier.size(26.dp)) {
      Image(
        painter = rememberImagePainter(data = repeatMode.drawable),
        contentDescription = stringResource(id = repeatMode.titleRes),
        alpha = if (repeatMode.isOn()) ALPHA_ON else ALPHA_OFF,
        modifier = Modifier.size(26.dp)
      )
    }
    Spacer(modifier = Modifier.height(26.dp))
    IconButton(onClick = nextShuffleMode, modifier = Modifier.size(26.dp)) {
      Image(
        painter = rememberImagePainter(data = shuffleMode.drawable),
        contentDescription = stringResource(id = shuffleMode.titleRes),
        alpha = if (shuffleMode.isOn()) ALPHA_ON else ALPHA_OFF,
        modifier = Modifier.size(26.dp)
      )
    }
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun ButtonRow(
  playState: PlayState,
  togglePlayPause: () -> Unit,
  next: () -> Unit,
  prev: () -> Unit,
  nextList: () -> Unit,
  prevList: () -> Unit,
  modifier: Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceEvenly
  ) {
    IconButton(onClick = prevList, modifier = Modifier.size(50.dp)) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_prev_list),
        contentDescription = "Play previous list",
        modifier = Modifier.size(38.dp)
      )
    }
    IconButton(onClick = prev, modifier = Modifier.size(50.dp)) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_previous),
        contentDescription = "Rewind or previous",
        modifier = Modifier.size(44.dp)
      )
    }
    IconButton(onClick = togglePlayPause, modifier = Modifier.size(50.dp)) {
      Image(
        painter = rememberImagePainter(
          data = if (playState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        ),
        contentDescription = "Toggle play pause",
        modifier = Modifier.size(50.dp)
      )
    }
    IconButton(onClick = next, modifier = Modifier.size(50.dp)) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_next),
        contentDescription = "Play next",
        modifier = Modifier.size(44.dp)
      )
    }
    IconButton(onClick = nextList, modifier = Modifier.size(50.dp)) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_next_list),
        contentDescription = "Play next list",
        modifier = Modifier.size(38.dp)
      )
    }
  }
}

fun portraitConstraints(): ConstraintSet = ConstraintSet {
  val buttonRow = createRefFor(ID_BUTTON_ROW)
  val slider = createRefFor(ID_POSITION_SLIDER)
  val position = createRefFor(ID_POSITION_TEXT)
  val duration = createRefFor(ID_DURATION_TEXT)
  val extraInfo = createRefFor(ID_EXTRA_INFO)
  val title = createRefFor(ID_TITLE)
  val artist = createRefFor(ID_ARTIST)
  val album = createRefFor(ID_ALBUM)
  val sliderSpace = createRefFor(ID_SLIDER_SPACE)
  val titleSpace = createRefFor(ID_TITLE_SPACE)
  val topSpace = createRefFor(ID_TOP_SPACE)
  val ratingBarRow = createRefFor(ID_RATING_BAR_ROW)
  constrain(topSpace) {
    top.linkTo(parent.top)
    centerHorizontallyTo(parent)
    bottom.linkTo(ratingBarRow.top)
    height = Dimension.value(8.dp)
  }
  constrain(ratingBarRow) {
    top.linkTo(topSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(titleSpace.top)
  }
  constrain(titleSpace) {
    top.linkTo(ratingBarRow.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(title.top)
    height = Dimension.fillToConstraints
  }
  constrain(title) {
    top.linkTo(titleSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(artist.top)
    height = Dimension.wrapContent
  }
  constrain(artist) {
    top.linkTo(title.bottom, margin = 2.dp)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(album.top)
    height = Dimension.wrapContent
  }
  constrain(album) {
    top.linkTo(artist.bottom, margin = 2.dp)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(sliderSpace.top)
    height = Dimension.wrapContent
  }
  constrain(sliderSpace) {
    top.linkTo(album.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(slider.top)
    height = Dimension.fillToConstraints
  }
  constrain(slider) {
    top.linkTo(sliderSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(extraInfo.top, margin = (-8).dp)
    height = Dimension.wrapContent
  }
  constrain(position) {
    top.linkTo(slider.bottom)
    start.linkTo(parent.start)
    bottom.linkTo(buttonRow.top)
    height = Dimension.wrapContent
  }
  constrain(extraInfo) {
    top.linkTo(slider.bottom)
    start.linkTo(position.end)
    end.linkTo(duration.start)
    bottom.linkTo(buttonRow.top)
    height = Dimension.wrapContent
  }
  constrain(duration) {
    top.linkTo(slider.bottom)
    end.linkTo(parent.end)
    bottom.linkTo(buttonRow.top)
    height = Dimension.wrapContent
  }
  constrain(buttonRow) {
    top.linkTo(extraInfo.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(parent.bottom)
    height = Dimension.wrapContent
  }
}

fun landscapeConstraints(): ConstraintSet = ConstraintSet {
  val buttonRow = createRefFor(ID_BUTTON_ROW)
  val slider = createRefFor(ID_POSITION_SLIDER)
  val position = createRefFor(ID_POSITION_TEXT)
  val duration = createRefFor(ID_DURATION_TEXT)
  val extraInfo = createRefFor(ID_EXTRA_INFO)
  val title = createRefFor(ID_TITLE)
  val artist = createRefFor(ID_ARTIST)
  val album = createRefFor(ID_ALBUM)
  val sliderSpace = createRefFor(ID_SLIDER_SPACE)
  val topSpace = createRefFor(ID_TOP_SPACE)
  val ratingBarRow = createRefFor(ID_RATING_BAR_ROW)
  constrain(topSpace) {
    top.linkTo(parent.top)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(title.top)
    height = Dimension.value(8.dp)
  }
  constrain(title) {
    top.linkTo(topSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(artist.top)
    height = Dimension.wrapContent
  }
  constrain(artist) {
    top.linkTo(title.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(album.top)
    height = Dimension.wrapContent
  }
  constrain(album) {
    top.linkTo(artist.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(sliderSpace.top)
    height = Dimension.wrapContent
  }
  constrain(sliderSpace) {
    top.linkTo(album.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(ratingBarRow.top)
    height = Dimension.fillToConstraints
  }
  constrain(ratingBarRow) {
    top.linkTo(sliderSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(slider.top, margin = (-12).dp)
  }
  constrain(slider) {
    top.linkTo(ratingBarRow.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(extraInfo.top)
    height = Dimension.wrapContent
  }
  constrain(position) {
    top.linkTo(slider.bottom, margin = (-8).dp)
    start.linkTo(parent.start)
    bottom.linkTo(buttonRow.top)
    end.linkTo(extraInfo.start)
    width = Dimension.wrapContent
    height = Dimension.wrapContent
  }
  constrain(extraInfo) {
    top.linkTo(slider.bottom, margin = (-8).dp)
    start.linkTo(position.end)
    end.linkTo(duration.start)
    bottom.linkTo(buttonRow.top)
    height = Dimension.wrapContent
    width = Dimension.fillToConstraints
  }
  constrain(duration) {
    top.linkTo(slider.bottom, margin = (-8).dp)
    start.linkTo(extraInfo.end)
    end.linkTo(parent.end)
    bottom.linkTo(buttonRow.top)
    height = Dimension.wrapContent
    width = Dimension.wrapContent
  }
  constrain(buttonRow) {
    top.linkTo(extraInfo.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(parent.bottom)
    height = Dimension.wrapContent
  }
}

fun makeScreenConfig(config: Configuration, density: Density, insets: WindowInsets): ScreenConfig {
  val screenWidthDp = config.screenWidthDp.dp
  val screenHeightDp = config.screenHeightDp.dp
  return ScreenConfig(
    inPortrait = config.inPortrait,
    screenWidthDp = screenWidthDp,
    screenWidthPx = with(density) { screenWidthDp.roundToPx() },
    screenHeightDp = screenHeightDp,
    screenHeightPx = with(density) { screenHeightDp.roundToPx() },
    statusBarHeight = with(density) { insets.statusBars.top.toDp() },
    navLeft = with(density) { insets.navigationBars.left.toDp() },
    navRight = with(density) { insets.navigationBars.right.toDp() }
  )
}

private val RepeatMode.drawable: Int
  @DrawableRes get() = when (this) {
    RepeatMode.None -> R.drawable.ic_repeat_off
    RepeatMode.All -> R.drawable.ic_repeat
    RepeatMode.One -> R.drawable.ic_repeat_once
  }

private val ShuffleMode.drawable: Int
  @DrawableRes get() = when (this) {
    ShuffleMode.None -> R.drawable.ic_shuffle_disabled
    ShuffleMode.Media -> R.drawable.ic_shuffle_media
    ShuffleMode.Lists -> R.drawable.ic_shuffle
    ShuffleMode.MediaAndLists -> R.drawable.ic_shuffle_both
  }
