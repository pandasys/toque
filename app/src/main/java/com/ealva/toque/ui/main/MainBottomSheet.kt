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

package com.ealva.toque.ui.main

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.ealva.toque.R
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.ui.config.ScreenConfig
import com.ealva.toque.ui.library.BaseLibraryItemsScreen
import com.ealva.toque.ui.settings.BaseAppSettingsScreen
import com.ealva.toque.ui.theme.shapes
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import kotlinx.coroutines.flow.collect

private const val ALPHA_ON = 1.0F
private const val ALPHA_OFF = 0.3F

private val backgroundColor = Color(0xFF151515)

@OptIn(ExperimentalCoilApi::class, ExperimentalAnimationApi::class)
@Composable
fun MainBottomSheet(
  topOfStack: ComposeKey,
  isExpanded: Boolean,
  goToSettings: () -> Unit,
  goToNowPlaying: () -> Unit,
  goToLibrary: () -> Unit,
  config: ScreenConfig,
  modifier: Modifier
) {
  Card(
    modifier = modifier,
    shape = shapes.medium,
    backgroundColor = Color.Transparent,
    elevation = 8.dp
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (isExpanded) {
        CurrentItemPager(
          goToNowPlaying = goToNowPlaying,
          config,
          modifier = Modifier
            .fillMaxWidth()
            .background(
              color = backgroundColor,
              shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
            .height(config.getMiniPlayerHeight())
        )
      }
      val bottomShape = if (isExpanded)
        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
      else
        RoundedCornerShape(8.dp)

      MainBottomSheetButtonRow(
        topOfStack,
        goToSettings,
        goToLibrary,
        config,
        Modifier
          .fillMaxWidth()
          .background(color = backgroundColor, shape = bottomShape)
          .height(config.getButtonBarHeight())
      )
      Spacer(
        modifier = Modifier
          .height(config.navBottom)
          .fillMaxWidth()
      )
    }
  }
}

private fun doNothing() = Unit

@Composable
private fun MainBottomSheetButtonRow(
  topOfStack: ComposeKey,
  goToSettings: () -> Unit,
  goToLibrary: () -> Unit,
  config: ScreenConfig,
  modifier: Modifier
) {
  val onSettingScreen = topOfStack is BaseAppSettingsScreen
  val onLibraryScreen = topOfStack is BaseLibraryItemsScreen

  val imageSize = config.getButtonBarHeight() - 12.dp

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceAround
  ) {
    IconButton(
      onClick = if (!onLibraryScreen) goToLibrary else ::doNothing,
      modifier = Modifier
        .fillMaxHeight()
        .weight(1.0F)
    ) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_map),
        contentDescription = "Library",
        alpha = if (onLibraryScreen) ALPHA_ON else ALPHA_OFF,
        modifier = Modifier.size(imageSize)
      )
    }
    IconButton(
      onClick = if (!onSettingScreen) goToSettings else ::doNothing,
      modifier = Modifier
        .height(48.dp)
        .weight(1.0F)
    ) {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_settings),
        contentDescription = "Settings",
        alpha = if (onSettingScreen) ALPHA_ON else ALPHA_OFF,
        modifier = Modifier.size(imageSize)
      )
    }
  }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun CurrentItemPager(goToNowPlaying: () -> Unit, config: ScreenConfig, modifier: Modifier) {
  val viewModel = rememberService<LocalAudioMiniPlayerViewModel>()
  val nowPlayingState = viewModel.miniPlayerState.collectAsState()

  val state = nowPlayingState.value
  val queue = state.queue
  val queueIndex = state.queueIndex
  val playingState = state.playingState

  val pagerState = rememberPagerState(initialPage = queueIndex.coerceAtLeast(0))

  if (queueIndex >= 0) {
    LaunchedEffect(key1 = queueIndex) {
      if (queueIndex in queue.indices) pagerState.scrollToPage(queueIndex)
    }
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect { index ->
      viewModel.goToQueueIndexMaybePlay(index)
    }
  }

  HorizontalPager(
    count = queue.size,
    state = pagerState,
    modifier = modifier
  ) { page ->
    CurrentItemPagerCard(
      item = queue[page],
      playState = playingState,
      togglePlayPause = { viewModel.togglePlayPause() },
      goToNowPlaying = goToNowPlaying,
      config = config
    )
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun CurrentItemPagerCard(
  item: AudioItem,
  playState: PlayState,
  togglePlayPause: () -> Unit,
  goToNowPlaying: () -> Unit,
  config: ScreenConfig
) {
  val miniPlayerHeight = config.getMiniPlayerHeight()
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(miniPlayerHeight)
      .clickable(onClick = goToNowPlaying)
  ) {
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
      val (albumArt, title, artistAlbum, playPause) = createRefs()

      Image(
        painter = rememberImagePainter(
          data = if (item.localAlbumArt !== Uri.EMPTY) item.localAlbumArt else item.albumArt,
          builder = {
            error(R.drawable.ic_big_album)
            placeholder(R.drawable.ic_big_album)
          }
        ),
        contentDescription = "${item.title()} Album Cover Art",
        modifier = Modifier
          .size(miniPlayerHeight - 2.dp)
          .padding(2.dp)
          .constrainAs(albumArt) {
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(title.start)
            bottom.linkTo(parent.bottom)
          }
      )
      Text(
        text = item.title.value,
        textAlign = TextAlign.Start,
        maxLines = 1,
        style = MaterialTheme.typography.caption,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .padding(top = 2.dp)
          .padding(horizontal = 8.dp)
          .constrainAs(title) {
            top.linkTo(parent.top)
            start.linkTo(albumArt.end)
            end.linkTo(playPause.start)
            bottom.linkTo(artistAlbum.top)
            width = Dimension.fillToConstraints
          }
      )
      Text(
        text = "${item.artist.value} - ${item.albumTitle.value}",
        textAlign = TextAlign.Start,
        maxLines = 1,
        style = MaterialTheme.typography.overline,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .padding(bottom = 2.dp)
          .padding(horizontal = 8.dp)
          .constrainAs(artistAlbum) {
            top.linkTo(title.bottom)
            start.linkTo(albumArt.end)
            end.linkTo(playPause.start)
            bottom.linkTo(parent.bottom)
            width = Dimension.fillToConstraints
          }
      )
      IconButton(
        onClick = togglePlayPause,
        modifier = Modifier
          .size(miniPlayerHeight)
          .constrainAs(playPause) {
            top.linkTo(parent.top)
            start.linkTo(artistAlbum.end)
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom)
          }
      ) {
        Image(
          painter = rememberImagePainter(
            data = if (playState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
          ),
          contentDescription = "Toggle play pause",
          modifier = Modifier.size(miniPlayerHeight - 8.dp)
        )
      }
    }
  }
}
