/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.service.vlc

import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.MediaFactory
import com.ealva.toque.service.media.MediaMetadataParserFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

object LibVlcModule {
  val module = module {
    single { LibVlcPreferencesSingleton(androidContext()) }
    single { LibVlcSingleton(androidContext(), get()) }
    single<MediaMetadataParserFactory> {
      VlcMediaMetadataParserFactory(get())
    }
    single { VlcPresetFactory(androidContext(), get(), get(), get()) } bind EqPresetFactory::class
    single { VlcPlayerFactory(androidContext(), get()) }
    single<MediaFactory> { VlcMediaFactory(get(), get(), get(), get()) }
  }
}
