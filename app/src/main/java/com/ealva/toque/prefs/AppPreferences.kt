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
import com.ealva.toque.persist.toEnum
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_DUCK_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_DUCK_VOLUME
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_END_OF_QUEUE_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_FADE_ON_PLAY_PAUSE
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_LAST_SCAN_TIME
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_PAUSE_FADE
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_UP_NEXT_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_SCROBBLER
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_SELECT_MEDIA_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DUCK_VOLUME_RANGE
import com.ealva.toque.prefs.AppPreferences.Companion.PLAY_PAUSE_FADE_RANGE
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.DUCK_ACTION
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.DUCK_VOLUME
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.END_OF_QUEUE_ACTION
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.FADE_ON_PLAY_PAUSE
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.LAST_SCAN_TIME
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.PLAY_PAUSE_FADE_LENGTH
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.PLAY_UP_NEXT_ACTION
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.SCROBBLER
import com.ealva.toque.prefs.AppPreferencesImpl.Keys.SELECT_MEDIA_ACTION
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

  fun playUpNextAction(): PlayUpNextAction
  suspend fun playUpNextAction(action: PlayUpNextAction): Boolean

  fun endOfQueueAction(): EndOfQueueAction
  suspend fun endOfQueueAction(action: EndOfQueueAction): Boolean

  fun selectMediaAction(): SelectMediaAction
  suspend fun selectMediaAction(action: SelectMediaAction): Boolean

  suspend fun resetAllToDefault()

  /** For test */
  fun asMap(): Map<Preferences.Key<*>, Any>

  companion object {
    const val DEFAULT_ALLOW_DUPLICATES = false
    const val DEFAULT_GO_TO_NOW_PLAYING = true
    const val DEFAULT_IGNORE_SMALL_FILES = false
    val DEFAULT_IGNORE_THRESHOLD = Millis.ZERO
    val DEFAULT_LAST_SCAN_TIME = Millis.ZERO
    const val DEFAULT_FADE_ON_PLAY_PAUSE = false
    val PLAY_PAUSE_FADE_RANGE = 500.toMillis()..2000.toMillis()
    val DEFAULT_PLAY_PAUSE_FADE = 1000.toMillis().coerceIn(PLAY_PAUSE_FADE_RANGE)
    val DEFAULT_SCROBBLER = ScrobblerPackage.None
    val DEFAULT_DUCK_ACTION = DuckAction.Duck
    val DUCK_VOLUME_RANGE = Volume.ZERO..Volume.ONE_HUNDRED
    val DEFAULT_DUCK_VOLUME = 50.toVolume().coerceIn(DUCK_VOLUME_RANGE)
    val DEFAULT_PLAY_UP_NEXT_ACTION = PlayUpNextAction.Prompt
    val DEFAULT_END_OF_QUEUE_ACTION = EndOfQueueAction.PlayNextList
    val DEFAULT_SELECT_MEDIA_ACTION = SelectMediaAction.Play
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

  override suspend fun instance(): AppPreferences = instance ?: withContext(dispatcher) {
    mutex.withLock { instance ?: make().also { instance = it } }
  }
}

@Suppress("FunctionName", "SameParameterValue")
private fun KeyDefaultVolume(name: String, defaultValue: Volume) =
  KeyDefault(name, defaultValue, ::Volume, { it.value })

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
    val ALLOW_DUPLICATES = KeyDefault("allow_duplicates", DEFAULT_ALLOW_DUPLICATES)
    val GO_TO_NOW_PLAYING = KeyDefault("go_to_now_playing", DEFAULT_GO_TO_NOW_PLAYING)
    val IGNORE_SMALL_FILES = KeyDefault("ignore_small_files", DEFAULT_IGNORE_SMALL_FILES)
    val IGNORE_THRESHOLD = KeyDefaultMillis("ignore_threshold", DEFAULT_IGNORE_THRESHOLD)
    val LAST_SCAN_TIME = KeyDefaultMillis("last_scan_time", DEFAULT_LAST_SCAN_TIME)
    val FADE_ON_PLAY_PAUSE = KeyDefault("fade_on_play_pause", DEFAULT_FADE_ON_PLAY_PAUSE)
    val PLAY_PAUSE_FADE_LENGTH = KeyDefaultMillis("play_pause_fade_length", DEFAULT_PLAY_PAUSE_FADE)
    val SCROBBLER = EnumKeyDefault(DEFAULT_SCROBBLER)
    val DUCK_ACTION = EnumKeyDefault(DEFAULT_DUCK_ACTION)
    val DUCK_VOLUME = KeyDefaultVolume("duck_volume", DEFAULT_DUCK_VOLUME)
    val PLAY_UP_NEXT_ACTION = EnumKeyDefault(DEFAULT_PLAY_UP_NEXT_ACTION)
    val END_OF_QUEUE_ACTION = EnumKeyDefault(DEFAULT_END_OF_QUEUE_ACTION)
    val SELECT_MEDIA_ACTION = EnumKeyDefault(DEFAULT_SELECT_MEDIA_ACTION)
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
    .map { id -> id.toEnum(ScrobblerPackage.None) }

  override fun duckAction(): DuckAction = state.value[DUCK_ACTION]
  override suspend fun duckAction(action: DuckAction): Boolean =
    dataStore.set(DUCK_ACTION, action)

  override fun duckVolume(): Volume = state.value[DUCK_VOLUME]
  override suspend fun duckVolume(volume: Volume): Boolean =
    dataStore.set(DUCK_VOLUME, volume.coerceIn(DUCK_VOLUME_RANGE))

  override fun playUpNextAction(): PlayUpNextAction = state.value[PLAY_UP_NEXT_ACTION]
  override suspend fun playUpNextAction(action: PlayUpNextAction): Boolean =
    dataStore.set(PLAY_UP_NEXT_ACTION, action)

  override fun endOfQueueAction(): EndOfQueueAction = state.value[END_OF_QUEUE_ACTION]
  override suspend fun endOfQueueAction(action: EndOfQueueAction): Boolean =
    dataStore.set(END_OF_QUEUE_ACTION, action)

  override fun selectMediaAction(): SelectMediaAction = state.value[SELECT_MEDIA_ACTION]
  override suspend fun selectMediaAction(action: SelectMediaAction): Boolean =
    dataStore.set(SELECT_MEDIA_ACTION, action)

  override fun asMap(): Map<Preferences.Key<*>, Any> = state.value.asMap()

  override suspend fun resetAllToDefault() {
    dataStore.put {
      this[ALLOW_DUPLICATES.key] = ALLOW_DUPLICATES.defaultAsStored()
      this[GO_TO_NOW_PLAYING.key] = GO_TO_NOW_PLAYING.defaultAsStored()
      this[IGNORE_SMALL_FILES.key] = IGNORE_SMALL_FILES.defaultAsStored()
      this[IGNORE_THRESHOLD.key] = IGNORE_THRESHOLD.defaultAsStored()
      // Don't reset last scan time
      // this[LAST_SCAN_TIME.key] = LAST_SCAN_TIME.realToStored(LAST_SCAN_TIME.defaultValue)
      this[FADE_ON_PLAY_PAUSE.key] = FADE_ON_PLAY_PAUSE.defaultAsStored()
      this[PLAY_PAUSE_FADE_LENGTH.key] = PLAY_PAUSE_FADE_LENGTH.defaultAsStored()
      this[SCROBBLER.key] = SCROBBLER.defaultAsStored()
      this[DUCK_ACTION.key] = DUCK_ACTION.defaultAsStored()
      this[DUCK_VOLUME.key] = DUCK_VOLUME.defaultAsStored()
      this[PLAY_UP_NEXT_ACTION.key] = PLAY_UP_NEXT_ACTION.defaultAsStored()
      this[END_OF_QUEUE_ACTION.key] = END_OF_QUEUE_ACTION.defaultAsStored()
      this[SELECT_MEDIA_ACTION.key] = SELECT_MEDIA_ACTION.defaultAsStored()
    }
  }
}
