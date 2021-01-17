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
import androidx.datastore.preferences.MutablePreferences
import androidx.datastore.preferences.Preferences
import androidx.datastore.preferences.createDataStore
import androidx.datastore.preferences.edit
import androidx.datastore.preferences.emptyPreferences
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.toEnum
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val DataStore<Preferences>.LOG by lazyLogger("DataStorePrefs")

data class KeyDefault<T>(val key: Preferences.Key<T>, val defaultValue: T)

fun Context.makeDataStore(
  name: String,
  dispatcher: CoroutineDispatcher
): DataStore<Preferences> = applicationContext.createDataStore(
  name,
  scope = CoroutineScope(dispatcher + SupervisorJob())
)

suspend inline fun DataStore<Preferences>.put(
  crossinline mutableFunc: MutablePreferences.() -> Unit
): Preferences = edit {
  mutableFunc(it)
}

suspend fun <T> DataStore<Preferences>.set(key: Preferences.Key<T>, value: T): Boolean =
  try {
    put { this[key] = value }
    true
  } catch (e: Exception) {
    LOG.e(e) { it("Exception setting value:'$value' for key:'$key'") }
    false
  }

suspend inline fun <T> DataStore<Preferences>.set(
  key: Preferences.Key<Int>,
  value: T
): Boolean where T : Enum<T>, T : HasConstId = set(key, value.id)

operator fun <T> Preferences.get(key: Preferences.Key<T>, defVal: T): T {
  return get(key) ?: defVal
}

inline operator fun <reified T> Preferences.get(
  key: Preferences.Key<Int>,
  defVal: T
): T where T : Enum<T>, T : HasConstId {
  return get(key).toEnum(defVal)
}

operator fun <T> Preferences.get(pair: KeyDefault<T>): T {
  return get(pair.key) ?: pair.defaultValue
}

/**
 * Make a flow of values for [keyDefault].key defaulting to [keyDefault].defaultValue if the
 * key is not present. Values will be [distinctUntilChanged]
 */
fun <T> DataStore<Preferences>.valueFlow(keyDefault: KeyDefault<T>): Flow<T> {
  return data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .map { preferences -> preferences[keyDefault.key] ?: keyDefault.defaultValue }
    .distinctUntilChanged()
}
