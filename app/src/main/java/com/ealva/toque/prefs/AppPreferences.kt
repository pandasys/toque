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

package com.ealva.toque.prefs

import android.content.Context
import androidx.datastore.DataStore
import androidx.datastore.preferences.Preferences
import androidx.datastore.preferences.preferencesKey
import com.ealva.toque.common.Millis
import com.ealva.toque.common.toMillis
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_PAUSE_FADE_LENGTH
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.FADE_ON_PLAY_PAUSE
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.LAST_SCAN_TIME
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.PLAY_PAUSE_FADE_LENGTH
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DATA_STORE_FILE_NAME = "user"

interface AppPreferences {
  fun allowDuplicates(): Boolean
  suspend fun allowDuplicates(value: Boolean): Boolean
  fun goToNowPlaying(): Boolean
  suspend fun goToNowPlaying(value: Boolean): Boolean
  fun ignoreSmallFiles(): Boolean
  suspend fun ignoreSmallFiles(value: Boolean): Boolean
  fun ignoreThreshold(): Millis
  suspend fun ignoreThreshold(time: Millis): Boolean
  fun lastScanTime(): Millis
  suspend fun lastScanTime(time: Millis): Boolean
  fun fadeOnPlayPause(): Boolean
  suspend fun fadeOnPlayPause(fade: Boolean): Boolean
  fun playPauseFadeLength(): Millis
  suspend fun playPauseFadeLength(millis: Millis): Boolean

  companion object {
    const val DEFAULT_PLAY_PAUSE_FADE_LENGTH = 500L
  }
}

interface AppPreferencesSingleton {
  suspend fun instance(): AppPreferences

  companion object {
    operator fun invoke(
      context: Context,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): AppPreferencesSingleton = AppPrefsSingletonImpl(context, dispatcher)
  }
}

private class AppPrefsSingletonImpl(
  private val context: Context,
  private val dispatcher: CoroutineDispatcher
) : AppPreferencesSingleton {
  private suspend fun make(): AppPreferences {
    val dataStore = context.makeDataStore(DATA_STORE_FILE_NAME, dispatcher)
    val stateFlow = dataStore.data.stateIn(CoroutineScope(dispatcher + SupervisorJob()))
    return AppPreferencesImpl(dataStore, stateFlow)
  }

  @Volatile
  private var instance: AppPreferences? = null
  private val mutex = Mutex()
  override suspend fun instance(): AppPreferences {
    instance?.let { return it } ?: return withContext(dispatcher) {
      mutex.withLock {
        instance?.let { instance } ?: make().also { instance = it }
      }
    }
  }
}

/**
 * Get/set app preferences. Get methods return the default value if an IO exception occurs.
 *
 * Kotlin suspending var would be nice
 */
@Suppress("PropertyName")
private class AppPreferencesImpl(
  private val dataStore: DataStore<Preferences>,
  private val state: StateFlow<Preferences>
) : AppPreferences {
  object Keys {
    val ALLOW_DUPLICATES = preferencesKey<Boolean>("allow_duplicates")
    val GO_TO_NOW_PLAYING = preferencesKey<Boolean>("go_to_now_playing")
    val IGNORE_SMALL_FILES = preferencesKey<Boolean>("ignore_small_files")
    val IGNORE_THRESHOLD = preferencesKey<Long>("ignore_threshold")
    val LAST_SCAN_TIME = preferencesKey<Long>("last_scan_time")
    val FADE_ON_PLAY_PAUSE = preferencesKey<Boolean>("fade_on_play_pause")
    val PLAY_PAUSE_FADE_LENGTH = preferencesKey<Long>("play_pause_fade_length")
  }

  override fun allowDuplicates(): Boolean = state.value[ALLOW_DUPLICATES, false]
  override suspend fun allowDuplicates(value: Boolean): Boolean =
    dataStore.set(ALLOW_DUPLICATES, value)

  override fun goToNowPlaying(): Boolean = state.value[GO_TO_NOW_PLAYING, true]
  override suspend fun goToNowPlaying(value: Boolean): Boolean =
    dataStore.set(GO_TO_NOW_PLAYING, value)

  override fun ignoreSmallFiles(): Boolean = state.value[IGNORE_SMALL_FILES, false]
  override suspend fun ignoreSmallFiles(value: Boolean): Boolean =
    dataStore.set(IGNORE_SMALL_FILES, value)

  override fun ignoreThreshold(): Millis = state.value[IGNORE_THRESHOLD, 0].toMillis()
  override suspend fun ignoreThreshold(time: Millis): Boolean =
    dataStore.set(IGNORE_THRESHOLD, time.value)

  override fun lastScanTime(): Millis = state.value[LAST_SCAN_TIME, 0].toMillis()
  override suspend fun lastScanTime(time: Millis): Boolean =
    dataStore.set(LAST_SCAN_TIME, time.value)

  override fun fadeOnPlayPause(): Boolean = state.value[FADE_ON_PLAY_PAUSE, false]
  override suspend fun fadeOnPlayPause(fade: Boolean): Boolean =
    dataStore.set(FADE_ON_PLAY_PAUSE, fade)

  override fun playPauseFadeLength(): Millis =
    state.value[PLAY_PAUSE_FADE_LENGTH, DEFAULT_PLAY_PAUSE_FADE_LENGTH].toMillis()
  override suspend fun playPauseFadeLength(millis: Millis): Boolean =
    dataStore.set(PLAY_PAUSE_FADE_LENGTH, millis.value)
}
