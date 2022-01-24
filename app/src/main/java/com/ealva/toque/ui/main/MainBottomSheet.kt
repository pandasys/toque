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
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.library.BaseLibraryItemsScreen
import com.ealva.toque.ui.library.SearchScreen
import com.ealva.toque.ui.queue.QueueScreen
import com.ealva.toque.ui.settings.BaseAppSettingsScreen
import com.ealva.toque.ui.theme.shapes
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.zhuinden.simplestackcomposeintegration.services.rememberService

private const val ALPHA_ON = 1.0F
private const val ALPHA_OFF = 0.3F

@OptIn(ExperimentalCoilApi::class, ExperimentalAnimationApi::class)
@Composable
fun MainBottomSheet(
  topOfStack: ComposeKey,
  isExpanded: Boolean,
  goToNowPlaying: () -> Unit,
  goToLibrary: () -> Unit,
  goToQueue: () -> Unit,
  goToSearch: () -> Unit,
  goToSettings: () -> Unit,
  modifier: Modifier
) {
  val config = LocalScreenConfig.current

  Column(
    modifier = modifier
  ) {
    Card(
      shape = shapes.medium,
      elevation = 8.dp
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (isExpanded) {
          CurrentItemPager(
            goToNowPlaying = goToNowPlaying,
            modifier = Modifier
              .fillMaxWidth()
              .height(config.getMiniPlayerHeight())
          )
        }
        MainBottomSheetButtonRow(
          topOfStack = topOfStack,
          goToLibrary = goToLibrary,
          goToQueue = goToQueue,
          goToSearch = goToSearch,
          goToSettings = goToSettings,
          modifier = Modifier
            .fillMaxWidth()
            .height(config.getBottomSheetButtonBarHeight())
        )
      }
    }
    Spacer(
      modifier = Modifier
        .background(Color.Transparent)
        .height(config.navBottom)
        .fillMaxWidth()
    )
  }
}

private fun doNothing() = Unit

@Composable
private fun MainBottomSheetButtonRow(
  topOfStack: ComposeKey,
  goToLibrary: () -> Unit,
  goToQueue: () -> Unit,
  goToSearch: () -> Unit,
  goToSettings: () -> Unit,
  modifier: Modifier
) {
  val onSettingScreen = topOfStack is BaseAppSettingsScreen
  val onLibraryScreen = topOfStack is BaseLibraryItemsScreen
  val onQueueScreen = topOfStack is QueueScreen
  val onSearchScreen = topOfStack is SearchScreen

  val imageSize = LocalScreenConfig.current.getBottomSheetButtonBarHeight() - 12.dp

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
      Icon(
        painter = painterResource(id = R.drawable.ic_map),
        contentDescription = "Library",
        modifier = Modifier.size(imageSize),
        tint = LocalContentColor.current.copy(alpha = if (onLibraryScreen) ALPHA_ON else ALPHA_OFF)
      )
    }
    IconButton(
      onClick = if (!onQueueScreen) goToQueue else ::doNothing,
      modifier = Modifier
        .fillMaxHeight()
        .weight(1.0F)
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_queue),
        contentDescription = "Queue",
        modifier = Modifier.size(imageSize),
        tint = LocalContentColor.current.copy(alpha = if (onQueueScreen) ALPHA_ON else ALPHA_OFF)
      )
    }
    IconButton(
      onClick = if (!onSearchScreen) goToSearch else ::doNothing,
      modifier = Modifier
        .fillMaxHeight()
        .weight(1.0F)
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_search),
        contentDescription = "Search",
        modifier = Modifier.size(imageSize),
        tint = LocalContentColor.current.copy(alpha = if (onSearchScreen) ALPHA_ON else ALPHA_OFF)
      )
    }
    IconButton(
      onClick = if (!onSettingScreen) goToSettings else ::doNothing,
      modifier = Modifier
        .height(48.dp)
        .weight(1.0F)
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_settings),
        contentDescription = "Settings",
        modifier = Modifier.size(imageSize),
        tint = LocalContentColor.current.copy(alpha = if (onSettingScreen) ALPHA_ON else ALPHA_OFF)
      )
    }
  }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun CurrentItemPager(goToNowPlaying: () -> Unit, modifier: Modifier) {
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
      goToNowPlaying = goToNowPlaying
    )
  }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun CurrentItemPagerCard(
  item: AudioItem,
  playState: PlayState,
  togglePlayPause: () -> Unit,
  goToNowPlaying: () -> Unit
) {
  val miniPlayerHeight = LocalScreenConfig.current.getMiniPlayerHeight()
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
        style = toqueTypography.caption,
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
        style = toqueTypography.overline,
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
        Icon(
          painter = painterResource(
            id = if (playState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
          ),
          contentDescription = "Toggle play pause",
          modifier = Modifier.size(miniPlayerHeight - 8.dp)
        )
      }
    }
  }
}
