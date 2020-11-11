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

import androidx.datastore.DataStore
import androidx.datastore.preferences.MutablePreferences
import androidx.datastore.preferences.Preferences
import androidx.datastore.preferences.edit
import androidx.datastore.preferences.emptyPreferences
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

typealias KeyDefault<T> = Pair<Preferences.Key<T>, T>
inline val <T> KeyDefault<T>.key: Preferences.Key<T>
  get() = first
inline val <T> KeyDefault<T>.defaultValue: T
  get() = second


val DataStore<Preferences>.LOG by lazyLogger("DataStorePrefs")

suspend fun <T> DataStore<Preferences>.set(key: Preferences.Key<T>, value: T): Boolean =
  try {
    put { this[key] = value }
    true
  } catch (e: Exception) {
    LOG.e(e) { it("Exception setting value:'$value' for key:'$key'") }
    false
  }

suspend inline fun DataStore<Preferences>.put(
  crossinline mutableFunc: MutablePreferences.() -> Unit
): Preferences = edit {
  mutableFunc(it)
}

suspend inline fun <T> DataStore<Preferences>.get(pair: KeyDefault<T>): T =
  get(pair.key, pair.defaultValue)

suspend fun <T> DataStore<Preferences>.get(
  key: Preferences.Key<T>,
  defaultVal: T
): T = try {
    data.catch {
      if (it is IOException) {
        LOG.e { it("Exception getting value for key:'$key', returning default '$defaultVal'") }
        emit(emptyPreferences())
      } else {
        throw it
      }
    }.map {
      it[key] ?: defaultVal
    }.first()
} catch (e: Exception) {
  LOG.e { it("Exception getting DataStore.data for key:'$key', returning default '$defaultVal'") }
  defaultVal
}
