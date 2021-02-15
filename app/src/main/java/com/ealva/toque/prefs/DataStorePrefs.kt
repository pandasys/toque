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
import androidx.datastore.preferences.preferencesKey
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
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

fun Context.makeDataStore(name: String, dispatcher: CoroutineDispatcher): DataStore<Preferences> =
  applicationContext.createDataStore(name, scope = CoroutineScope(dispatcher + SupervisorJob()))

interface PrefKeyValue<Stored : Any, Actual : Any> {
  val key: Preferences.Key<Stored>
  val defaultValue: Actual
  fun storedToActual(stored: Stored?): Actual
  fun actualToStored(actual: Actual): Stored
  fun defaultAsStored(): Stored = actualToStored(defaultValue)
}

interface UnmappedPrefKeyValue<T : Any> : PrefKeyValue<T, T> {
  override val key: Preferences.Key<T>
  override val defaultValue: T
  override fun storedToActual(stored: T?): T = stored ?: defaultValue
  override fun actualToStored(actual: T): T = actual
}

class KeyDefault<T : Any>(
  override val key: Preferences.Key<T>,
  override val defaultValue: T
) : UnmappedPrefKeyValue<T>

@Suppress("FunctionName")
inline fun <reified T : Any> KeyDefault(name: String, theDefaultValue: T): KeyDefault<T> =
  KeyDefault(preferencesKey(name), theDefaultValue)

@Suppress("FunctionName")
inline fun <reified T> EnumKeyDefault(
  theDefaultValue: T
): PrefKeyValue<Int, T> where T : Enum<T>, T : HasConstId {
  return object : PrefKeyValue<Int, T> {
    override val key: Preferences.Key<Int> = preferencesKey(T::class.java.simpleName)
    override val defaultValue: T = theDefaultValue
    override fun storedToActual(stored: Int?): T = stored.toEnum(theDefaultValue)
    override fun actualToStored(actual: T): Int = actual.id
  }
}

@Suppress("FunctionName")
inline fun <reified S : Any, reified A : Any> KeyDefault(
  name: String,
  theDefaultValue: A,
  crossinline maker: (S) -> A,
  crossinline extract: (A) -> S
): PrefKeyValue<S, A> {
  return object : PrefKeyValue<S, A> {
    override val key: Preferences.Key<S> = preferencesKey(name)
    override val defaultValue: A = theDefaultValue
    override fun storedToActual(stored: S?): A = stored?.let { maker(it) } ?: theDefaultValue
    override fun actualToStored(actual: A): S = extract(actual)
  }
}

@Suppress("FunctionName")
fun KeyDefaultMillis(name: String, defaultValue: Millis) =
  KeyDefault(name, defaultValue, ::Millis, { it.value })

suspend inline fun DataStore<Preferences>.put(
  crossinline mutableFunc: MutablePreferences.() -> Unit
): Preferences = edit { mutableFunc(it) }

suspend fun <T : Any> DataStore<Preferences>.set(key: Preferences.Key<T>, value: T) = try {
  put { this[key] = value }
  true
} catch (e: Exception) {
  LOG.e(e) { it("Exception setting value:'$value' for key:'$key'") }
  false
}

suspend inline fun <S : Any, A : Any> DataStore<Preferences>.set(
  prefKeyValue: PrefKeyValue<S, A>,
  value: A
): Boolean = set(prefKeyValue.key, prefKeyValue.actualToStored(value))

suspend inline fun <T : Any> DataStore<Preferences>.set(
  prefKeyValue: UnmappedPrefKeyValue<T>,
  value: T
): Boolean = set(prefKeyValue.key, value)

suspend inline fun <T> DataStore<Preferences>.set(
  prefKeyValue: PrefKeyValue<Int, T>,
  value: T
): Boolean where T : Enum<T>, T : HasConstId = set(prefKeyValue.key, value.id)

operator fun <S : Any, A : Any> Preferences.get(pair: PrefKeyValue<S, A>): A =
  pair.storedToActual(get(pair.key))

/**
 * Make a flow of values for [keyValue].key defaulting to [keyValue].defaultValue if the
 * key is not present. Values will be [distinctUntilChanged]
 */
fun <S : Any, R : Any> DataStore<Preferences>.valueFlow(
  keyValue: PrefKeyValue<S, R>
): Flow<R> = data
  .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
  .map { preferences -> keyValue.storedToActual(preferences[keyValue.key]) }
  .distinctUntilChanged()
