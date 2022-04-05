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

package com.ealva.toque.service

import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.PlayableAudioItemFactory
import com.ealva.toque.service.player.WakeLockFactory
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.widget.WidgetUpdater
import org.koin.dsl.module

object ServiceModule {
  val koinModule = module {
    single { WakeLockFactory(powerManager = get()) }
    single {
      PlayableAudioItemFactory(
        audioMediaDao = get(),
        mediaFileStore = get(),
        libVlcSingleton = get(),
        wakeLockFactory = get(),
        appPrefsSingleton = get(AppPrefs.QUALIFIER),
        libVlcPrefsSingleton = get(LibVlcPrefs.QUALIFIER)
      )
    }
    single {
      PlayableQueueFactory(
        queuePositionStateDaoFactory = get(),
        playableAudioItemFactory = get(),
        audioManager = get(),
        appPrefsSingleton = get(AppPrefs.QUALIFIER),
        libVlcPrefsSingleton = get(LibVlcPrefs.QUALIFIER),
      )
    }
    single { WidgetUpdater(get()) }
  }
}
