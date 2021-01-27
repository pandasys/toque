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
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.common.toMillis
import com.ealva.toque.common.toVolume
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_DUCK_VOLUME
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_PAUSE_FADE
import com.ealva.toque.prefs.AppPreferences.Companion.DUCK_VOLUME_RANGE
import com.ealva.toque.prefs.AppPreferences.Companion.PLAY_PAUSE_FADE_RANGE
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.DUCK_ACTION
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.DUCK_VOLUME
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.FADE_ON_PLAY_PAUSE
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.LAST_SCAN_TIME
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.PLAY_PAUSE_FADE_LENGTH
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.SCROBBLER
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
  fun scrobbler(): ScrobblerPackage
  suspend fun scrobbler(scrobblerPackage: ScrobblerPackage): Boolean
  fun scrobblerFlow(): Flow<ScrobblerPackage>
  fun duckAction(): DuckAction
  suspend fun duckAction(action: DuckAction): Boolean
  fun duckVolume(): Volume
  suspend fun duckVolume(volume: Volume): Boolean

  suspend fun resetAllToDefault()

  companion object {
    val DEFAULT_IGNORE_THRESHOLD = Millis.ZERO
    val DEFAULT_PLAY_PAUSE_FADE = 1000.toMillis()
    val PLAY_PAUSE_FADE_RANGE = 500.toMillis()..2000.toMillis()
    val DEFAULT_DUCK_VOLUME = 50.toVolume()
    val DUCK_VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED
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
    val stateFlow = dataStore.data.stateIn(MainScope())
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
    val ALLOW_DUPLICATES = KeyDefault("allow_duplicates", false)
    val GO_TO_NOW_PLAYING = KeyDefault("go_to_now_playing", true)
    val IGNORE_SMALL_FILES = KeyDefault("ignore_small_files", false)
    val IGNORE_THRESHOLD =
      KeyDefault("ignore_threshold", DEFAULT_IGNORE_THRESHOLD, ::Millis, { it.value })
    val LAST_SCAN_TIME =
      KeyDefault("last_scan_time", Millis.ZERO, ::Millis, { it.value })
    val FADE_ON_PLAY_PAUSE = KeyDefault("fade_on_play_pause", false)
    val PLAY_PAUSE_FADE_LENGTH =
      KeyDefault("play_pause_fade_length", DEFAULT_PLAY_PAUSE_FADE, ::Millis, { it.value })
    val SCROBBLER = EnumKeyDefault(ScrobblerPackage.None)
    val DUCK_ACTION = EnumKeyDefault(DuckAction.Duck)
    val DUCK_VOLUME =
      KeyDefault("duck_volume", DEFAULT_DUCK_VOLUME, ::Volume, { it.value })
  }

  override fun allowDuplicates(): Boolean = state.value[ALLOW_DUPLICATES]
  override suspend fun allowDuplicates(value: Boolean): Boolean =
    dataStore.set(ALLOW_DUPLICATES, value)

  override fun goToNowPlaying(): Boolean = state.value[GO_TO_NOW_PLAYING]
  override suspend fun goToNowPlaying(value: Boolean): Boolean =
    dataStore.set(GO_TO_NOW_PLAYING, value)

  override fun ignoreSmallFiles(): Boolean = state.value[IGNORE_SMALL_FILES]
  override suspend fun ignoreSmallFiles(value: Boolean): Boolean =
    dataStore.set(IGNORE_SMALL_FILES, value)

  override fun ignoreThreshold(): Millis = state.value[IGNORE_THRESHOLD]
  override suspend fun ignoreThreshold(time: Millis): Boolean =
    dataStore.set(IGNORE_THRESHOLD, time.coerceAtLeast(Millis.ZERO))

  override fun lastScanTime(): Millis = state.value[LAST_SCAN_TIME]
  override suspend fun lastScanTime(time: Millis): Boolean =
    dataStore.set(LAST_SCAN_TIME, time)

  override fun fadeOnPlayPause(): Boolean = state.value[FADE_ON_PLAY_PAUSE]
  override suspend fun fadeOnPlayPause(fade: Boolean): Boolean =
    dataStore.set(FADE_ON_PLAY_PAUSE, fade)

  override fun playPauseFadeLength(): Millis = state.value[PLAY_PAUSE_FADE_LENGTH]
  override suspend fun playPauseFadeLength(millis: Millis): Boolean =
    dataStore.set(PLAY_PAUSE_FADE_LENGTH, millis.coerceIn(PLAY_PAUSE_FADE_RANGE))

  override fun scrobbler(): ScrobblerPackage = state.value[SCROBBLER]
  override suspend fun scrobbler(scrobblerPackage: ScrobblerPackage): Boolean =
    dataStore.set(SCROBBLER, scrobblerPackage)

  override fun scrobblerFlow(): Flow<ScrobblerPackage> = dataStore
    .valueFlow(KeyDefault(SCROBBLER.key, scrobbler().id))
    .map { id -> id.toScrobblerPackage() }

  override fun duckAction(): DuckAction = state.value[DUCK_ACTION]
  override suspend fun duckAction(action: DuckAction): Boolean =
    dataStore.set(DUCK_ACTION, action)

  override fun duckVolume(): Volume = state.value[DUCK_VOLUME]
  override suspend fun duckVolume(volume: Volume): Boolean =
    dataStore.set(DUCK_VOLUME, volume.coerceIn(DUCK_VOLUME_RANGE))

  override suspend fun resetAllToDefault() {
    dataStore.put {
      this[ALLOW_DUPLICATES.key] = ALLOW_DUPLICATES.realToStored(ALLOW_DUPLICATES.defaultValue)
      this[GO_TO_NOW_PLAYING.key] = GO_TO_NOW_PLAYING.realToStored(GO_TO_NOW_PLAYING.defaultValue)
      this[IGNORE_SMALL_FILES.key] =
        IGNORE_SMALL_FILES.realToStored(IGNORE_SMALL_FILES.defaultValue)
      this[IGNORE_THRESHOLD.key] = IGNORE_THRESHOLD.realToStored(IGNORE_THRESHOLD.defaultValue)
      // Don't reset last scan time
      // this[LAST_SCAN_TIME.key] = LAST_SCAN_TIME.realToStored(LAST_SCAN_TIME.defaultValue)
      this[FADE_ON_PLAY_PAUSE.key] =
        FADE_ON_PLAY_PAUSE.realToStored(FADE_ON_PLAY_PAUSE.defaultValue)
      this[PLAY_PAUSE_FADE_LENGTH.key] =
        PLAY_PAUSE_FADE_LENGTH.realToStored(PLAY_PAUSE_FADE_LENGTH.defaultValue)
      this[SCROBBLER.key] = SCROBBLER.realToStored(SCROBBLER.defaultValue)
      this[DUCK_ACTION.key] = DUCK_ACTION.realToStored(DUCK_ACTION.defaultValue)
      this[DUCK_VOLUME.key] = DUCK_VOLUME.realToStored(DUCK_VOLUME.defaultValue)
    }
  }
}
