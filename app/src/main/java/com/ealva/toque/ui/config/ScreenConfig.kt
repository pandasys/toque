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

package com.ealva.toque.ui.config

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.android.content.inPortrait
import com.google.accompanist.insets.WindowInsets

data class ScreenConfig(
  val inPortrait: Boolean,
  val screenWidthDp: Dp,
  val screenWidthPx: Int,
  val screenHeightDp: Dp,
  val screenHeightPx: Int,
  val statusBarHeight: Dp,
  val navLeft: Dp,
  val navRight: Dp,
  val navBottom: Dp,
  val density: Density
) {
  val imageSizePx: Int
    get() = if (inPortrait) screenWidthPx else screenHeightPx
  val imageSizeDp: Dp
    get() = if (inPortrait) screenHeightDp else screenHeightDp
  val navOnLeft: Boolean
    get() = navLeft > navRight

  fun getButtonBarHeight(): Dp = MainBottomSheet.getButtonBarHeight(inPortrait)

  fun getMiniPlayerHeight(): Dp = MainBottomSheet.getMiniPlayerHeight(inPortrait)

  fun getBottomSheetHeight(isExpanded: Boolean): Dp =
    MainBottomSheet.getBottomSheetHeight(isExpanded, inPortrait)

  fun getNavPlusBottomSheetHeight(isExpanded: Boolean): Dp =
    navBottom + getBottomSheetHeight(isExpanded)

  fun Dp.roundToPx(): Int = with(density) { roundToPx() }
}

val LocalScreenConfig = staticCompositionLocalOf<ScreenConfig> {
  throw IllegalStateException("LocalScaffoldState not provided")
}

@Composable
fun ProvideScreenConfig(
  screenConfig: ScreenConfig,
  content: @Composable () -> Unit
) = CompositionLocalProvider(LocalScreenConfig provides screenConfig) { content() }

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
    navRight = with(density) { insets.navigationBars.right.toDp() },
    navBottom = with(density) { insets.navigationBars.bottom.toDp() },
    density = density
  )
}

object MainBottomSheet {
  val portraitHeight: Dp = 46.dp
  val portraitMiniPlayerHeight: Dp = 42.dp
  val landscapeHeight: Dp = 42.dp
  val landscapeMiniPlayerHeight = 40.dp

  fun getButtonBarHeight(inPortrait: Boolean) = if (inPortrait) portraitHeight else landscapeHeight

  fun getMiniPlayerHeight(inPortrait: Boolean) =
    if (inPortrait) portraitMiniPlayerHeight else landscapeMiniPlayerHeight

  fun getBottomSheetHeight(isExpanded: Boolean, inPortrait: Boolean): Dp =
    getButtonBarHeight(inPortrait) + if (isExpanded) getMiniPlayerHeight((inPortrait)) else 0.dp
}
