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

package com.ealva.toque.ui.library

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.GenreId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.github.michaelbull.result.Result
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

@Immutable
@Parcelize
data class GenreSongsScreen(
  private val genreId: GenreId,
  private val genreName: GenreName,
  private val artwork: Uri,
  private val backTo: String
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(GenreSongsViewModel(genreId, get(), lookup(), get(AppPrefs.QUALIFIER), backstack))
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<GenreSongsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.collectAsState()

    val tertiaryInfo = songs.value.toSongCount()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      ScreenHeaderWithArtwork(artwork = artwork) {
        SongListHeaderInfo(
          title = genreName.value,
          subtitle = null,
          tertiaryInfo = tertiaryInfo,
          itemCount = songs.value.size,
          selectedItems = selected.value,
          viewModel = viewModel,
          buttonColors = ActionButtonDefaults.overArtworkColors(),
          backTo = backTo,
          scrollConnection = scrollConnection,
          back = { viewModel.goBack() }
        )
      }
      SongItemList(
        list = songs.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.mediaClicked(it.id) },
        itemLongClicked = { viewModel.mediaLongClicked(it.id) },
      )
    }
  }
}

private class GenreSongsViewModel(
  private val genreId: GenreId,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  appPrefs: AppPrefsSingleton,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, appPrefs, backstack, dispatcher),
  ScopedServices.Activated {
  override val categoryToken: CategoryToken
    get() = CategoryToken(genreId)

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> =
    audioMediaDao.getGenreAudio(genreId = genreId, filter = filter)
}
