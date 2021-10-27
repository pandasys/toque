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
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.GenreDescription
import com.ealva.toque.persist.GenreId
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.AllGenresViewModel.GenreInfo
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

private val LOG by lazyLogger(AllGenresScreen::class)

@Immutable
@Parcelize
data class AllGenresScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllGenresViewModel(get())) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllGenresViewModel>()
    val genres = viewModel.allGenres.collectAsState()
    AllGenres(genres.value)
  }
}

@Composable
private fun AllGenres(list: List<GenreInfo>) {
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
    items(list) { genreInfo -> GenreItem(genreInfo = genreInfo) }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun GenreItem(genreInfo: GenreInfo) {
  ListItem(
    modifier = Modifier.fillMaxWidth(),
    icon = {
      Image(
        painter = rememberImagePainter(data = R.drawable.ic_guitar_acoustic),
        contentDescription = "Genre icon",
        modifier = Modifier.size(40.dp)
      )
    },
    text = { Text(text = genreInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          genreInfo.songCount,
          genreInfo.songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private interface AllGenresViewModel {
  data class GenreInfo(
    val id: GenreId,
    val name: GenreName,
    val songCount: Int
  )

  val allGenres: StateFlow<List<GenreInfo>>

  companion object {
    operator fun invoke(
      genreDao: GenreDao,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AllGenresViewModel = AllGenresViewModelImpl(genreDao, dispatcher)
  }
}

private class AllGenresViewModelImpl(
  private val genreDao: GenreDao,
  private val dispatcher: CoroutineDispatcher
) : AllGenresViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val allGenres = MutableStateFlow<List<GenreInfo>>(emptyList())

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + dispatcher)
    scope.launch {
      when (val result = genreDao.getAllGenres()) {
        is Ok -> handleGenreList(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handleGenreList(list: List<GenreDescription>) {
    allGenres.value = list.mapTo(ArrayList(list.size)) {
      GenreInfo(
        id = it.genreId,
        name = it.genreName,
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    allGenres.value = emptyList()
  }
}
