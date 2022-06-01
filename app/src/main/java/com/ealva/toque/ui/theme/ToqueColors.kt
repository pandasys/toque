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

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

data class ToqueColors(
  val material: Colors,
  val warning: Color,
  val onWarning: Color,
  val selectedBackground: Color,
  val shadedBackground: Color = Color.Black.copy(alpha = 0.3F),
  val uncheckedThumbColor: Color = material.secondaryVariant
    .copy(alpha = .38F)
    .compositeOver(material.surface)
) {
  val primary: Color get() = material.primary
  val primaryVariant: Color get() = material.primaryVariant
  val secondary: Color get() = material.secondary
  val secondaryVariant: Color get() = material.secondaryVariant
  val background: Color get() = material.background
  val surface: Color get() = material.surface
  val error: Color get() = material.error
  val onPrimary: Color get() = material.onPrimary
  val onSecondary: Color get() = material.onSecondary
  val onBackground: Color get() = material.onBackground
  val onSurface: Color get() = material.onSurface
  val onError: Color get() = material.onError
  val isLight: Boolean get() = material.isLight

  companion object
}
