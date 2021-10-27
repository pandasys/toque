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

package com.ealva.toque.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.db.ComposerDao
import com.ealva.toque.db.ComposerDescription
import com.ealva.toque.log._e
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.AllComposersViewModel.ComposerInfo
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(AllComposersScreen::class)

@Immutable
@Parcelize
data class AllComposersScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllComposersViewModel(get())) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllComposersViewModel>()
    val composers = viewModel.allComposers.collectAsState()
    AllComposers(composers.value)
  }
}

@Composable
private fun AllComposers(list: List<ComposerInfo>) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current
  val sheetHeight = config.getNavPlusBottomSheetHeight(isExpanded = true)

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = sheetHeight, end = 8.dp),
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
  ) {
    items(list) { composerInfo -> ComposerItem(composerInfo = composerInfo) }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ComposerItem(composerInfo: ComposerInfo) {
  ListItem(
    modifier = Modifier.fillMaxWidth(),
    icon = {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_person),
        contentDescription = "Composer icon",
        modifier = Modifier.size(40.dp)
      )
    },
    text = { Text(text = composerInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          composerInfo.songCount,
          composerInfo.songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private interface AllComposersViewModel {
  data class ComposerInfo(
    val id: ComposerId,
    val name: ComposerName,
    val songCount: Int
  )

  val allComposers: StateFlow<List<ComposerInfo>>

  companion object {
    operator fun invoke(
      composerDao: ComposerDao,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AllComposersViewModel =
      AllComposersViewModelImpl(composerDao, dispatcher)
  }
}

private class AllComposersViewModelImpl(
  private val composerDao: ComposerDao,
  private val dispatcher: CoroutineDispatcher
) : AllComposersViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  override val allComposers = MutableStateFlow<List<ComposerInfo>>(emptyList())

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + dispatcher)
    scope.launch {
      when (val result = composerDao.getAllComposers()) {
        is Ok -> handleList(result.value)
        is Err -> LOG._e { it("%s", result.error) }
      }
    }
  }

  private fun handleList(list: List<ComposerDescription>) {
    allComposers.value = list.mapTo(ArrayList(list.size)) {
      ComposerInfo(
        id = it.composerId,
        name = it.composerName,
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    allComposers.value = emptyList()
  }
}
