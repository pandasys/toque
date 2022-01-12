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

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val typography = Typography()

data class ToqueTypography(
  val material: Typography = typography,
  val nowPlayingTitle: TextStyle = material.subtitle1.copy(
    fontSize = material.subtitle1.fontSize * 1.25
  ),
  val nowPlayingArtist: TextStyle = material.body2.copy(
    fontSize = material.body2.fontSize * 1.25
  ),
  val nowPlayingAlbum: TextStyle = material.overline.copy(
    fontSize = material.overline.fontSize * 1.25,
    letterSpacing = 0.5.sp
  )
) {
  val h1: TextStyle get() = material.h1
  val h2: TextStyle get() = material.h2
  val h3: TextStyle get() = material.h3
  val h4: TextStyle get() = material.h4
  val h5: TextStyle get() = material.h5
  val h6: TextStyle get() = material.h6
  val subtitle1: TextStyle get() = material.subtitle1
  val subtitle2: TextStyle get() = material.subtitle2
  val body1: TextStyle get() = material.body1
  val body2: TextStyle get() = material.body2
  val button: TextStyle get() = material.button
  val caption: TextStyle get() = material.caption
  val overline: TextStyle get() = material.overline
}
