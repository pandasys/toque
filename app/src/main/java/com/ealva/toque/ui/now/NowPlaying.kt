/*
 * Copyright 2022 Eric A. Snell
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

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Rating
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.drawable
import com.ealva.toque.common.toStarRating
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.vlc.toFloat
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_BUTTON_ROW
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_DURATION_TEXT
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_EXTRA_INFO
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_ITEM_TITLES
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_POSITION_SLIDER
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_POSITION_TEXT
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_RATING_BAR_ROW
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_SLIDER_SPACE
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_TITLE_SPACE
import com.ealva.toque.ui.now.NowPlayingScreenIds.ID_TOP_SPACE
import com.ealva.toque.ui.now.NowPlayingViewModel.NowPlayingState
import com.ealva.toque.ui.now.NowPlayingViewModel.QueueItem
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.ExperimentalAnimatedInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarConfig
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.DurationUnit

@Suppress("unused")
private val LOG by lazyLogger("NowPlaying")

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
  userSeekingComplete: () -> Unit,
  toggleShowRemaining: () -> Unit,
  toggleEqMode: () -> Unit,
  nextRepeatMode: () -> Unit,
  nextShuffleMode: () -> Unit,
  showItemDialog: () -> Unit,
  modifier: Modifier,
  isLockScreen: Boolean = false
) {
  val screenConfig = LocalScreenConfig.current

  val systemUiController = rememberSystemUiController()
  SideEffect {
    systemUiController.setSystemBarsColor(
      color = Color.Black.copy(alpha = .25F),
      darkIcons = false,
    )
  }

  Box(modifier = modifier.navigationBarsPadding()) {
    val isPortrait = screenConfig.inPortrait
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
      val (pager, controls, menu) = createRefs()
      MediaArtPager(
        queue = state.queue,
        queueIndex = state.queueIndex,
        size = screenConfig.imageSizePx,
        goToIndex = goToIndex,
        modifier = if (isPortrait) {
          Modifier
            .statusBarsPadding()
            .constrainAs(pager) {
              top.linkTo(parent.top)
              start.linkTo(parent.start)
              end.linkTo(parent.end)
              bottom.linkTo(controls.top)
              height = Dimension.value(screenConfig.screenWidthDp)
            }
        } else {
          Modifier
            .statusBarsPadding()
            .padding(bottom = screenConfig.getNavPlusBottomSheetHeight(isExpanded = false))
            .constrainAs(pager) {
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
      if (!isLockScreen) {
        CurrentItemMenu(
          modifier = Modifier
            .modifyIf(screenConfig.inLandscape) {
              padding(bottom = screenConfig.getNavPlusBottomSheetHeight(isExpanded = false))
            }
            .constrainAs(menu) {
              end.linkTo(pager.end, 16.dp)
              bottom.linkTo(pager.bottom)
            },
          onClick = showItemDialog
        )
      }
      PlayerControls(
        state = state,
        togglePlayPause = togglePlayPause,
        next = next,
        prev = prev,
        nextList = nextList,
        prevList = prevList,
        seekTo = seekTo,
        userSeekingComplete = userSeekingComplete,
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
            .padding(bottom = screenConfig.getBottomSheetHeight(false) + 8.dp)
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
            .padding(bottom = screenConfig.getBottomSheetHeight(false) + 8.dp)
        }
      )
    }
  }
}

@Composable
private fun CurrentItemMenu(
  modifier: Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .size(42.dp)
      .clip(RoundedCornerShape(50))
      .background(Color.Black.copy(alpha = .25F))
      .clickable(onClick = onClick),
  ) {
    Icon(
      modifier = Modifier
        .size(28.dp)
        .align(Alignment.Center),
      painter = painterResource(id = R.drawable.ic_more_vert),
      contentDescription = stringResource(id = R.string.Menu),
      tint = Color.White
    )
  }
}

private const val ALPHA_ON = 1.0F
private const val ALPHA_OFF = 0.3F

private object NowPlayingScreenIds {
  const val ID_SLIDER_SPACE = 1
  const val ID_BUTTON_ROW = 2
  const val ID_POSITION_SLIDER = 3
  const val ID_POSITION_TEXT = 4
  const val ID_DURATION_TEXT = 5
  const val ID_EXTRA_INFO = 6
  const val ID_ITEM_TITLES = 9
  const val ID_TITLE_SPACE = 11
  const val ID_TOP_SPACE = 12
  const val ID_RATING_BAR_ROW = 13
}

@OptIn(ExperimentalUnitApi::class, ExperimentalCoilApi::class)
@Composable
private fun PlayerControls(
  state: NowPlayingState,
  togglePlayPause: () -> Unit,
  next: () -> Unit,
  prev: () -> Unit,
  nextList: () -> Unit,
  prevList: () -> Unit,
  seekTo: (Millis) -> Unit,
  userSeekingComplete: () -> Unit,
  toggleShowRemaining: () -> Unit,
  toggleEqMode: () -> Unit,
  nextRepeatMode: () -> Unit,
  nextShuffleMode: () -> Unit,
  constraintSet: ConstraintSet,
  modifier: Modifier
) {
  val item = state.currentItem

  BoxWithConstraints(
    modifier = modifier
  ) {
    ConstraintLayout(constraintSet = constraintSet, modifier = Modifier.fillMaxSize()) {

      Spacer(modifier = Modifier.layoutId(ID_TOP_SPACE)) // can't top align rating bar without this
      RatingBarRow(
        queueIndex = state.queueIndex,
        queueSize = state.queue.size,
        eqMode = state.eqMode,
        rating = item.rating,
        playbackRate = state.playbackRate,
        repeatMode = state.repeatMode,
        shuffleMode = state.shuffleMode,
        toggleEqMode = toggleEqMode,
        setPlaybackRate = {
        },
        nextRepeatMode = nextRepeatMode,
        nextShuffleMode = nextShuffleMode,
        modifier = Modifier
          .layoutId(ID_RATING_BAR_ROW)
          .padding(horizontal = 12.dp)
          .fillMaxWidth()
      )
      ItemTitles(item)
      Text(
        text = state.extraMediaInfo,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = toqueTypography.caption,
        modifier = Modifier
          .layoutId(ID_EXTRA_INFO)
          .padding(horizontal = 8.dp)
      )
      Spacer(modifier = Modifier.layoutId(ID_SLIDER_SPACE))
      PositionSlider(
        state.position,
        0F..state.duration.toDouble(DurationUnit.MILLISECONDS).toFloat(),
        seekTo,
        userSeekingComplete,
        modifier = Modifier
          .layoutId(ID_POSITION_SLIDER)
          .padding(horizontal = 16.dp)
      )
      Text(
        text = state.getPositionDisplay(),
        textAlign = TextAlign.Start,
        maxLines = 1,
        style = toqueTypography.caption,
        modifier = Modifier
          .layoutId(ID_POSITION_TEXT)
          .padding(start = 12.dp)
      )
      Text(
        text = state.getDurationDisplay(),
        textAlign = TextAlign.End,
        maxLines = 1,
        style = toqueTypography.caption,
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

@Composable
private fun ItemTitles(item: QueueItem) {
  SelectionContainer(
    modifier = Modifier
      .padding(horizontal = 12.dp)
      .layoutId(ID_ITEM_TITLES)
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = item.albumTitle.value,
        textAlign = TextAlign.Center,
        style = toqueTypography.nowPlayingAlbum,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = item.title.value,
        textAlign = TextAlign.Center,
        style = toqueTypography.nowPlayingTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        Text(
          text = item.albumArtist.value,
          textAlign = TextAlign.Center,
          style = toqueTypography.nowPlayingArtist,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun MediaArtPager(
  queue: List<QueueItem>,
  queueIndex: Int,
  size: Int,
  goToIndex: (Int) -> Unit,
  modifier: Modifier
) {
  val pagerState: PagerState = rememberPagerState(initialPage = queueIndex.coerceAtLeast(0))

  HorizontalPager(
    count = queue.size,
    state = pagerState,
    modifier = modifier
  ) { page ->
    ArtPagerCard(queue, page, size)
  }

  if (queueIndex >= 0 && queueIndex in queue.indices) {
    LaunchedEffect(key1 = queueIndex) {
      pagerState.scrollToPage(queueIndex)
    }
  }

  LaunchedEffect(pagerState) {
    // ViewModel will determine if we are already at index and do nothing
    snapshotFlow { pagerState.currentPage }
      .drop(1)
      .onEach { index -> goToIndex(index) }
      .launchIn(this)
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun ArtPagerCard(queue: List<QueueItem>, currentPage: Int, size: Int) {
  if (currentPage in queue.indices) {
    val item = queue[currentPage]
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = rememberImagePainter(
          data = if (item.artwork !== Uri.EMPTY) item.artwork else R.drawable.ic_big_album,
          builder = {
            size(size)
            error(R.drawable.ic_album)
          }
        ),
        contentDescription = "${item.title.value} Album Cover Art",
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
private fun PositionSlider(
  position: Millis,
  range: ClosedFloatingPointRange<Float>,
  seekTo: (Millis) -> Unit,
  userSeekingComplete: () -> Unit,
  modifier: Modifier
) {
  Slider(
    value = position.toFloat(),
    valueRange = range,
    onValueChange = { value -> seekTo(Millis(value)) },
    onValueChangeFinished = userSeekingComplete,
    modifier = modifier
  )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun RatingBarRow(
  queueIndex: Int,
  queueSize: Int,
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
  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = toggleEqMode, modifier = Modifier.size(26.dp)) {
        Icon(
          painter = painterResource(id = R.drawable.ic_audio_equalizer),
          contentDescription = "Toggle Equalizer",
          modifier = Modifier.size(26.dp),
          tint = LocalContentColor.current.copy(alpha = if (eqMode.isOn()) ALPHA_ON else ALPHA_OFF)
        )
      }
      Spacer(modifier = Modifier.height(26.dp))
      Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
        Text(
          text = "%.2fX".format(playbackRate.value),
          textAlign = TextAlign.Center,
          maxLines = 1,
          style = toqueTypography.caption,
          modifier = Modifier
            .border(1.dp, LocalContentColor.current)
            .padding(2.dp)
            .clickable(onClick = setPlaybackRate)
        )
      }
      Spacer(modifier = Modifier.height(26.dp))
      RatingBar(
        modifier = Modifier.wrapContentSize(),
        value = rating.toStarRating().value,
        config = RatingBarConfig()
          .size(22.dp)
          .padding(2.dp)
          .isIndicator(true)
          .activeColor(LocalContentColor.current)
          .inactiveColor(LocalContentColor.current)
          .stepSize(StepSize.HALF)
          .style(RatingBarStyle.HighLighted),
        onValueChange = {},
        onRatingChanged = {},
      )
      Spacer(modifier = Modifier.height(26.dp))
      IconButton(onClick = nextRepeatMode, modifier = Modifier.size(26.dp)) {
        Icon(
          painter = painterResource(repeatMode.drawable),
          contentDescription = stringResource(id = repeatMode.titleRes),
          modifier = Modifier.size(26.dp),
          tint = LocalContentColor.current.copy(
            alpha = if (repeatMode.isOn()) ALPHA_ON else ALPHA_OFF
          )
        )
      }
      Spacer(modifier = Modifier.height(26.dp))
      IconButton(onClick = nextShuffleMode, modifier = Modifier.size(26.dp)) {
        Icon(
          painter = painterResource(shuffleMode.drawable),
          contentDescription = stringResource(id = shuffleMode.titleRes),
          modifier = Modifier.size(26.dp),
          tint = LocalContentColor.current.copy(
            alpha = if (shuffleMode.isOn()) ALPHA_ON else ALPHA_OFF
          )
        )
      }
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 8.dp, top = 4.dp, end = 8.dp),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${queueIndex + 1}/${queueSize}",
        textAlign = TextAlign.End,
        maxLines = 1,
        style = toqueTypography.overline,
      )
    }
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun ButtonRow(
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
      Icon(
        painter = painterResource(id = R.drawable.ic_prev_list),
        contentDescription = "Play previous list",
        modifier = Modifier.size(38.dp)
      )
    }
    IconButton(onClick = prev, modifier = Modifier.size(50.dp)) {
      Icon(
        painter = painterResource(id = R.drawable.ic_previous),
        contentDescription = "Rewind or previous",
        modifier = Modifier.size(44.dp),
      )
    }
    PlayPauseButton(togglePlayPause, playState)
    IconButton(onClick = next, modifier = Modifier.size(50.dp)) {
      Icon(
        painter = painterResource(id = R.drawable.ic_baseline_skip_next_24),
        contentDescription = "Play next",
        modifier = Modifier.size(44.dp)
      )
    }
    IconButton(onClick = nextList, modifier = Modifier.size(50.dp)) {
      Icon(
        painter = painterResource(id = R.drawable.ic_next_list),
        contentDescription = "Play next list",
        modifier = Modifier.size(38.dp)
      )
    }
  }
}

@Composable
private fun PlayPauseButton(
  togglePlayPause: () -> Unit,
  playState: PlayState
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val drawable = when {
    playState.isPlaying -> when {
      isPressed -> R.drawable.ic_baseline_pause_circle_filled_24
      else -> R.drawable.ic_baseline_pause_circle_outline_24
    }
    else -> when {
      isPressed -> R.drawable.ic_baseline_play_circle_filled_24
      else -> R.drawable.ic_baseline_play_circle_outline_24
    }
  }

  IconButton(
    onClick = togglePlayPause,
    modifier = Modifier.size(50.dp),
    interactionSource = interactionSource
  ) {
    Icon(
      painter = painterResource(id = drawable),
      contentDescription = "Toggle play pause",
      modifier = Modifier.size(50.dp)
    )
  }
}

private fun portraitConstraints(): ConstraintSet = ConstraintSet {
  val buttonRow = createRefFor(ID_BUTTON_ROW)
  val slider = createRefFor(ID_POSITION_SLIDER)
  val position = createRefFor(ID_POSITION_TEXT)
  val duration = createRefFor(ID_DURATION_TEXT)
  val extraInfo = createRefFor(ID_EXTRA_INFO)
  val titles = createRefFor(ID_ITEM_TITLES)
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
    bottom.linkTo(titles.top)
    height = Dimension.fillToConstraints
  }
  constrain(titles) {
    top.linkTo(titleSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(sliderSpace.top)
    height = Dimension.wrapContent
  }
  constrain(sliderSpace) {
    top.linkTo(titles.bottom)
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

private fun landscapeConstraints(): ConstraintSet = ConstraintSet {
  val buttonRow = createRefFor(ID_BUTTON_ROW)
  val slider = createRefFor(ID_POSITION_SLIDER)
  val position = createRefFor(ID_POSITION_TEXT)
  val duration = createRefFor(ID_DURATION_TEXT)
  val extraInfo = createRefFor(ID_EXTRA_INFO)
  val titles = createRefFor(ID_ITEM_TITLES)
  val sliderSpace = createRefFor(ID_SLIDER_SPACE)
  val topSpace = createRefFor(ID_TOP_SPACE)
  val ratingBarRow = createRefFor(ID_RATING_BAR_ROW)
  constrain(topSpace) {
    top.linkTo(parent.top)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(titles.top)
    height = Dimension.fillToConstraints
  }
  constrain(titles) {
    top.linkTo(topSpace.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    bottom.linkTo(sliderSpace.top)
    height = Dimension.wrapContent
  }
  constrain(sliderSpace) {
    top.linkTo(titles.bottom)
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
