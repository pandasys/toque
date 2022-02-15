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

package com.ealva.toque.art

import com.ealva.toque.prefs.AppPrefs
import org.koin.dsl.module

object ArtworkModule {
  val koinModule = module {
    single { ArtworkDownloader() }
    single { MusicInfoProvider() }
    single {
      DownloadAlbumArtWorkerFactory(
        appPrefs = get(AppPrefs.QUALIFIER),
        albumDao = get(),
        artworkDownloader = get()
      )
    }
    single {
      DownloadArtistArtWorkerFactory(
        appPrefs = get(AppPrefs.QUALIFIER),
        artistDao = get(),
        artworkDownloader = get()
      )
    }
    single {
      ArtworkUpdateListener(
        work = get(),
        albumDao = get(),
        artistDao = get(),
        appPrefs = get(AppPrefs.QUALIFIER),
        albumArtWorkerFactory = get(),
        artistArtWorkerFactory = get()
      )
    }
  }
}
