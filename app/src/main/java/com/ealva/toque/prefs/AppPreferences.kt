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
import androidx.datastore.preferences.createDataStore
import androidx.datastore.preferences.preferencesKey
import com.ealva.toque.prefs.AppPreferences.Keys.ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferences.Keys.GO_TO_NOW_PLAYING
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Get/set app preferences. Get methods return the default value if an IO exception occurs.
 */
@Suppress("PropertyName")
class AppPreferences private constructor(private val dataStore: DataStore<Preferences>) {
  object Keys {
    private inline fun <reified T : Any> key(name: String, defVal: T): KeyDefault<T> {
      return Pair(preferencesKey(name), defVal)
    }

    val ALLOW_DUPLICATES = key("allow_duplicates", false)
    val GO_TO_NOW_PLAYING = key("go_to_now_playing", true)
  }

  suspend fun allowDuplicates(): Boolean = dataStore.get(ALLOW_DUPLICATES)
  suspend fun allowDuplicates(value: Boolean): Boolean =
    dataStore.set(ALLOW_DUPLICATES.key, value)

  suspend fun goToNowPlaying(): Boolean = dataStore.get(GO_TO_NOW_PLAYING)
  suspend fun goToNowPlaying(value: Boolean): Boolean =
    dataStore.set(GO_TO_NOW_PLAYING.key, value)

  companion object {
    operator fun invoke(
      context: Context,
      coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): AppPreferences = AppPreferences(
      context.applicationContext.createDataStore(
        "user",
        scope = CoroutineScope(coroutineDispatcher + SupervisorJob())
      )
    )

    internal operator fun invoke(dataStore: DataStore<Preferences>): AppPreferences {
      return AppPreferences(dataStore)
    }
  }
}
