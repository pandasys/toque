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

import com.ealva.musicinfo.service.MusicInfoService
import com.ealva.musicinfo.service.common.AppName
import com.ealva.musicinfo.service.common.AppVersion
import com.ealva.musicinfo.service.common.ContactEmail
import com.ealva.musicinfo.service.lastfm.LastFmService
import com.ealva.musicinfo.service.spotify.SpotifyService
import com.ealva.toque.BuildConfig
import com.ealva.toque.net.NetworkDefaults
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MusicInfoProvider {
  suspend fun getMusicInfoService(): MusicInfoService

  companion object {
    operator fun invoke(): MusicInfoProvider = MusicInfoProviderImpl()
  }
}

private class MusicInfoProviderImpl : MusicInfoProvider {
  @Volatile
  private var instance: MusicInfoService? = null
  private val mutex = Mutex()

  private suspend fun instance(): MusicInfoService = instance ?: mutex.withLock {
    instance ?: make().also { instance = it }
  }

  private suspend fun make(): MusicInfoService {
    return MusicInfoService.make(
      appName = AppName(BuildConfig.MUSICINFO_APP_NAME),
      appVersion = AppVersion(BuildConfig.MUSICINFO_APP_VERSION),
      contactEmail = ContactEmail(BuildConfig.MUSICINFO_CONTACT_EMAIL),
      lastFmApiKey = LastFmService.LastFmApiKey(BuildConfig.LASTFM_API_KEY),
      spotifyClientId = SpotifyService.SpotifyClientId(BuildConfig.SPOTIFY_CLIENT_ID),
      spotifyClientSecret = SpotifyService.SpotifyClientSecret(BuildConfig.SPOTIFY_CLIENT_SECRET),
      addLoggingInterceptor = BuildConfig.DEBUG,
      okHttpClient = NetworkDefaults.okHttpClient,
    )
  }

  override suspend fun getMusicInfoService(): MusicInfoService = instance()
}
