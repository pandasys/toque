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

package com.ealva.toque.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ealva.toque.prefs.ThemeChoice

private val Amber500 = Color(0xFFFFC107)
private val Amber700 = Color(0xFFFF8F00)

private val LightColorPalette = ToqueColors(
  material = lightColors(
    primary = blue500,
    primaryVariant = blue700,
    secondary = cyan700
  ),
  warning = Amber500,
  onWarning = Color.Black,
  selectedBackground = Color(0xFF_D0_D0_D0),
)

private val DarkColorPalette = ToqueColors(
  material = darkColors(
    primary = blue200,
    primaryVariant = blue700,
    secondary = cyan200,
    background = black,
//    surface = black
  ),
  warning = Amber700,
  onWarning = Color.White,
  selectedBackground = Color(0xFF_30_30_30),
)

private val LocalColors = staticCompositionLocalOf { DarkColorPalette }

@Composable
fun ToqueTheme(
  themeChoice: ThemeChoice,
  content: @Composable () -> Unit,
) {
  val colors = when (themeChoice) {
    ThemeChoice.Dark -> DarkColorPalette
    ThemeChoice.Light -> LightColorPalette
    ThemeChoice.System -> if (isSystemInDarkTheme()) DarkColorPalette else LightColorPalette
  }

  CompositionLocalProvider(LocalColors provides colors) {
    MaterialTheme(
      colors = colors.material,
      content = content,
    )
  }
}

@Suppress("unused")
val MaterialTheme.toqueColors: ToqueColors
  @Composable
  @ReadOnlyComposable
  get() = LocalColors.current
