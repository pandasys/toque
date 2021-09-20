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

package com.ealva.toque.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.theme.toque
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class SplashScreen(private val noArgPlaceholder: String = "") : ComposeKey() {
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    Splash()
  }
}

@Composable
fun Splash() {
      Column(
      modifier = Modifier
        .padding(top = 60.dp)
        .fillMaxWidth()
    ) {
      Image(
        painter = painterResource(R.drawable.ic_toque),
        modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
        contentDescription = "Toque",
      )
      Text(
        text = stringResource(id = R.string.app_name),
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = toque,
        modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
      )
    }
}
