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

package com.ealva.toque.ui.common

import androidx.compose.ui.graphics.Color

//private val COLOR_RANGE = 0f..1f

fun Color.timesAlpha(alphaPercentage: Float): Color = copy(alpha = alpha * alphaPercentage)

//fun Color.times(percentage: Float): Color =
//  copy(
//    red = (red * percentage).coerceIn(COLOR_RANGE),
//    green = green * percentage.coerceIn(COLOR_RANGE),
//    blue = blue * percentage.coerceIn(COLOR_RANGE)
//  )
